package com.p2p.client.ui.social;

import com.p2p.client.ui.AppIcon;
import com.p2p.client.ui.ModernUI;
import com.p2p.client.ui.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import static com.p2p.client.ui.social.Values.*;
import com.p2p.client.ui.social.SocialWindow.Page;

/** MessagesView screen: Conversation list, Public Groups discovery & join, rich Group Creation with friend invites, and Group management. */
final class MessagesView extends ViewSupport {
    MessagesView(SocialWindow app) { super(app); }

    void showConversations() {
        app.convPage = null;
        app.convPanelCache = null;
        app.selectedConvId = 0;
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        JPanel emptyActions = horizontal(
                primary(tr("Tạo nhóm mới", "Create Group"), new AppIcon(AppIcon.Type.PLUS, Theme.ON_PRIMARY, 14), this::createGroup),
                button(tr("Khám phá nhóm công khai", "Explore Public Groups"), new AppIcon(AppIcon.Type.PEOPLE, Theme.TEXT, 15), this::showPublicGroups)
        );
        empty.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.CHAT, Theme.MUTED, 30),
                tr("Chọn một cuộc trò chuyện", "Select a Conversation"),
                tr("Chọn từ danh sách bên trái, khám phá nhóm công khai hoặc tạo nhóm mới.", "Choose from the left list, browse public groups, or create a new group."),
                emptyActions));
        JPanel shell = messagesShell(empty);
        setView("conversations", shell, this::showConversations);
        app.convPage.load(true);
    }

    /** Restored Public Groups discovery & join view inside the two-pane messaging layout. */
    void showPublicGroups() {
        app.selectedConvId = 0;
        Page pub = app.new Page(
                tr("Nhóm công khai", "Public Groups"),
                "GROUP_PUBLIC_LIST",
                of(),
                this::publicGroupCard,
                primary(tr("Tạo nhóm mới", "Create Group"), new AppIcon(AppIcon.Type.PLUS, Theme.ON_PRIMARY, 14), this::createGroup)
        );
        pub.emptyIcon = AppIcon.Type.PEOPLE;
        pub.emptyTitle = tr("Chưa có nhóm công khai nào", "No public groups yet");
        pub.emptyHint = tr("Hãy tạo nhóm đầu tiên để mọi người có thể tham gia trò chuyện!", "Create the first group so others can join!");

        JPanel shell = messagesShell(pub.panel);
        setView("publicGroups", shell, this::showPublicGroups);
        if (app.convPage != null) app.convPage.load(true);
        pub.load(true);
    }

    private JComponent publicGroupCard(Map<String, Object> g) {
        JPanel c = card();
        long gid = id(g, "conversation");
        String title = name(g);
        String ownerName = str(g, "ownerName");
        long memberCount = num(g, "memberCount");
        boolean joined = num(g, "joined") > 0 || bool(g, "joined");
        boolean canJoin = bool(g, "canJoin");

        JPanel info = vertical();
        JLabel nameLbl = label(title, true);
        nameLbl.setFont(Theme.font(Font.BOLD, 15));
        String meta = tr("Chủ nhóm: ", "Owner: ") + (ownerName.isBlank() ? "#" + num(g, "ownerId") : ownerName)
                + " · " + memberCount + " / 20 " + tr("thành viên", "members");
        info.add(nameLbl);
        info.add(muted(meta));
        c.add(info, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        if (joined) {
            actions.add(ModernUI.chip(tr("ĐÃ THAM GIA", "JOINED"), ModernUI.Tone.SUCCESS));
            actions.add(button(tr("Mở nhóm", "Open Chat"), () -> {
                Map<String, Object> conv = new LinkedHashMap<>(g);
                conv.put("conversationId", gid);
                conv.put("kind", "GROUP");
                conv.put("canSend", memberCount >= 2);
                if (memberCount < 2) conv.put("denyReason", "NEED_MEMBERS");
                openChat(conv);
            }));
        } else if (canJoin) {
            JButton joinBtn = primary(tr("Tham gia nhóm", "Join Group"), () -> {});
            joinBtn.addActionListener(e -> mutate("GROUP_JOIN", of("conversationId", gid), joinBtn, res -> {
                info(tr("Đã tham gia nhóm: ", "Joined group: ") + title);
                Map<String, Object> conv = new LinkedHashMap<>(g);
                conv.putAll(res);
                conv.put("conversationId", gid);
                conv.put("kind", "GROUP");
                conv.put("title", title);
                conv.put("memberCount", Math.max(2, memberCount + 1));
                conv.put("canSend", true);
                openChat(conv);
            }));
            actions.add(joinBtn);
        } else {
            JButton fullBtn = button(tr("Nhóm đã đầy (20/20)", "Full (20/20)"), () -> {});
            fullBtn.setEnabled(false);
            actions.add(fullBtn);
        }
        c.add(actions, BorderLayout.EAST);
        return c;
    }

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
        app.convPage.emptyTitle = tr("Chưa có cuộc trò chuyện", "No conversations yet");
        app.convPage.emptyHint = tr("Bấm dấu + để tạo nhóm, xem Nhóm công khai hoặc nhắn tin từ Bạn bè.",
                "Click + to create a group, browse Public Groups, or message a friend.");
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
        JLabel heading = new JLabel(tr("Tin nhắn", "Messages"));
        heading.setFont(Theme.font(Font.BOLD, 18));
        heading.setForeground(Theme.TEXT);
        JButton add = Theme.ghostButton("");
        add.setIcon(new AppIcon(AppIcon.Type.PLUS, Theme.TEXT_SECONDARY, 16));
        add.setToolTipText(tr("Tạo nhóm mới", "Create new group"));
        add.getAccessibleContext().setAccessibleName(tr("Tạo nhóm mới", "Create new group"));
        add.putClientProperty("FlatLaf.style", "margin:6,6,6,6");
        add.addActionListener(e -> createGroup());
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(heading, BorderLayout.CENTER);
        titleRow.add(add, BorderLayout.EAST);
        JTextField search = new JTextField();
        search.setName("conversationSearch");
        Theme.styleInput(search);
        search.putClientProperty("JTextField.placeholderText", tr("Tìm cuộc trò chuyện", "Filter conversations"));
        search.getAccessibleContext().setAccessibleName(tr("Tìm cuộc trò chuyện", "Filter conversations"));
        search.setPreferredSize(new Dimension(100, 36));
        search.getDocument().addDocumentListener(new DocumentListener() {
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
            public void insertUpdate(DocumentEvent e) { apply(); }
            public void removeUpdate(DocumentEvent e) { apply(); }
            public void changedUpdate(DocumentEvent e) { apply(); }
        });
        top.add(titleRow, BorderLayout.NORTH);
        top.add(search, BorderLayout.CENTER);
        p.add(top, BorderLayout.NORTH);
        p.add(app.convPage.panel, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(2, 1, 0, 2));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), new EmptyBorder(6, 8, 6, 8)));
        JButton publicGroupsBtn = ghost(tr("Nhóm công khai", "Public Groups"), new AppIcon(AppIcon.Type.PEOPLE, Theme.PRIMARY, 15), this::showPublicGroups);
        publicGroupsBtn.setHorizontalAlignment(SwingConstants.LEFT);
        JButton invites = ghost(tr("Lời mời vào nhóm", "Group Invites"), new AppIcon(AppIcon.Type.NOTIFICATION, Theme.TEXT_SECONDARY, 15), this::showGroupInvites);
        invites.setHorizontalAlignment(SwingConstants.LEFT);
        bottom.add(publicGroupsBtn);
        bottom.add(invites);
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
        String avatarMid = str(conv, "avatarMediaId");
        if (!group && !avatarMid.isBlank()) {
            app.thumbnails.load(avatarMid, 76, icon -> {
                face.setImage(icon.getImage());
                row.repaint();
            });
        }
        JPanel faceSlot = new JPanel(new GridBagLayout());
        faceSlot.setOpaque(false);
        faceSlot.add(face);
        row.add(faceSlot, BorderLayout.WEST);

        String preview = unread > 0
                ? unread + tr(" tin nhắn chưa đọc", " unread")
                : group ? tr("Nhóm trò chuyện", "Group chat") : tr("Tin nhắn riêng", "Direct chat");
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
        row.getAccessibleContext().setAccessibleName(name(conv) + (unread > 0 ? ", " + unread + tr(" chưa đọc", " unread") : ""));
        return row;
    }

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
            long convId = num(invite, "conversationId");
            String gTitle = str(invite, "title", "groupTitle");
            Runnable done = () -> {
                if (app.invitesDialog != null) app.invitesDialog.dispose();
                showConversations();
            };
            if (num(invite, "inviteeId") != id(app.currentUser, "user")) {
                actions.add(button(tr("Hủy lời mời", "Cancel Invite"), () -> mutate("GROUP_INVITE_CANCEL", of("inviteId", inviteId), null, r -> done.run())));
            } else {
                actions.add(primary(tr("Tham gia nhóm", "Accept & Join"), () -> mutate("GROUP_INVITE_ACCEPT", of("inviteId", inviteId), null, r -> {
                    if (app.invitesDialog != null) app.invitesDialog.dispose();
                    info(tr("Bạn có thể xem lịch sử kể từ thời điểm tham gia nhóm.", "Joined group! History is visible from your join time."));
                    openChat(of("conversationId", convId, "kind", "GROUP", "title", gTitle, "canSend", true, "memberCount", 2));
                })));
                actions.add(button(tr("Từ chối", "Decline"), () -> mutate("GROUP_INVITE_REJECT", of("inviteId", inviteId), null, r -> done.run())));
            }
            if (!"PENDING".equals(str(invite, "status"))) disableButtons(actions);
            c.add(actions, BorderLayout.SOUTH);
            return c;
        });
        invites.emptyTitle = tr("Không có lời mời nhóm nào", "No group invitations");
        invites.emptyHint = tr("Lời mời bạn nhận được hoặc đã gửi sẽ hiện tại đây.", "Received and sent group invitations appear here.");
        app.invitesDialog = dialog(tr("Lời mời tham gia nhóm", "Group Invitations"), 560, 520);
        app.invitesDialog.setLayout(new BorderLayout());
        app.invitesDialog.add(invites.panel, BorderLayout.CENTER);
        app.invitesDialog.setVisible(true);
        invites.load(true);
    }

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
        JLabel handle = muted(group ? tr("Nhóm trò chuyện · #", "Group chat · #") + cid : " ");
        handle.setFont(Theme.font(Font.PLAIN, 12));
        handle.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel status = group ? ModernUI.chip(tr("NHÓM", "GROUP"), ModernUI.Tone.SUCCESS)
                : bool(conv, "online") ? ModernUI.chip(tr("ĐANG ONLINE", "ONLINE"), ModernUI.Tone.SUCCESS) : ModernUI.chip("OFFLINE", ModernUI.Tone.NEUTRAL);
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
        JLabel bioTitle = contextHeading(tr("GIỚI THIỆU", "BIO"));
        bioTitle.setVisible(false);
        p.add(Box.createVerticalStrut(22));
        p.add(bioTitle);
        p.add(bio);

        p.add(Box.createVerticalStrut(18));
        if (group) {
            boolean owner = num(conv, "ownerId") == id(app.currentUser, "user") || "OWNER".equals(str(conv, "role"));
            p.add(fullWidth(button(tr("Quản lý thành viên", "Manage Members"), new AppIcon(AppIcon.Type.PEOPLE, Theme.TEXT, 15), () -> groupInfo(conv))));
            if (owner) {
                p.add(Box.createVerticalStrut(8));
                p.add(fullWidth(button(tr("Mời bạn bè", "Invite Friends"), new AppIcon(AppIcon.Type.PLUS, Theme.TEXT, 15), () -> selectFriend(uid ->
                        mutate("GROUP_INVITE", of("conversationId", cid, "targetUserId", uid), null, r -> info(tr("Đã gửi lời mời tham gia nhóm.", "Group invitation sent.")))))));
            }
        } else if (other > 0) {
            p.add(fullWidth(button(tr("Xem hồ sơ", "View Profile"), new AppIcon(AppIcon.Type.PEOPLE, Theme.TEXT, 15), () -> showProfile(other))));
            p.add(Box.createVerticalStrut(8));
            p.add(fullWidth(danger(tr("Chặn người dùng", "Block User"), () -> block(other, true))));
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

    /** Rich Group Creation dialog supporting group title + optional initial friend invitations. */
    void createGroup() {
        JDialog d = dialog(tr("Tạo nhóm trò chuyện mới", "Create New Group"), 540, 500);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);

        JPanel top = vertical();
        JTextField titleField = new JTextField();
        Theme.styleInput(titleField);
        titleField.putClientProperty("JTextField.placeholderText", tr("Ví dụ: Nhóm Đồ Án Lập Trình Mạng (tối đa 60 ký tự)", "e.g., Network Programming Team (max 60 chars)"));
        addField(top, tr("Tên nhóm trò chuyện", "Group Name"), titleField);

        JTextField filterFriends = new JTextField();
        Theme.styleInput(filterFriends);
        filterFriends.putClientProperty("JTextField.placeholderText", tr("Tìm bạn bè để mời vào nhóm (không bắt buộc)…", "Filter friends to invite (optional)…"));
        addField(top, tr("Mời bạn bè ngay khi tạo (tối đa 19)", "Invite friends now (optional, max 19)"), filterFriends);
        root.add(top, BorderLayout.NORTH);

        JPanel friendsList = vertical();
        JLabel loadingLbl = muted(tr("Đang tải danh sách bạn bè…", "Loading friends list…"));
        friendsList.add(loadingLbl);
        root.add(scroll(friendsList), BorderLayout.CENTER);

        Set<Long> selectedIds = new LinkedHashSet<>();
        List<JComponent> friendItems = new ArrayList<>();
        JLabel selectedCounter = muted(tr("Đã chọn: 0 / 19 bạn bè", "Selected: 0 / 19 friends"));

        query("RELATION_LIST", of("kind", "FRIENDS", "limit", 50), r -> {
            friendsList.removeAll();
            List<Map<String, Object>> items = rows(r.get("items"));
            if (items.isEmpty()) {
                friendsList.add(muted(tr("Chưa có bạn bè. Bạn vẫn có thể tạo nhóm công khai để người khác tham gia!",
                        "No friends yet. You can still create the group and others can join from Public Groups!")));
            } else {
                for (Map<String, Object> rel : items) {
                    Map<String, Object> u = relationUser(rel);
                    long uid = id(u, "user");
                    if (uid <= 0) continue;
                    String uname = name(u);
                    String handle = str(u, "username");
                    JPanel row = card();
                    row.setBorder(new EmptyBorder(8, 12, 8, 12));
                    JCheckBox cb = new JCheckBox(uname + (handle.isBlank() ? "" : " (@" + handle + ")"));
                    cb.setOpaque(false);
                    cb.setForeground(Theme.TEXT);
                    cb.setFont(Theme.font(Font.PLAIN, 13));
                    cb.addActionListener(ev -> {
                        if (cb.isSelected()) {
                            if (selectedIds.size() >= 19) {
                                cb.setSelected(false);
                                warn(tr("Tối đa mời 19 bạn bè (nhóm tối đa 20 thành viên).", "Maximum 19 invitees per group."));
                                return;
                            }
                            selectedIds.add(uid);
                        } else {
                            selectedIds.remove(uid);
                        }
                        selectedCounter.setText(tr("Đã chọn: " + selectedIds.size() + " / 19 bạn bè",
                                "Selected: " + selectedIds.size() + " / 19 friends"));
                    });
                    row.add(cb, BorderLayout.CENTER);
                    row.putClientProperty("filterText", (uname + " " + handle).toLowerCase(Locale.ROOT));
                    friendItems.add(row);
                    friendsList.add(row);
                    friendsList.add(Box.createVerticalStrut(6));
                }
            }
            friendsList.revalidate();
            friendsList.repaint();
        });

        filterFriends.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String q = filterFriends.getText().trim().toLowerCase(Locale.ROOT);
                for (JComponent item : friendItems) {
                    String ft = String.valueOf(item.getClientProperty("filterText"));
                    item.setVisible(q.isEmpty() || ft.contains(q));
                }
                friendsList.revalidate();
                friendsList.repaint();
            }
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });

        JButton submit = Theme.primaryButton(tr("Tạo nhóm", "Create Group"));
        submit.addActionListener(e -> {
            String title = titleField.getText().trim();
            if (title.isEmpty() || title.length() > 60) {
                warn(tr("Tên nhóm cần từ 1 đến 60 ký tự.", "Group name must be 1–60 characters."));
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", title);
            if (!selectedIds.isEmpty()) {
                payload.put("inviteeIds", new ArrayList<>(selectedIds));
            }
            mutate("GROUP_CREATE", payload, submit, r -> {
                d.dispose();
                Map<String, Object> c = map(r.get("conversation"));
                if (c.isEmpty()) c = new LinkedHashMap<>(r);
                c.putIfAbsent("kind", "GROUP");
                c.putIfAbsent("ownerId", id(app.currentUser, "user"));
                c.putIfAbsent("role", "OWNER");
                c.putIfAbsent("memberCount", 1L);
                c.putIfAbsent("canSend", false);
                c.putIfAbsent("denyReason", "NEED_MEMBERS");
                if (!selectedIds.isEmpty()) {
                    info(tr("Đã tạo nhóm và gửi " + selectedIds.size() + " lời mời!", "Group created and sent " + selectedIds.size() + " invitations!"));
                } else {
                    info(tr("Đã tạo nhóm công khai thành công!", "Public group created!"));
                }
                openChat(c);
            });
        });

        root.add(actionRow(selectedCounter, button(tr("Đóng", "Cancel"), d::dispose), submit), BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(submit);
        d.add(root);
        d.setVisible(true);
        SwingUtilities.invokeLater(titleField::requestFocusInWindow);
    }

    void groupInfo(Map<String, Object> original) {
        long cid = id(original, "conversation");
        if (!"GROUP".equals(str(original, "type", "kind"))) {
            warn(tr("Đây là cuộc trò chuyện cá nhân.", "This is a direct conversation."));
            return;
        }
        boolean owner = num(original, "ownerId") == id(app.currentUser, "user") || "OWNER".equals(str(original, "role"));
        JDialog d = dialog(tr("Thông tin nhóm trò chuyện", "Group Information"), 720, 600);
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setOpaque(false);

        JPanel header = vertical();
        header.add(label(name(original), true));
        header.add(readable(tr("Lịch sử hiển thị kể từ thời điểm tham gia nhóm. Chặn người dùng không ẩn tin nhắn của họ trong nhóm chung.",
                "Message history is visible from your join time. Blocking a user does not hide their messages inside shared groups.")));

        JTextField title = new JTextField(name(original));
        Theme.styleInput(title);
        title.setPreferredSize(new Dimension(260, 38));
        if (owner) {
            JPanel renameRow = new JPanel(new BorderLayout(8, 0));
            renameRow.setOpaque(false);
            renameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            renameRow.setBorder(new EmptyBorder(10, 0, 0, 0));
            renameRow.add(title, BorderLayout.CENTER);
            renameRow.add(button(tr("Đổi tên", "Rename"), () -> mutate("GROUP_RENAME", of("conversationId", cid, "title", title.getText(), "expectedVersion", num(original, "version")), null, r -> {
                d.dispose();
                showConversations();
            })), BorderLayout.EAST);
            renameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            header.add(renameRow);
        }

        JPanel groupActions = horizontal();
        if (owner) {
            groupActions.add(primary(tr("Mời bạn bè", "Invite Friends"), new AppIcon(AppIcon.Type.PEOPLE, Theme.ON_PRIMARY, 14),
                    () -> selectFriend(uid -> mutate("GROUP_INVITE", of("conversationId", cid, "targetUserId", uid), null,
                            r -> info(tr("Đã gửi lời mời tham gia nhóm.", "Group invitation sent."))))));
        }
        groupActions.add(danger(tr("Rời khỏi nhóm", "Leave Group"), () -> {
            if (confirm(owner
                    ? tr("Chủ nhóm cần chuyển quyền trước khi rời (trừ khi là thành viên duy nhất). Tiếp tục?", "Owner must transfer ownership before leaving (unless sole member). Continue?")
                    : tr("Rời nhóm? Bạn sẽ mất quyền đọc lịch sử của nhóm.", "Leave this group? You will lose access to its message history."))) {
                mutate("GROUP_LEAVE", of("conversationId", cid, "expectedVersion", num(original, "version")), null, r -> {
                    d.dispose();
                    showConversations();
                });
            }
        }));
        if (owner) {
            groupActions.add(danger(tr("Giải tán nhóm", "Disband Group"), () -> {
                if (confirm(tr("Giải tán nhóm cho tất cả thành viên?", "Disband this group for all members?"))) {
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
        JLabel status = label(tr("Đang tải danh sách thành viên…", "Loading members…"), false);
        String[] cursor = {null};
        JButton more = button(tr("Xem thêm thành viên", "Load more"), () -> {});

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
                        actions.add(ghost(tr("Chuyển quyền", "Make Owner"), () -> {
                            if (confirm(tr("Chuyển quyền chủ nhóm cho người này?", "Transfer group ownership to this member?"))) {
                                mutate("GROUP_OWNER_TRANSFER", of("conversationId", cid, "userId", uid, "expectedVersion", num(original, "version")), null, x -> {
                                    d.dispose();
                                    showConversations();
                                });
                            }
                        }));
                        actions.add(danger(tr("Mời ra", "Remove"), () -> {
                            if (confirm(tr("Mời thành viên này ra khỏi nhóm?", "Remove this member from the group?"))) {
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
                status.setText(tr("Chỉ chủ nhóm mới có quyền đổi tên hoặc quản lý thành viên.", "Only the group owner can rename or manage members."));
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
        JDialog d = dialog(tr("Chọn bạn bè để mời", "Select Friend to Invite"), 550, 500);
        Page page = app.new Page(tr("Chọn bạn bè để mời", "Select Friend to Invite"), "RELATION_LIST", of("kind", "FRIENDS"), rel -> {
            Map<String, Object> u = relationUser(rel);
            long uid = id(u, "user");
            JPanel row = card();
            JLabel who = label(name(u), false);
            who.setForeground(Theme.TEXT);
            who.setFont(Theme.font(Font.BOLD, 13));
            row.add(horizontal(avatar(u, 34), who), BorderLayout.CENTER);
            row.add(primary(tr("Mời", "Invite"), () -> {
                d.dispose();
                selection.accept(uid);
            }), BorderLayout.EAST);
            return row;
        });
        page.emptyTitle = tr("Chưa có bạn bè", "No friends yet");
        page.emptyHint = tr("Hãy kết bạn trước khi gửi lời mời riêng.", "Add friends first to send direct group invitations.");
        d.add(page.panel);
        d.setVisible(true);
        page.load(true);
    }
}
