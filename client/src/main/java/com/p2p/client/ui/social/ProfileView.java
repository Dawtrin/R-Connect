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

/** ProfileView screen. Extracted from SocialWindow; behaviour is unchanged. */
final class ProfileView extends ViewSupport {
    ProfileView(SocialWindow app) { super(app); }

    // ================= PROFILE & SETTINGS =================
    void showProfile(long uid) {
        Page timeline = app.new Page("Bài viết của người dùng", "PROFILE_POSTS_GET", of("authorId", uid), this::postCard);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JPanel profile = vertical();
        profile.setBorder(new EmptyBorder(22, 24, 6, 24));
        profile.add(muted("Đang tải thông tin hồ sơ…"));
        panel.add(centered(profile, Theme.COLUMN_WIDTH + 48), BorderLayout.NORTH);
        panel.add(timeline.panel, BorderLayout.CENTER);
        setView("profile", panel, () -> showProfile(uid));

        long token = app.generation;
        query("PROFILE_GET", of("targetUserId", uid), r -> {
            if (token != app.generation) return;
            Map<String, Object> u = map(r.get("profile"));
            if (u.isEmpty()) u = r;
            profile.removeAll();
            JPanel header = card();
            header.setBorder(new EmptyBorder(20, 22, 18, 22));
            header.setLayout(new BorderLayout(18, 14));
            String avatarMedia = str(u, "avatarMediaId");
            JPanel avatarSlot = new JPanel(new BorderLayout());
            avatarSlot.setOpaque(false);
            JComponent bigAvatar = avatar(u, 84);
            avatarSlot.add(bigAvatar, BorderLayout.NORTH);
            header.add(avatarSlot, BorderLayout.WEST);
            JPanel identityBlock = new JPanel();
            identityBlock.setOpaque(false);
            identityBlock.setLayout(new BoxLayout(identityBlock, BoxLayout.Y_AXIS));
            JLabel pname = new JLabel(name(u));
            pname.setFont(Theme.font(Font.BOLD, 22));
            pname.setForeground(Theme.TEXT);
            pname.putClientProperty("html.disable", Boolean.TRUE);
            pname.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel phandle = muted("@" + str(u, "username"));
            phandle.setAlignmentX(Component.LEFT_ALIGNMENT);
            identityBlock.add(Box.createVerticalStrut(6));
            identityBlock.add(pname);
            identityBlock.add(phandle);
            if (!str(u, "bio").isBlank()) {
                JTextArea about = readable(str(u, "bio"));
                about.setForeground(Theme.TEXT_SECONDARY);
                about.setAlignmentX(Component.LEFT_ALIGNMENT);
                identityBlock.add(Box.createVerticalStrut(8));
                identityBlock.add(about);
            }
            header.add(identityBlock, BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            actions.setOpaque(false);
            if (!avatarMedia.isBlank()) actions.add(ghost("Xem ảnh đại diện", () -> app.media.preview(app, avatarMedia)));
            if (uid != id(app.currentUser, "user")) {
                actions.add(primary("Nhắn tin", () -> openDirect(uid)));
                actions.add(button("Kết bạn", () -> mutate("FRIEND_REQUEST", of("targetUserId", uid), null, x -> showPeople())));
                actions.add(ghost("Báo cáo", () -> report("USER", uid)));
                actions.add(danger("Chặn", () -> block(uid, true)));
            } else {
                actions.add(primary("Chỉnh sửa hồ sơ", this::showSettings));
            }
            header.add(actions, BorderLayout.SOUTH);
            profile.add(header);
            profile.revalidate();
            profile.repaint();
        });
        timeline.load(true);
    }

    void showSettings() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(centered(titled("Hồ sơ & Cài đặt"), Theme.COLUMN_WIDTH + 48), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        styleTabs(tabs);
        panel.add(tabs, BorderLayout.CENTER);

        JPanel settings = vertical();
        settings.setBorder(new EmptyBorder(20, 0, 28, 0));

        JTextField nameField = new JTextField(name(app.currentUser));
        JTextArea bio = text(str(app.currentUser, "bio"), 4);
        addField(settings, "Tên hiển thị", nameField);
        addField(settings, "Tiểu sử / Giới thiệu", framed(bio));
        JComboBox<String> language = new JComboBox<>(new String[]{"Tiếng Việt", "English"});
        language.setSelectedIndex("en".equals(app.displayLanguage) ? 1 : 0);
        addField(settings, "Ngôn ngữ hiển thị tin nhắn", language);
        settings.add(readable("Tin nhắn vẫn được lưu bằng ngôn ngữ gốc trên server. Bạn có thể bấm “Xem bản gốc” trong từng tin để đối chiếu."));
        language.addActionListener(e -> {
            String next = language.getSelectedIndex() == 1 ? "en" : "vi";
            if (!next.equals(app.displayLanguage)) {
                app.displayLanguage = next;
                app.userPreferences.put("displayLanguage", next);
                app.translations.clear();
                app.translationPending.clear();
                app.originalMessages.clear();
                if (app.activeConversation > 0) renderMessages(false);
            }
        });
        JLabel version = label("Đang tải hồ sơ…", false);
        settings.add(version);
        Map<String, Object> profile = new LinkedHashMap<>(app.currentUser);

        nameField.setEnabled(false);
        bio.setEnabled(false);

        JButton save = Theme.primaryButton("Lưu hồ sơ");
        save.addActionListener(e -> mutate("PROFILE_UPDATE", of("displayName", nameField.getText(), "bio", bio.getText(), "expectedVersion", num(profile, "version")), save, r -> {
            Map<String, Object> u = map(r.get("profile"));
            if (u.isEmpty()) u = r;
            updateSelf(u);
            showSettings();
        }));

        settings.add(horizontal(
                save,
                button("Đổi ảnh đại diện", () -> {
                    JFileChooser c = new JFileChooser();
                    if (c.showOpenDialog(app) == JFileChooser.APPROVE_OPTION) {
                        app.media.upload(app, c.getSelectedFile().toPath(), "AVATAR", mid -> mutate("AVATAR_SET", of("mediaId", mid, "expectedVersion", num(profile, "version")), null, r -> showSettings()));
                    }
                }),
                button("Xem hồ sơ công khai", () -> showProfile(id(app.currentUser, "user")))
        ));

        settings.add(Box.createVerticalStrut(24));
        JSeparator settingsRule = new JSeparator();
        settingsRule.setForeground(Theme.BORDER);
        settings.add(settingsRule);
        settings.add(Box.createVerticalStrut(20));
        settings.add(label("Đổi mật khẩu tài khoản", true));
        JPasswordField old = new JPasswordField(), next = new JPasswordField(), repeat = new JPasswordField();
        addField(settings, "Mật khẩu hiện tại", old);
        addField(settings, "Mật khẩu mới (10–128 ký tự)", next);
        addField(settings, "Xác nhận mật khẩu mới", repeat);

        JButton change = Theme.primaryButton("Cập nhật mật khẩu");
        change.addActionListener(e -> {
            char[] a = old.getPassword(), b = next.getPassword(), c = repeat.getPassword();
            if (b.length < 10 || b.length > 128 || !Arrays.equals(b, c)) {
                Arrays.fill(a, '\0');
                Arrays.fill(b, '\0');
                Arrays.fill(c, '\0');
                warn("Mật khẩu mới cần từ 10–128 ký tự và phải trùng khớp.");
                return;
            }
            Map<String, Object> payload = of("currentPassword", new String(a), "newPassword", new String(b));
            Arrays.fill(a, '\0');
            Arrays.fill(b, '\0');
            Arrays.fill(c, '\0');
            old.setText("");
            next.setText("");
            repeat.setText("");
            command("AUTH_PASSWORD_CHANGE", payload, null, change, r -> {
                clearSession();
                showAuth(false);
                info("Đã đổi mật khẩu thành công. Vui lòng đăng nhập lại.");
            }, false);
        });
        settings.add(horizontal(change));
        tabs.addTab("Hồ sơ & Bảo mật", columnScroll(settings, Theme.COLUMN_WIDTH));

        Page blocked = app.new Page("Danh sách người dùng đã chặn", "BLOCK_LIST", of(), u -> {
            JPanel card = card();
            card.add(label(name(u), false), BorderLayout.WEST);
            card.add(button("Bỏ chặn", () -> block(id(u, "user"), false)), BorderLayout.EAST);
            return card;
        });
        tabs.addTab("Đã chặn", blocked.panel);

        setView("settings", panel, this::showSettings);
        long token = app.generation;
        query("PROFILE_GET", of("targetUserId", id(app.currentUser, "user")), r -> {
            if (token != app.generation) return;
            Map<String, Object> u = map(r.get("profile"));
            if (u.isEmpty()) u = r;
            profile.clear();
            profile.putAll(u);
            updateSelf(u);
            nameField.setText(name(u));
            bio.setText(str(u, "bio"));
            version.setText("Phiên bản hồ sơ v" + num(u, "version"));
            nameField.setEnabled(true);
            bio.setEnabled(true);
        });
        blocked.load(true);
    }
}
