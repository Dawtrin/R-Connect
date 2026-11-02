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

/** ShellView screen. Extracted from SocialWindow; behaviour is unchanged. */
final class ShellView extends ViewSupport {
    ShellView(SocialWindow app) { super(app); }

    // ================= MAIN CHROME =================
    void showMain() {
        app.root.removeAll();
        app.navButtons.clear();

        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setOpaque(true);
        sidebar.setBackground(Theme.NAV_BACKGROUND);
        sidebar.setPreferredSize(new Dimension(Theme.SIDEBAR_WIDTH, 600));
        sidebar.setMinimumSize(new Dimension(Theme.SIDEBAR_WIDTH, 400));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                new EmptyBorder(20, 12, 14, 12)));

        // Brand
        JLabel brand = Brand.lockup(22, 14f);
        brand.setBorder(new EmptyBorder(0, 6, 0, 0));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(22));

        // Navigation
        sidebar.add(navButton("feed", "Bảng tin", AppIcon.Type.FEED, this::showFeed));
        sidebar.add(Box.createVerticalStrut(2));
        sidebar.add(navButton("people", "Bạn bè & Tìm người", AppIcon.Type.PEOPLE, this::showPeople));
        sidebar.add(Box.createVerticalStrut(16));
        sidebar.add(navButton("conversations", "Tin nhắn & Nhóm", AppIcon.Type.CHAT, this::showConversations));
        sidebar.add(Box.createVerticalStrut(2));
        app.broadcastNav = navButton("broadcast", "Kênh chung", AppIcon.Type.BROADCAST, this::showBroadcast);
        sidebar.add(app.broadcastNav);
        sidebar.add(Box.createVerticalStrut(16));
        app.notificationNav = navButton("notifications", "Thông báo", AppIcon.Type.NOTIFICATION, this::showNotifications);
        sidebar.add(app.notificationNav);
        sidebar.add(Box.createVerticalStrut(2));
        sidebar.add(navButton("settings", "Hồ sơ & Cài đặt", AppIcon.Type.SETTINGS, this::showSettings));
        if ("ADMIN".equals(str(app.currentUser, "role"))) {
            sidebar.add(Box.createVerticalStrut(2));
            sidebar.add(navButton("admin", "Quản trị hệ thống", AppIcon.Type.SHIELD, this::showAdmin));
        }

        sidebar.add(Box.createVerticalGlue());

        JButton resume = ghost("Tiếp tục tải tệp", new AppIcon(AppIcon.Type.FOLDER, Theme.TEXT_SECONDARY, 16), () -> app.media.resumeUploads(app));
        resume.setHorizontalAlignment(SwingConstants.LEFT);
        resume.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        resume.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(resume);
        sidebar.add(Box.createVerticalStrut(10));

        // Account row: avatar + app.identity + sign-out
        JPanel account = new Theme.RoundedPanel(Theme.SURFACE, Theme.RADIUS_LG);
        ((Theme.RoundedPanel) account).setStroke(Theme.BORDER);
        account.setLayout(new BorderLayout(10, 0));
        account.setBorder(new EmptyBorder(8, 10, 8, 6));
        account.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        account.setAlignmentX(Component.LEFT_ALIGNMENT);

        app.ownAvatarHolder = (JPanel) avatar(app.currentUser, 36);
        JPanel avatarSlot = new JPanel(new GridBagLayout());
        avatarSlot.setOpaque(false);
        avatarSlot.add(app.ownAvatarHolder);
        account.add(avatarSlot, BorderLayout.WEST);

        JPanel identityBox = new JPanel();
        identityBox.setLayout(new BoxLayout(identityBox, BoxLayout.Y_AXIS));
        identityBox.setOpaque(false);
        app.identity.setText(name(app.currentUser));
        app.identity.setFont(Theme.font(Font.BOLD, 13));
        app.identity.setForeground(Theme.TEXT);
        app.identity.putClientProperty("html.disable", true);
        JLabel roleLabel = new JLabel("ADMIN".equals(str(app.currentUser, "role")) ? "Quản trị viên" : "Thành viên");
        roleLabel.setFont(Theme.font(Font.PLAIN, 11));
        roleLabel.setForeground(Theme.MUTED);
        identityBox.add(Box.createVerticalGlue());
        identityBox.add(app.identity);
        identityBox.add(roleLabel);
        identityBox.add(Box.createVerticalGlue());
        account.add(identityBox, BorderLayout.CENTER);

        JButton logoutBtn = Theme.ghostButton("");
        logoutBtn.setIcon(new AppIcon(AppIcon.Type.LOGOUT, Theme.TEXT_SECONDARY, 17));
        logoutBtn.setToolTipText("Đăng xuất");
        logoutBtn.getAccessibleContext().setAccessibleName("Đăng xuất");
        logoutBtn.setPreferredSize(new Dimension(34, 34));
        logoutBtn.putClientProperty("FlatLaf.style", "margin:4,4,4,4; hoverBackground:" + "#2A1517" + "; arc:" + Theme.RADIUS_MD);
        logoutBtn.addActionListener(e -> logout());
        JPanel logoutSlot = new JPanel(new GridBagLayout());
        logoutSlot.setOpaque(false);
        logoutSlot.add(logoutBtn);
        account.add(logoutSlot, BorderLayout.EAST);
        sidebar.add(account);

        // Status strip (real app.connection state; text updated by the networking callbacks)
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(Theme.NAV_BACKGROUND);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(3, 16, 3, 10)));

        app.connection.setForeground(Theme.TEXT_SECONDARY);
        app.connection.setFont(Theme.font(Font.PLAIN, 12));
        app.connection.setIcon(new AppIcon(AppIcon.Type.DOT, Theme.SUCCESS, 10));
        app.connection.setIconTextGap(8);
        app.connection.setText("Đã kết nối TCP · " + app.host + ":" + app.port);

        JButton sync = ghost("Đồng bộ", new AppIcon(AppIcon.Type.REFRESH, Theme.TEXT_SECONDARY, 13), () -> query("STATE_SYNC", of("limit", 20), r -> {
            app.refreshCurrent.run();
            refreshCount();
        }));
        JButton relogin = ghost("Đăng nhập lại", () -> {
            app.reconnectToken++;
            if (app.client != null) app.client.close();
            showAuth(false);
        });
        sync.setFont(Theme.font(Font.PLAIN, 12));
        relogin.setFont(Theme.font(Font.PLAIN, 12));
        sync.putClientProperty("FlatLaf.style", "margin:2,8,2,8;");
        relogin.putClientProperty("FlatLaf.style", "margin:2,8,2,8;");
        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        footerActions.setOpaque(false);
        footerActions.add(sync);
        footerActions.add(relogin);
        footer.add(app.connection, BorderLayout.CENTER);
        footer.add(footerActions, BorderLayout.EAST);

        app.contextHolder.setOpaque(true);
        app.contextHolder.setBackground(Theme.NAV_BACKGROUND);
        app.contextHolder.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER));
        app.contextHolder.setPreferredSize(new Dimension(SocialWindow.CONTEXT_WIDTH, 100));
        app.contextHolder.setVisible(false);
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(app.body, BorderLayout.CENTER);
        content.add(app.contextHolder, BorderLayout.EAST);
        content.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { applyContextVisibility(); }
        });
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.APP_BACKGROUND);
        main.add(content, BorderLayout.CENTER);
        main.add(footer, BorderLayout.SOUTH);

        app.root.add(sidebar, BorderLayout.WEST);
        app.root.add(main, BorderLayout.CENTER);
        app.root.revalidate();
        app.root.repaint();
        getRootPane().setDefaultButton(null);
        installShortcuts();

        app.countRefresh.start();
        refreshCount();
    }

    /** Ctrl+1…6 jump between the main sections; shown in each navigation item's tooltip. */
    void installShortcuts() {
        String[] keys = {"feed", "people", "conversations", "broadcast", "notifications", "settings"};
        for (int i = 0; i < keys.length; i++) {
            NavItem item = app.navButtons.get(keys[i]);
            if (item == null) continue;
            item.setToolTipText(item.label() + "  (Ctrl+" + (i + 1) + ")");
            String action = "nav-" + keys[i];
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK), action);
            getRootPane().getActionMap().put(action, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (app.authenticated) item.doClick(0);
                }
            });
        }
    }

    JLabel navSection(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 10).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.12f)));
        l.setForeground(Theme.MUTED);
        l.setBorder(new EmptyBorder(0, 14, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    NavItem navButton(String key, String text, AppIcon.Type iconType, Runnable action) {
        NavItem item = new NavItem(text, iconType);
        item.addActionListener(e -> runAction(item, action));
        app.navButtons.put(key, item);
        return item;
    }
}
