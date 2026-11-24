package com.p2p.client.ui.social;

import com.p2p.client.ui.ModernUI;
import com.p2p.client.ui.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import static com.p2p.client.ui.social.Values.*;
import com.p2p.client.ui.social.SocialWindow.Page;

/** ProfileView screen: User profile with accurate relationship state, Settings, Instant App Language Switcher, and Block list. */
final class ProfileView extends ViewSupport {
    ProfileView(SocialWindow app) { super(app); }

    void showProfile(long uid) {
        Page timeline = app.new Page(tr("Bài viết của người dùng", "User Posts"), "PROFILE_POSTS_GET", of("authorId", uid), this::postCard);
        timeline.emptyTitle = tr("Chưa có bài viết", "No posts yet");
        timeline.emptyHint = tr("Các bài viết công khai hoặc bạn bè được phép xem sẽ hiện ở đây.", "Visible posts from this user will appear here.");
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JPanel profile = vertical();
        profile.setBorder(new EmptyBorder(22, 24, 6, 24));
        profile.add(muted(tr("Đang tải thông tin hồ sơ…", "Loading profile…")));
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
            if (!avatarMedia.isBlank()) {
                actions.add(ghost(tr("Xem ảnh đại diện", "View Avatar"), () -> app.media.preview(app, avatarMedia)));
            }
            if (uid != id(app.currentUser, "user")) {
                actions.add(primary(tr("Nhắn tin", "Message"), () -> openDirect(uid)));
                String relStatus = str(u, "relationshipStatus");
                long relId = num(u, "relationId");
                long relVer = num(u, "relationVersion");
                switch (relStatus) {
                    case "ACCEPTED" -> {
                        actions.add(ModernUI.chip(tr("BẠN BÈ", "FRIENDS"), ModernUI.Tone.SUCCESS));
                        actions.add(button(tr("Hủy kết bạn", "Unfriend"), () -> {
                            if (confirm(tr("Hủy kết bạn với người này?", "Unfriend this user?"))) {
                                mutate("FRIEND_REMOVE", of("relationId", relId), null, x -> showProfile(uid));
                            }
                        }));
                    }
                    case "PENDING_INCOMING" -> {
                        actions.add(primary(tr("Chấp nhận lời mời", "Accept Request"), () ->
                                mutate("FRIEND_ACCEPT", of("relationId", relId, "expectedVersion", relVer), null, x -> showProfile(uid))));
                        actions.add(button(tr("Từ chối", "Decline"), () ->
                                mutate("FRIEND_REJECT", of("relationId", relId, "expectedVersion", relVer), null, x -> showProfile(uid))));
                    }
                    case "PENDING_OUTGOING" -> {
                        actions.add(button(tr("Hủy lời mời đã gửi", "Cancel Request"), () ->
                                mutate("FRIEND_CANCEL", of("relationId", relId, "expectedVersion", relVer), null, x -> showProfile(uid))));
                    }
                    default -> {
                        actions.add(button(tr("Kết bạn", "Add Friend"), () ->
                                mutate("FRIEND_REQUEST", of("targetUserId", uid), null, x -> {
                                    info(tr("Đã gửi lời mời kết bạn!", "Friend request sent!"));
                                    showProfile(uid);
                                })));
                    }
                }
                actions.add(ghost(tr("Báo cáo", "Report"), () -> report("USER", uid)));
                actions.add(danger(tr("Chặn", "Block"), () -> block(uid, true)));
            } else {
                actions.add(primary(tr("Chỉnh sửa hồ sơ", "Edit Profile"), this::showSettings));
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
        panel.add(centered(titled(tr("Hồ sơ & Cài đặt", "Profile & Settings")), Theme.COLUMN_WIDTH + 48), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        styleTabs(tabs);
        panel.add(tabs, BorderLayout.CENTER);

        JPanel settings = vertical();
        settings.setBorder(new EmptyBorder(20, 0, 28, 0));

        JTextField nameField = new JTextField(name(app.currentUser));
        JTextArea bio = text(str(app.currentUser, "bio"), 4);
        addField(settings, tr("Tên hiển thị", "Display Name"), nameField);
        addField(settings, tr("Tiểu sử / Giới thiệu", "Bio / About"), framed(bio));

        JComboBox<String> language = new JComboBox<>(new String[]{"Tiếng Việt (Vietnamese)", "English"});
        language.setSelectedIndex(isEn() ? 1 : 0);
        addField(settings, tr("Ngôn ngữ giao diện & Dịch tin nhắn", "App Language & Message Translation"), language);
        settings.add(readable(tr(
                "Đổi ngôn ngữ sẽ cập nhật ngay toàn bộ giao diện ứng dụng và tự động dịch tin nhắn sang ngôn ngữ đã chọn (tin gốc trên máy chủ vẫn giữ nguyên).",
                "Switching language immediately updates the entire application UI and translates chat messages into your chosen language (original messages remain untouched on the server).")));
        language.addActionListener(e -> {
            String next = language.getSelectedIndex() == 1 ? "en" : "vi";
            if (!next.equals(app.displayLanguage)) {
                app.applyLanguage(next);
            }
        });

        JLabel version = label(tr("Đang tải hồ sơ…", "Loading profile…"), false);
        settings.add(version);
        Map<String, Object> profile = new LinkedHashMap<>(app.currentUser);

        nameField.setEnabled(false);
        bio.setEnabled(false);

        JButton save = Theme.primaryButton(tr("Lưu hồ sơ", "Save Profile"));
        save.addActionListener(e -> mutate("PROFILE_UPDATE", of("displayName", nameField.getText(), "bio", bio.getText(), "expectedVersion", num(profile, "version")), save, r -> {
            Map<String, Object> u = map(r.get("profile"));
            if (u.isEmpty()) u = r;
            updateSelf(u);
            info(tr("Đã lưu thông tin hồ sơ.", "Profile updated."));
            showSettings();
        }));

        settings.add(horizontal(
                save,
                button(tr("Đổi ảnh đại diện", "Change Avatar"), () -> {
                    JFileChooser c = new JFileChooser();
                    if (c.showOpenDialog(app) == JFileChooser.APPROVE_OPTION) {
                        app.media.upload(app, c.getSelectedFile().toPath(), "AVATAR", mid ->
                                mutate("AVATAR_SET", of("mediaId", mid, "expectedVersion", num(profile, "version")), null, r -> {
                                    info(tr("Đã cập nhật ảnh đại diện!", "Avatar updated!"));
                                    showSettings();
                                }));
                    }
                }),
                button(tr("Xem hồ sơ công khai", "View Public Profile"), () -> showProfile(id(app.currentUser, "user")))
        ));

        settings.add(Box.createVerticalStrut(24));
        JSeparator settingsRule = new JSeparator();
        settingsRule.setForeground(Theme.BORDER);
        settings.add(settingsRule);
        settings.add(Box.createVerticalStrut(20));
        settings.add(label(tr("Đổi mật khẩu tài khoản", "Change Account Password"), true));
        JPasswordField old = new JPasswordField(), next = new JPasswordField(), repeat = new JPasswordField();
        addField(settings, tr("Mật khẩu hiện tại", "Current Password"), old);
        addField(settings, tr("Mật khẩu mới (10–128 ký tự)", "New Password (10–128 chars)"), next);
        addField(settings, tr("Xác nhận mật khẩu mới", "Confirm New Password"), repeat);

        JButton change = Theme.primaryButton(tr("Cập nhật mật khẩu", "Update Password"));
        change.addActionListener(e -> {
            char[] a = old.getPassword(), b = next.getPassword(), c = repeat.getPassword();
            if (b.length < 10 || b.length > 128 || !Arrays.equals(b, c)) {
                Arrays.fill(a, '\0');
                Arrays.fill(b, '\0');
                Arrays.fill(c, '\0');
                warn(tr("Mật khẩu mới cần từ 10–128 ký tự và phải trùng khớp.", "New password must be 10–128 characters and match confirmation."));
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
                info(tr("Đã đổi mật khẩu thành công. Vui lòng đăng nhập lại.", "Password changed successfully. Please sign in again."));
            }, false);
        });
        settings.add(horizontal(change));
        tabs.addTab(tr("Hồ sơ & Bảo mật", "Profile & Security"), columnScroll(settings, Theme.COLUMN_WIDTH));

        Page[] blockedRef = new Page[1];
        Page blocked = app.new Page(tr("Danh sách người dùng đã chặn", "Blocked Users"), "BLOCK_LIST", of(), u -> {
            JPanel card = card();
            String display = name(u) + (str(u, "username").isBlank() ? "" : " (@" + str(u, "username") + ")");
            card.add(label(display, false), BorderLayout.WEST);
            card.add(button(tr("Bỏ chặn", "Unblock"), () ->
                    mutate("BLOCK_SET", of("targetUserId", id(u, "user"), "blocked", false), null, r -> {
                        info(tr("Đã bỏ chặn người dùng.", "User unblocked."));
                        if (blockedRef[0] != null) blockedRef[0].load(true);
                    })), BorderLayout.EAST);
            return card;
        });
        blockedRef[0] = blocked;
        blocked.emptyTitle = tr("Chưa chặn ai", "No blocked users");
        blocked.emptyHint = tr("Những người dùng bị bạn chặn sẽ xuất hiện tại đây.", "Users you block will be listed here.");
        tabs.addTab(tr("Đã chặn", "Blocked"), blocked.panel);

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
            version.setText(tr("Phiên bản hồ sơ v", "Profile version v") + num(u, "version"));
            nameField.setEnabled(true);
            bio.setEnabled(true);
        });
        blocked.load(true);
    }
}
