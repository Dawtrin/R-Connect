package com.p2p.client.ui.social;

import com.p2p.client.network.social.*;
import com.p2p.client.ui.AppIcon;
import com.p2p.client.ui.Brand;
import com.p2p.client.ui.Dialogs;
import com.p2p.client.ui.ModernUI;
import com.p2p.client.ui.NavItem;
import com.p2p.client.ui.Theme;
import com.p2p.common.social.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.prefs.Preferences;
import static com.p2p.client.ui.social.Values.*;
import com.p2p.client.ui.social.SocialWindow.Page;

/** BroadcastView screen. Extracted from SocialWindow; behaviour is unchanged. */
final class BroadcastView extends ViewSupport {
    BroadcastView(SocialWindow app) { super(app); }

    JPanel broadcastMessages;
    JTextArea broadcastInput;
    JScrollPane broadcastScroll;
    String broadcastPendingKey, broadcastPendingContent;
    boolean broadcastSending;
    final LinkedHashMap<String, Map<String, Object>> broadcasts = new LinkedHashMap<>();
    int broadcastUnread;

    // ================= BROADCAST =================
    void receiveBroadcast(Map<String, Object> p) {
        String key = str(p, "broadcastId");
        if (key.isBlank() || broadcasts.containsKey(key)) return;
        broadcasts.put(key, new LinkedHashMap<>(p));
        while (broadcasts.size() > 100) broadcasts.remove(broadcasts.keySet().iterator().next());
        if (!"broadcast".equals(app.view)) broadcastUnread = Math.min(100, broadcastUnread + 1);
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

        JPanel heading = titled("Kênh chung");
        JLabel badgeBroadcast = ModernUI.chip("Kênh chung", ModernUI.Tone.WARNING);
        JPanel headerTop = horizontal(badgeBroadcast);
        headerTop.setBorder(new EmptyBorder(0, 0, 4, 0));
        heading.add(headerTop, BorderLayout.CENTER);
        JTextArea broadcastNote = readable("Kênh công khai cho mọi tài khoản đang online, kể cả người đã chặn. Giữ tối đa 100 tin gần nhất trong phiên hiện tại; không lưu vĩnh viễn khi offline.");
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

        JTextArea input = text(Objects.toString(broadcastPendingContent, ""), 3);
        input.putClientProperty("JTextField.placeholderText", "Nhập thông báo gửi tới mọi tài khoản đang online…");
        broadcastInput = input;
        JButton send = Theme.primaryButton("Gửi tới kênh chung");
        send.setIcon(new AppIcon(AppIcon.Type.BROADCAST, Theme.ON_PRIMARY, 16));
        send.setIconTextGap(8);
        send.setEnabled(app.authenticated && !broadcastSending);
        input.setEnabled(app.authenticated && !broadcastSending);

        JLabel status = label(broadcastPendingKey == null ? "Tối đa 4.000 ký tự" : "Chưa xác định kết quả · gửi lại cùng nội dung để dùng lại mã", false);
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
        JPanel composer = new JPanel(new BorderLayout());
        composer.setBackground(Theme.APP_BACKGROUND);
        composer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        composer.add(centered(composerInner, Theme.COLUMN_WIDTH + 48));
        panel.add(composer, BorderLayout.SOUTH);

        setView("broadcast", panel, this::renderBroadcasts);
        renderBroadcasts();
    }

    void sendBroadcast(JTextArea input, JButton send, JLabel status) {
        if (!app.authenticated || broadcastSending) return;
        String content = input.getText().trim();
        if (content.isBlank() || content.length() > 4000) {
            status.setText("Nhập từ 1 đến 4.000 ký tự.");
            return;
        }
        if (!content.equals(broadcastPendingContent)) {
            broadcastPendingKey = UUID.randomUUID().toString();
            broadcastPendingContent = content;
        }
        broadcastSending = true;
        send.setEnabled(false);
        input.setEnabled(false);
        status.setText("Đang gửi phát sóng…");
        SocialClient expected = app.client;
        String key = broadcastPendingKey;
        CompletableFuture.supplyAsync(() -> blocking(expected, "BROADCAST_SEND", of("content", content), key), app.workers).whenComplete((ack, error) -> invoke(() -> {
            if (app.client != expected || !app.authenticated) return;
            broadcastSending = false;
            send.setEnabled(true);
            input.setEnabled(true);
            if (error != null) {
                status.setText(unwrap(error) instanceof RemoteException ? friendly(error) : "Chưa xác định kết quả · gửi lại cùng nội dung để dùng lại mã");
            } else {
                receiveBroadcast(ack);
                broadcastPendingKey = null;
                broadcastPendingContent = null;
                input.setText("");
                status.setText("Máy chủ đã xếp hàng gửi tới " + num(ack, "recipientCount") + " phiên online.");
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
            broadcastMessages.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.BROADCAST, Theme.MUTED, 36), "Kênh chung đang yên tĩnh", "Tin gửi tới mọi tài khoản đang online sẽ xuất hiện tại đây."));
        }
        for (Map<String, Object> b : broadcasts.values()) {
            JPanel c = card();
            c.add(label(str(b, "senderName") + " · " + time(b), false), BorderLayout.NORTH);
            c.add(readable(str(b, "content")), BorderLayout.CENTER);
            broadcastMessages.add(c);
            broadcastMessages.add(Box.createVerticalStrut(10));
        }
        broadcastMessages.revalidate();
        broadcastMessages.repaint();
        if (bottom) invoke(() -> bar.setValue(bar.getMaximum()));
    }
}
