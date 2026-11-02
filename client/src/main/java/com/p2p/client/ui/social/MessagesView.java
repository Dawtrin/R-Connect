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

/** MessagesView screen. Extracted from SocialWindow; behaviour is unchanged. */
final class MessagesView extends ViewSupport {
    MessagesView(SocialWindow app) { super(app); }

    // ================= CONVERSATIONS & CHAT =================
    void showConversations() {
        app.convPage = null;
        app.convPanelCache = null;
        app.selectedConvId = 0;
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        empty.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.CHAT, Theme.MUTED, 30),
                "Chọn một cuộc trò chuyện",
                "Chọn từ danh sách bên trái hoặc tạo nhóm mới để bắt đầu.",
                primary("Tạo nhóm mới", new AppIcon(AppIcon.Type.PEOPLE, Theme.ON_PRIMARY, 15), this::createGroup)));
        JPanel shell = messagesShell(empty);
        setView("conversations", shell, this::showConversations);
        app.convPage.load(true);
    }

    /** Two-pane messaging layout: persistent conversation list on the left, active content on the right. */
    JPanel messagesShell(JComponent center) {
        if (app.convPanelCache == null) buildConversationList();
        JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(false);
        shell.add(app.convPanelCache, BorderLayout.WEST);
        shell.add(center, BorderLayout.CENTER);
        return shell;
    }

    void buildConversationList() {
        app.compactNext = true;
        app.convPage = app.new Page("", "CONVERSATION_LIST", of(), this::conversationRow);
        app.convPage.emptyIcon = AppIcon.Type.CHAT;
        app.convPage.emptyTitle = "Chưa có cuộc trò chuyện";
        app.convPage.emptyHint = "Bấm dấu + để tạo nhóm, hoặc nhắn tin từ danh sách bạn bè.";
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(true);
        p.setBackground(Theme.SURFACE);
        int listWidth = getWidth() > 0 && getWidth() < 1150 ? 264 : SocialWindow.LIST_WIDTH;
        p.setPreferredSize(new Dimension(listWidth, 100));
        p.setMinimumSize(new Dimension(listWidth, 100));
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(18, 16, 10, 12));
        JLabel heading = new JLabel("Tin nhắn");
        heading.setFont(Theme.font(Font.BOLD, 18));
        heading.setForeground(Theme.TEXT);
        JButton add = Theme.ghostButton("");
        add.setIcon(new AppIcon(AppIcon.Type.PLUS, Theme.TEXT_SECONDARY, 16));
        add.setToolTipText("Tạo nhóm mới");
        add.getAccessibleContext().setAccessibleName("Tạo nhóm mới");
        add.putClientProperty("FlatLaf.style", "margin:6,6,6,6");
        add.addActionListener(e -> createGroup());
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(heading, BorderLayout.CENTER);
        titleRow.add(add, BorderLayout.EAST);
        JTextField search = new JTextField();
        search.setName("conversationSearch");
        Theme.styleInput(search);
        search.putClientProperty("JTextField.placeholderText", "Tìm cuộc trò chuyện");
        search.getAccessibleContext().setAccessibleName("Tìm cuộc trò chuyện");
        search.setPreferredSize(new Dimension(100, 36));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void apply() {
                String q = search.getText().trim().toLowerCase();
                for (Component c : app.convPage.list.getComponents()) {
                    if (c instanceof JComponent jc) {
                        Object hay = jc.getClientProperty("search");
                        if (hay != null) jc.setVisible(q.isEmpty() || hay.toString().contains(q));
                    }
                }
                app.convPage.list.revalidate();
                app.convPage.list.repaint();
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { apply(); }
        });
        top.add(titleRow, BorderLayout.NORTH);
        top.add(search, BorderLayout.CENTER);
        p.add(top, BorderLayout.NORTH);
        p.add(app.convPage.panel, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), new EmptyBorder(6, 8, 6, 8)));
        JButton invites = ghost("Lời mời vào nhóm", new AppIcon(AppIcon.Type.PEOPLE, Theme.TEXT_SECONDARY, 15), this::showGroupInvites);
        invites.setHorizontalAlignment(SwingConstants.LEFT);
        bottom.add(invites, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        app.convPanelCache = p;
    }

    JComponent conversationRow(Map<String, Object> conv) {
        long cid = id(conv, "conversation");
        boolean group = "GROUP".equals(str(conv, "type", "kind"));
        boolean selected = cid == app.selectedConvId;
        long unread = num(conv, "unreadCount", "unread");

        ListRow row = new ListRow(Theme.SURFACE, selected, () -> openChat(conv));

        Theme.Avatar face = new Theme.Avatar(name(conv), 38, !group && bool(conv, "online"));
        row.ring(face);
        JPanel faceSlot = new JPanel(new GridBagLayout());
        faceSlot.setOpaque(false);
        faceSlot.add(face);
        row.add(faceSlot, BorderLayout.WEST);

        String preview = unread > 0 ? unread + " tin nhắn chưa đọc" : group ? "Nhóm trò chuyện" : "Tin nhắn riêng";
        long activity = num(conv, "lastActivityAt");
        JLabel title = new JLabel(name(conv));
        title.setFont(Theme.font(unread > 0 ? Font.BOLD : Font.PLAIN, 13));
        title.setForeground(Theme.TEXT);
        title.putClientProperty("html.disable", Boolean.TRUE);
        JLabel sub = new JLabel(preview);
        sub.setFont(Theme.font(Font.PLAIN, 12));
        sub.setForeground(unread > 0 ? Theme.PRIMARY_LIGHT : Theme.MUTED);
        sub.putClientProperty("html.disable", Boolean.TRUE);
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        texts.add(title);
        texts.add(Box.createVerticalStrut(2));
        texts.add(sub);
        JPanel textSlot = new JPanel(new GridBagLayout());
        textSlot.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        textSlot.add(texts, gc);
        textSlot.setMinimumSize(new Dimension(10, 10));
        row.add(textSlot, BorderLayout.CENTER);

        JPanel meta = new JPanel();
        meta.setOpaque(false);
        meta.setLayout(new BoxLayout(meta, BoxLayout.Y_AXIS));
        if (activity > 0) {
            JLabel when = muted(shortStamp(activity));
            when.setFont(Theme.font(Font.PLAIN, 11));
            when.setAlignmentX(Component.RIGHT_ALIGNMENT);
            meta.add(when);
        }
        if (unread > 0) {
            JLabel chip = ModernUI.chip(unread > 99 ? "99+" : String.valueOf(unread), ModernUI.Tone.ACCENT);
            chip.setAlignmentX(Component.RIGHT_ALIGNMENT);
            meta.add(Box.createVerticalStrut(3));
            meta.add(chip);
        }
        row.add(meta, BorderLayout.EAST);

        row.putClientProperty("search", (name(conv) + " " + preview).toLowerCase());
        row.getAccessibleContext().setAccessibleName(name(conv) + (unread > 0 ? ", " + unread + " chưa đọc" : ""));
        return row;
    }

    /** Today: HH:mm. This year: dd/MM. Otherwise dd/MM/yy. */
    String shortStamp(long epochMillis) {
        java.time.ZonedDateTime t = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault());
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        if (t.toLocalDate().equals(now.toLocalDate())) return String.format("%02d:%02d", t.getHour(), t.getMinute());
        if (t.getYear() == now.getYear()) return String.format("%02d/%02d", t.getDayOfMonth(), t.getMonthValue());
        return String.format("%02d/%02d/%02d", t.getDayOfMonth(), t.getMonthValue(), t.getYear() % 100);
    }

    void showGroupInvites() {
        Page invites = app.new Page("", "GROUP_INVITE_LIST", of(), invite -> {
            JPanel c = card();
            c.add(label(str(invite, "title", "groupTitle") + " · #" + num(invite, "conversationId") + " · " + statusText(str(invite, "status")), false), BorderLayout.NORTH);
            JPanel actions = horizontal();
            long inviteId = id(invite, "invite");
            Runnable done = () -> {
                if (app.invitesDialog != null) app.invitesDialog.dispose();
                showConversations();
            };
            if (num(invite, "inviteeId") != id(app.currentUser, "user")) {
                actions.add(button("Hủy lời mời", () -> mutate("GROUP_INVITE_CANCEL", of("inviteId", inviteId), null, r -> done.run())));
            } else {
                actions.add(primary("Tham gia nhóm", () -> mutate("GROUP_INVITE_ACCEPT", of("inviteId", inviteId), null, r -> {
                    info("Bạn có thể xem lịch sử kể từ thời điểm tham gia nhóm.");
                    done.run();
                })));
                actions.add(button("Từ chối", () -> mutate("GROUP_INVITE_REJECT", of("inviteId", inviteId), null, r -> done.run())));
            }
            if (!"PENDING".equals(str(invite, "status"))) disableButtons(actions);
            c.add(actions, BorderLayout.SOUTH);
            return c;
        });
        app.invitesDialog = dialog("Lời mời tham gia nhóm", 560, 520);
        app.invitesDialog.setLayout(new BorderLayout());
        app.invitesDialog.add(invites.panel, BorderLayout.CENTER);
        app.invitesDialog.setVisible(true);
        invites.load(true);
    }

    /** Right-hand panel for the open conversation: who you are talking to, or group details. Real data only. */
    JComponent chatContext(Map<String, Object> conv) {
        boolean group = "GROUP".equals(str(conv, "type", "kind"));
        long cid = id(conv, "conversation");
        long other = num(conv, "otherUserId");
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(32, 22, 20, 22));

        Theme.Avatar face = new Theme.Avatar(name(conv), 84, !group && bool(conv, "online"));
        face.setRing(Theme.NAV_BACKGROUND);
        face.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel("<html><div style='text-align:center;width:190px'>"
                + name(conv).replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
        title.setFont(Theme.font(Font.BOLD, 17));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel handle = muted(group ? "Nhóm trò chuyện · #" + cid : " ");
        handle.setFont(Theme.font(Font.PLAIN, 12));
        handle.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel status = group ? ModernUI.chip("NHÓM", ModernUI.Tone.SUCCESS)
                : bool(conv, "online") ? ModernUI.chip("ĐANG ONLINE", ModernUI.Tone.SUCCESS) : ModernUI.chip("OFFLINE", ModernUI.Tone.NEUTRAL);
        status.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel head = new JPanel(new GridBagLayout());
        head.setOpaque(false);
        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.add(face);
        inner.add(Box.createVerticalStrut(14));
        inner.add(title);
        inner.add(Box.createVerticalStrut(2));
        inner.add(handle);
        inner.add(Box.createVerticalStrut(10));
        inner.add(status);
        head.add(inner);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        p.add(head);

        JTextArea bio = readable("");
        bio.setAlignmentX(Component.LEFT_ALIGNMENT);
        bio.setVisible(false);
        JLabel bioTitle = contextHeading("GIỚI THIỆU");
        bioTitle.setVisible(false);
        p.add(Box.createVerticalStrut(22));
        p.add(bioTitle);
        p.add(bio);

        p.add(Box.createVerticalStrut(18));
        if (group) {
            boolean owner = num(conv, "ownerId") == id(app.currentUser, "user") || "OWNER".equals(str(conv, "role"));
            p.add(fullWidth(button("Quản lý thành viên", new AppIcon(AppIcon.Type.PEOPLE, Theme.TEXT, 15), () -> groupInfo(conv))));
            if (owner) {
                p.add(Box.createVerticalStrut(8));
                p.add(fullWidth(button("Mời bạn bè", new AppIcon(AppIcon.Type.PLUS, Theme.TEXT, 15), () -> selectFriend(uid ->
                        mutate("GROUP_INVITE", of("conversationId", cid, "targetUserId", uid), null, r -> info("Đã gửi lời mời tham gia nhóm."))))));
            }
        } else if (other > 0) {
            p.add(fullWidth(button("Xem hồ sơ", new AppIcon(AppIcon.Type.PEOPLE, Theme.TEXT, 15), () -> showProfile(other))));
            p.add(Box.createVerticalStrut(8));
            p.add(fullWidth(danger("Chặn người dùng", () -> block(other, true))));
            query("PROFILE_GET", of("targetUserId", other), r -> {
                Map<String, Object> u = map(r.get("profile"));
                if (u.isEmpty()) u = r;
                handle.setText("@" + str(u, "username"));
                String about = str(u, "bio");
                if (!about.isBlank()) {
                    bio.setText(about);
                    bio.setRows(Math.min(8, about.length() / 28 + 1));
                    bioTitle.setVisible(true);
                    bio.setVisible(true);
                }
                String mid = str(u, "avatarMediaId");
                if (!mid.isBlank()) app.thumbnails.load(mid, 168, icon -> face.setImage(icon.getImage()));
                p.revalidate();
                p.repaint();
            });
        }
        p.add(Box.createVerticalGlue());
        return p;
    }

    JLabel contextHeading(String text) {
        String sentence = text.isEmpty() ? text : text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
        JLabel l = new JLabel(sentence);
        l.setFont(Theme.font(Font.BOLD, 12));
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    JComponent fullWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        return c;
    }

    void createGroup() {
        String title = Dialogs.prompt(app, "Tạo nhóm mới", "Tên nhóm trò chuyện", "Tạo nhóm");
        if (title == null || title.isBlank()) return;
        mutate("GROUP_CREATE", of("title", title), null, r -> {
            Map<String, Object> c = map(r.get("conversation"));
            if (c.isEmpty()) c = new LinkedHashMap<>(r);
            c.putIfAbsent("kind", "GROUP");
            c.putIfAbsent("ownerId", id(app.currentUser, "user"));
            openChat(c);
        });
    }

    void groupInfo(Map<String, Object> original) {
        long cid = id(original, "conversation");
        if (!"GROUP".equals(str(original, "type", "kind"))) {
            warn("Đây là cuộc trò chuyện cá nhân.");
            return;
        }
        boolean owner = num(original, "ownerId") == id(app.currentUser, "user") || "OWNER".equals(str(original, "role"));
        JDialog d = dialog("Thông tin nhóm trò chuyện", 720, 600);
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setOpaque(false);

        JPanel header = vertical();
        header.add(label(name(original), true));
        header.add(readable("Lịch sử hiển thị kể từ thời điểm tham gia nhóm. Chặn người dùng không ẩn tin nhắn của họ trong nhóm chung."));

        JTextField title = new JTextField(name(original));
        Theme.styleInput(title);
        title.setPreferredSize(new Dimension(260, 38));
        if (owner) {
            JPanel renameRow = new JPanel(new BorderLayout(8, 0));
            renameRow.setOpaque(false);
            renameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            renameRow.setBorder(new EmptyBorder(10, 0, 0, 0));
            renameRow.add(title, BorderLayout.CENTER);
            renameRow.add(button("Đổi tên", () -> mutate("GROUP_RENAME", of("conversationId", cid, "title", title.getText(), "expectedVersion", num(original, "version")), null, r -> {
                d.dispose();
                showConversations();
            })), BorderLayout.EAST);
            renameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            header.add(renameRow);
        }

        JPanel groupActions = horizontal();
        if (owner) {
            groupActions.add(primary("Mời bạn bè", new AppIcon(AppIcon.Type.PEOPLE, Theme.ON_PRIMARY, 14), () -> selectFriend(uid -> mutate("GROUP_INVITE", of("conversationId", cid, "targetUserId", uid), null, r -> info("Đã gửi lời mời tham gia nhóm.")))));
        }
        groupActions.add(danger("Rời khỏi nhóm", () -> {
            if (confirm(owner ? "Chủ nhóm cần chuyển quyền trước khi rời (trừ khi là thành viên duy nhất). Tiếp tục?" : "Rời nhóm? Bạn sẽ mất quyền đọc lịch sử của nhóm.")) {
                mutate("GROUP_LEAVE", of("conversationId", cid, "expectedVersion", num(original, "version")), null, r -> {
                    d.dispose();
                    showConversations();
                });
            }
        }));
        if (owner) {
            groupActions.add(danger("Giải tán nhóm", () -> {
                if (confirm("Giải tán nhóm cho tất cả thành viên?")) {
                    mutate("GROUP_DISBAND", of("conversationId", cid, "expectedVersion", num(original, "version")), null, r -> {
                        d.dispose();
                        showConversations();
                    });
                }
            }));
        }
        header.add(groupActions);
        p.add(header, BorderLayout.NORTH);

        JPanel members = vertical();
        p.add(scroll(members), BorderLayout.CENTER);
        JLabel status = label("Đang tải danh sách thành viên…", false);
        String[] cursor = {null};
        JButton more = button("Xem thêm thành viên", () -> {});

        Runnable load = () -> {
            Map<String, Object> request = of("conversationId", cid, "limit", 20, "cursor", cursor[0]);
            query("GROUP_MEMBERS_GET", request, r -> {
                if (members.getComponentCount() >= 60) members.removeAll();
                for (Map<String, Object> member : rows(r.get("items"))) {
                    Map<String, Object> user = map(member.get("user"));
                    if (user.isEmpty()) user = member;
                    long uid = id(user, "user");
                    JPanel row = card();
                    JLabel memberName = label(name(user), false);
                    memberName.setForeground(Theme.TEXT);
                    memberName.setFont(Theme.font(Font.BOLD, 13));
                    row.add(horizontal(avatar(user, 32), memberName,
                            ModernUI.chip(roleText(str(member, "role")).toUpperCase(), "OWNER".equals(str(member, "role")) ? ModernUI.Tone.ACCENT : ModernUI.Tone.NEUTRAL)), BorderLayout.WEST);
                    JPanel actions = horizontal();
                    if (owner && uid != id(app.currentUser, "user")) {
                        actions.add(ghost("Chuyển quyền", () -> {
                            if (confirm("Chuyển quyền chủ nhóm cho người này?")) {
                                mutate("GROUP_OWNER_TRANSFER", of("conversationId", cid, "userId", uid, "expectedVersion", num(original, "version")), null, x -> {
                                    d.dispose();
                                    showConversations();
                                });
                            }
                        }));
                        actions.add(danger("Mời ra", () -> {
                            if (confirm("Mời thành viên này ra khỏi nhóm?")) {
                                mutate("GROUP_KICK", of("conversationId", cid, "userId", uid, "expectedVersion", num(original, "version")), null, x -> {
                                    d.dispose();
                                    showConversations();
                                });
                            }
                        }));
                    }
                    row.add(actions, BorderLayout.EAST);
                    members.add(row);
                }
                cursor[0] = str(r, "nextCursor");
                more.setEnabled(bool(r, "hasMore") || !cursor[0].isBlank());
                status.setText("Chỉ chủ nhóm mới có quyền đổi tên hoặc quản lý thành viên.");
                members.revalidate();
                members.repaint();
            });
        };
        more.addActionListener(e -> load.run());
        p.add(horizontal(status, more), BorderLayout.SOUTH);
        d.add(p);
        d.setVisible(true);
        load.run();
    }

    void selectFriend(LongConsumer selection) {
        JDialog d = dialog("Chọn bạn bè để mời", 550, 500);
        Page page = app.new Page("Chọn bạn bè để mời", "RELATION_LIST", of("kind", "FRIENDS"), rel -> {
            Map<String, Object> u = relationUser(rel);
            long uid = id(u, "user");
            JPanel row = card();
            JLabel who = label(name(u), false);
            who.setForeground(Theme.TEXT);
            who.setFont(Theme.font(Font.BOLD, 13));
            row.add(horizontal(avatar(u, 34), who), BorderLayout.CENTER);
            row.add(primary("Mời", () -> {
                d.dispose();
                selection.accept(uid);
            }), BorderLayout.EAST);
            return row;
        });
        d.add(page.panel);
        d.setVisible(true);
        page.load(true);
    }
}
