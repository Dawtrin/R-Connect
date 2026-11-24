package com.p2p.client.ui.social;

import com.p2p.client.network.social.*;
import com.p2p.client.ui.AppIcon;
import com.p2p.client.ui.ModernUI;
import com.p2p.client.ui.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.*;
import static com.p2p.client.ui.social.Values.*;

/** BroadcastView screen: Admin-only system announcement broadcast channel over TCP. */
final class BroadcastView extends ViewSupport {
    BroadcastView(SocialWindow app) { super(app); }

    JPanel broadcastMessages;
    JTextArea broadcastInput;
    JScrollPane broadcastScroll;
    String broadcastPendingKey, broadcastPendingContent;
    boolean broadcastSending;
    final LinkedHashMap<String, Map<String, Object>> broadcasts = new LinkedHashMap<>();
    int broadcastUnread;

    boolean isAdminUser() {
        return "ADMIN".equals(str(app.currentUser, "role"));
    }

    void receiveBroadcast(Map<String, Object> p) {
        String key = str(p, "broadcastId");
        if (key.isBlank() || broadcasts.containsKey(key)) return;
        broadcasts.put(key, new LinkedHashMap<>(p));
        while (broadcasts.size() > 100) broadcasts.remove(broadcasts.keySet().iterator().next());
        boolean ownMessage = num(p, "senderId") > 0 && num(p, "senderId") == id(app.currentUser, "user");
        if (!"broadcast".equals(app.view) && !ownMessage) {
            broadcastUnread = Math.min(100, broadcastUnread + 1);
        }
        if (app.broadcastNav != null) {
            app.broadcastNav.setBadge(broadcastUnread);
        }
        if ("broadcast".equals(app.view)) renderBroadcasts();
    }

    void resetBroadcast() {
        broadcasts.clear();
        broadcastUnread = 0;
        broadcastPendingKey = null;
        broadcastPendingContent = null;
        broadcastSending = false;
        broadcastInput = null;
        broadcastMessages = null;
        broadcastScroll = null;
    }

    void showBroadcast() {
        broadcastUnread = 0;
        if (app.broadcastNav != null) app.broadcastNav.setBadge(0);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);

        JPanel heading = titled(tr("Kênh thông báo hệ thống (Public Channel)", "Public Channel (System Broadcast)"));
        JLabel badgeRole = ModernUI.chip(
                tr("CHỈ QUẢN TRỊ VIÊN ĐƯỢC ĐĂNG", "ADMIN ANNOUNCEMENTS ONLY"),
                ModernUI.Tone.ACCENT);
        JLabel badgeBroadcast = ModernUI.chip(
                tr("PHÁT TRỰC TIẾP QUA TCP · BỘ ĐỆM PHIÊN HIỆN TẠI", "LIVE TCP FAN-OUT · SESSION EPHEMERAL"),
                ModernUI.Tone.WARNING);
        JPanel headerTop = horizontal(badgeRole, badgeBroadcast);
        headerTop.setBorder(new EmptyBorder(0, 0, 4, 0));
        heading.add(headerTop, BorderLayout.CENTER);
        JTextArea broadcastNote = readable(tr(
                "Kênh thông báo một chiều từ Quản trị viên (Admin) tới mọi tài khoản đang kết nối. Giữ tối đa 20 thông báo gần nhất trong phiên chạy máy chủ hiện tại.",
                "One-way announcement channel from Administrators to all connected accounts. Retains up to 20 recent broadcasts during the active server session."));
        broadcastNote.setForeground(Theme.TEXT_SECONDARY);
        heading.add(broadcastNote, BorderLayout.SOUTH);
        JPanel broadcastHeaderBar = new JPanel(new BorderLayout());
        broadcastHeaderBar.setBackground(Theme.APP_BACKGROUND);
        broadcastHeaderBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        broadcastHeaderBar.add(centered(heading, Theme.COLUMN_WIDTH + 48));
        panel.add(broadcastHeaderBar, BorderLayout.NORTH);

        broadcastMessages = new StackPanel(true);
        broadcastMessages.setBorder(new EmptyBorder(15, 24, 15, 24));
        broadcastScroll = scroll(broadcastMessages);
        panel.add(broadcastScroll, BorderLayout.CENTER);

        JPanel composer = new JPanel(new BorderLayout());
        composer.setBackground(Theme.APP_BACKGROUND);
        composer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));

        if (isAdminUser()) {
            JTextArea input = text(Objects.toString(broadcastPendingContent, ""), 3);
            input.putClientProperty("JTextField.placeholderText",
                    tr("Nhập thông báo hệ thống gửi tới toàn bộ người dùng đang online…", "Type an official system announcement to broadcast to all online users…"));
            broadcastInput = input;
            JButton send = Theme.primaryButton(tr("Phát thông báo hệ thống", "Send System Broadcast"));
            send.setIcon(new AppIcon(AppIcon.Type.BROADCAST, Theme.ON_PRIMARY, 16));
            send.setIconTextGap(8);
            send.setEnabled(app.authenticated && !broadcastSending);
            input.setEnabled(app.authenticated && !broadcastSending);

            JLabel status = label(broadcastPendingKey == null
                    ? tr("Quyền Quản trị viên · Tối đa 4.000 ký tự", "Administrator privilege · Up to 4,000 characters")
                    : tr("Chưa xác định kết quả · gửi lại cùng nội dung để dùng lại mã", "Outcome uncertain · resend same text to reuse idempotency key"), false);
            send.addActionListener(e -> sendBroadcast(input, send, status));

            input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "broadcast-send");
            input.getActionMap().put("broadcast-send", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { send.doClick(); }
            });
            input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break");

            JPanel composerInner = new JPanel(new BorderLayout(8, 8));
            composerInner.setOpaque(false);
            composerInner.setBorder(new EmptyBorder(12, 24, 14, 24));
            JScrollPane broadcastFrame = framed(input);
            broadcastFrame.setPreferredSize(new Dimension(100, 84));
            composerInner.add(broadcastFrame, BorderLayout.CENTER);
            JPanel broadcastActions = new JPanel(new BorderLayout(12, 0));
            broadcastActions.setOpaque(false);
            status.setForeground(Theme.MUTED);
            broadcastActions.add(status, BorderLayout.CENTER);
            broadcastActions.add(send, BorderLayout.EAST);
            composerInner.add(broadcastActions, BorderLayout.SOUTH);
            composer.add(centered(composerInner, Theme.COLUMN_WIDTH + 48));
        } else {
            broadcastInput = null;
            JPanel readOnlyNotice = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            readOnlyNotice.setOpaque(false);
            readOnlyNotice.setBorder(new EmptyBorder(14, 24, 14, 24));
            JLabel lockLabel = new JLabel(
                    tr("Kênh chỉ đọc — Chỉ Quản trị viên (Admin) mới có quyền đăng thông báo hệ thống tại đây.",
                       "Read-only channel — Only Administrators can post official system announcements here."),
                    new AppIcon(AppIcon.Type.SHIELD, Theme.MUTED, 16),
                    SwingConstants.LEFT);
            lockLabel.setIconTextGap(8);
            lockLabel.setFont(Theme.font(Font.PLAIN, 13));
            lockLabel.setForeground(Theme.MUTED);
            readOnlyNotice.add(lockLabel);
            composer.add(centered(readOnlyNotice, Theme.COLUMN_WIDTH + 48));
        }
        panel.add(composer, BorderLayout.SOUTH);

        setView("broadcast", panel, this::renderBroadcasts);
        renderBroadcasts();
    }

    void sendBroadcast(JTextArea input, JButton send, JLabel status) {
        if (!app.authenticated || broadcastSending || !isAdminUser()) return;
        String content = input.getText().trim();
        if (content.isBlank() || content.length() > 4000) {
            status.setText(tr("Nhập từ 1 đến 4.000 ký tự.", "Enter between 1 and 4,000 characters."));
            return;
        }
        if (!content.equals(broadcastPendingContent)) {
            broadcastPendingKey = UUID.randomUUID().toString();
            broadcastPendingContent = content;
        }
        broadcastSending = true;
        send.setEnabled(false);
        input.setEnabled(false);
        status.setText(tr("Đang phát thông báo…", "Broadcasting announcement…"));
        SocialClient expected = app.client;
        String key = broadcastPendingKey;
        CompletableFuture.supplyAsync(() -> blocking(expected, "BROADCAST_SEND", of("content", content), key), app.workers).whenComplete((ack, error) -> invoke(() -> {
            if (app.client != expected || !app.authenticated) return;
            broadcastSending = false;
            send.setEnabled(true);
            input.setEnabled(true);
            if (error != null) {
                status.setText(unwrap(error) instanceof RemoteException
                        ? friendly(error)
                        : tr("Chưa xác định kết quả · gửi lại cùng nội dung để dùng lại mã", "Outcome uncertain · resend same content to retry safely"));
            } else {
                receiveBroadcast(ack);
                broadcastPendingKey = null;
                broadcastPendingContent = null;
                input.setText("");
                status.setText(tr("Đã phát thông báo tới " + num(ack, "recipientCount") + " phiên đang online.",
                        "Broadcast delivered to " + num(ack, "recipientCount") + " online sessions."));
            }
            if ("broadcast".equals(app.view) && broadcastInput != input) showBroadcast();
        }));
    }

    void renderBroadcasts() {
        if (broadcastMessages == null) return;
        JScrollBar bar = broadcastScroll.getVerticalScrollBar();
        boolean bottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 40;
        broadcastMessages.removeAll();
        if (broadcasts.isEmpty()) {
            broadcastMessages.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.BROADCAST, Theme.MUTED, 36),
                    tr("Chưa có thông báo hệ thống", "No system announcements yet"),
                    tr("Thông báo chính thức từ Quản trị viên (Admin) sẽ xuất hiện trực tiếp tại đây.",
                       "Official live announcements from Administrators will appear here.")));
        }
        for (Map<String, Object> b : broadcasts.values()) {
            JPanel c = card();
            JPanel header = horizontal(
                    ModernUI.chip(tr("ADMIN", "ADMIN"), ModernUI.Tone.ACCENT),
                    label(str(b, "senderName") + " · " + time(b), false));
            c.add(header, BorderLayout.NORTH);
            c.add(readable(str(b, "content")), BorderLayout.CENTER);
            broadcastMessages.add(c);
            broadcastMessages.add(Box.createVerticalStrut(10));
        }
        broadcastMessages.revalidate();
        broadcastMessages.repaint();
        if (bottom) invoke(() -> bar.setValue(bar.getMaximum()));
    }
}
