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

/** FeedView screen. Extracted from SocialWindow; behaviour is unchanged. */
final class FeedView extends ViewSupport {
    FeedView(SocialWindow app) { super(app); }

    // ================= FEED =================
    void showFeed() {
        Page p = app.new Page("Bảng tin", "FEED_GET", of(), this::postCard);
        p.emptyIcon = AppIcon.Type.FEED;
        p.emptyTitle = "Chưa có bài viết nào";
        p.emptyHint = "Hãy là người đầu tiên chia sẻ điều gì đó.";

        // Inline composer sits directly under the page title; it opens the real post editor.
        JPanel composer = card();
        composer.putClientProperty("CardPanel.fillWidth", false);
        composer.setLayout(new BorderLayout(12, 0));
        composer.setBorder(new EmptyBorder(12, 14, 12, 14));
        JPanel own = new JPanel(new GridBagLayout());
        own.setOpaque(false);
        own.add(avatar(app.currentUser, 38));
        JButton prompt = Theme.secondaryButton("Bạn muốn chia sẻ điều gì?");
        prompt.setFont(Theme.font(Font.PLAIN, 14));
        prompt.setForeground(Theme.MUTED);
        prompt.setHorizontalAlignment(SwingConstants.LEFT);
        prompt.putClientProperty("FlatLaf.style", prompt.getClientProperty("FlatLaf.style") + "; arc:20; margin:9,16,9,16");
        prompt.getAccessibleContext().setAccessibleName("Soạn bài viết mới");
        prompt.addActionListener(e -> composePost(null));
        JButton share = primary("Chia sẻ", () -> composePost(null));
        composer.add(own, BorderLayout.WEST);
        composer.add(prompt, BorderLayout.CENTER);
        composer.add(share, BorderLayout.EAST);
        JPanel composerWrap = new JPanel(new BorderLayout());
        composerWrap.setOpaque(false);
        composerWrap.setBorder(new EmptyBorder(0, 24, 12, 24));
        composerWrap.add(composer);

        BorderLayout pageLayout = (BorderLayout) p.panel.getLayout();
        Component header = pageLayout.getLayoutComponent(BorderLayout.NORTH);
        p.panel.remove(header);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(centered(composerWrap, Theme.COLUMN_WIDTH + 48), BorderLayout.CENTER);
        p.panel.add(top, BorderLayout.NORTH);

        setView("feed", p.panel, this::showFeed);
        setContext(feedContext());
        p.load(true);
    }

    /** Right-hand panel of the feed: your profile summary, friends with live presence, pending requests. */
    JComponent feedContext() {
        JPanel p = vertical();
        p.setBorder(new EmptyBorder(26, 20, 20, 20));
        long me = id(app.currentUser, "user");

        JPanel head = new JPanel(new GridBagLayout());
        head.setOpaque(false);
        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        Theme.Avatar face = new Theme.Avatar(name(app.currentUser), 72, false);
        face.setRing(Theme.NAV_BACKGROUND);
        face.setAlignmentX(Component.CENTER_ALIGNMENT);
        String ownMedia = str(app.currentUser, "avatarMediaId");
        if (!ownMedia.isBlank()) app.thumbnails.load(ownMedia, 144, icon -> face.setImage(icon.getImage()));
        JLabel who = new JLabel(name(app.currentUser));
        who.setFont(Theme.font(Font.BOLD, 16));
        who.setForeground(Theme.TEXT);
        who.setAlignmentX(Component.CENTER_ALIGNMENT);
        who.putClientProperty("html.disable", Boolean.TRUE);
        JLabel handle = muted("@" + str(app.currentUser, "username"));
        handle.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel role = ModernUI.chip("ADMIN".equals(str(app.currentUser, "role")) ? "QUẢN TRỊ VIÊN" : "THÀNH VIÊN", ModernUI.Tone.NEUTRAL);
        role.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(face);
        inner.add(Box.createVerticalStrut(12));
        inner.add(who);
        inner.add(Box.createVerticalStrut(2));
        inner.add(handle);
        inner.add(Box.createVerticalStrut(10));
        inner.add(role);
        head.add(inner);
        p.add(head);
        p.add(Box.createVerticalStrut(14));
        p.add(fullWidth(button("Hồ sơ của tôi", () -> showProfile(me))));

        p.add(Box.createVerticalStrut(26));
        p.add(contextHeading("BẠN BÈ"));
        JPanel friends = new JPanel();
        friends.setOpaque(false);
        friends.setLayout(new BoxLayout(friends, BoxLayout.Y_AXIS));
        friends.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel loading = muted("Đang tải…");
        friends.add(loading);
        p.add(friends);

        JPanel pending = new JPanel(new BorderLayout());
        pending.setOpaque(false);
        pending.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(Box.createVerticalStrut(18));
        p.add(pending);

        long token = app.generation;
        query("RELATION_LIST", of("kind", "FRIENDS", "limit", 8), r -> {
            if (token != app.generation) return;
            friends.removeAll();
            List<Map<String, Object>> items = rows(r.get("items"));
            if (items.isEmpty()) {
                JTextArea none = readable("Chưa có bạn bè. Dùng mục “Bạn bè & Tìm người” để kết nối.");
                none.setForeground(Theme.MUTED);
                none.setAlignmentX(Component.LEFT_ALIGNMENT);
                friends.add(none);
            }
            Map<Long, Theme.Avatar> faces = new LinkedHashMap<>();
            Map<Long, JLabel> states = new LinkedHashMap<>();
            for (Map<String, Object> item : items) {
                Map<String, Object> u = relationUser(item);
                long uid = num(u, "userId", "id", "otherUserId", "targetUserId");
                if (uid == 0) continue;
                friends.add(friendRow(u, uid, faces, states));
                friends.add(Box.createVerticalStrut(2));
            }
            friends.revalidate();
            friends.repaint();
            if (!faces.isEmpty()) {
                query("PRESENCE_GET", of("userIds", new ArrayList<>(faces.keySet())), presence -> {
                    if (token != app.generation) return;
                    for (Map<String, Object> state : rows(presence.get("items"))) {
                        long uid = num(state, "userId");
                        boolean online = bool(state, "online");
                        Theme.Avatar f = faces.get(uid);
                        JLabel s = states.get(uid);
                        if (f != null) f.setOnline(online);
                        if (s != null) {
                            s.setText(online ? "Đang online" : "Offline");
                            s.setForeground(online ? Theme.ONLINE : Theme.MUTED);
                        }
                    }
                });
            }
        });
        query("RELATION_LIST", of("kind", "INCOMING", "limit", 20), r -> {
            if (token != app.generation) return;
            int count = rows(r.get("items")).size();
            if (count > 0) {
                pending.removeAll();
                JPanel box = new JPanel();
                box.setOpaque(false);
                box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
                JLabel h = contextHeading("LỜI MỜI ĐANG CHỜ");
                box.add(h);
                JButton open = fullWidthButton(count + " lời mời kết bạn · Xem", () -> app.peopleView.openMode("INCOMING"));
                box.add(open);
                pending.add(box);
                pending.revalidate();
                pending.repaint();
            }
        });
        return scroll(p);
    }

    JButton fullWidthButton(String text, Runnable action) {
        JButton b = button(text, action);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        return b;
    }

    JComponent friendRow(Map<String, Object> user, long uid, Map<Long, Theme.Avatar> faces, Map<Long, JLabel> states) {
        ListRow row = new ListRow(Theme.NAV_BACKGROUND, false, () -> showProfile(uid));
        row.setBorder(new EmptyBorder(6, 6, 6, 6));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        Theme.Avatar face = new Theme.Avatar(name(user), 32, false);
        row.ring(face);
        face.setOnline(false);
        String mid = str(user, "avatarMediaId");
        if (!mid.isBlank()) app.thumbnails.load(mid, 64, icon -> face.setImage(icon.getImage()));
        faces.put(uid, face);
        JPanel faceSlot = new JPanel(new GridBagLayout());
        faceSlot.setOpaque(false);
        faceSlot.add(face);
        row.add(faceSlot, BorderLayout.WEST);
        JLabel nm = new JLabel(name(user));
        nm.setFont(Theme.font(Font.PLAIN, 13));
        nm.setForeground(Theme.TEXT);
        nm.putClientProperty("html.disable", Boolean.TRUE);
        JLabel st = new JLabel("Đang cập nhật…");
        st.setFont(Theme.font(Font.PLAIN, 11));
        st.setForeground(Theme.MUTED);
        states.put(uid, st);
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(nm);
        texts.add(st);
        row.add(texts, BorderLayout.CENTER);
        row.getAccessibleContext().setAccessibleName("Hồ sơ " + name(user));
        return row;
    }

    JComponent postCard(Map<String, Object> post) {
        JPanel c = card();
        long postId = id(post, "post");
        c.putClientProperty("postId", postId);

        Map<String, Object> author = map(post.get("author"));
        if (author.isEmpty()) {
            author = of("userId", num(post, "authorId"), "displayName", str(post, "authorName", "displayName"), "avatarMediaId", str(post, "authorAvatarMediaId"));
        }
        Map<String, Object> profile = author;

        // Post Header
        JPanel postHeader = new JPanel(new BorderLayout(8, 0));
        postHeader.setOpaque(false);
        JPanel authorBox = horizontal(
                avatar(author, 40),
                authorLink(name(author), () -> showProfile(id(profile, "user"))),
                muted(time(post) + " · " + visibility(str(post, "visibility")))
        );
        postHeader.add(authorBox, BorderLayout.WEST);
        c.add(postHeader, BorderLayout.NORTH);

        if (bool(post, "unavailable") || "HIDDEN".equals(str(post, "status"))) {
            c.add(readable("Bài viết đã bị ẩn.\n" + str(post, "moderationReason")));
            return c;
        }

        JPanel content = vertical();
        content.add(readable(str(post, "content")));

        List<Map<String, Object>> images = rows(post.get("media"));
        if (images.isEmpty()) images = rows(post.get("images"));
        List<Component> imageButtons = new ArrayList<>();
        for (Map<String, Object> image : images) {
            String mid = str(image, "mediaId", "id");
            imageButtons.add(photo(mid, str(image, "fileName")));
        }
        if (imageButtons.isEmpty() && post.get("mediaIds") instanceof Collection<?> ids) {
            for (Object raw : ids) {
                String mid = String.valueOf(raw);
                imageButtons.add(photo(mid, ""));
            }
        }
        if (!imageButtons.isEmpty()) {
            content.add(horizontal(imageButtons.toArray(Component[]::new)));
        }
        c.add(content, BorderLayout.CENTER);

        // Actions
        boolean liked = bool(post, "liked");
        JButton like = ghost((liked ? "Đã thích" : "Thích") + " (" + num(post, "likeCount") + ")", () -> {});
        like.setIcon(new AppIcon(liked ? AppIcon.Type.HEART_FILL : AppIcon.Type.HEART, liked ? Theme.LIKE : Theme.TEXT_SECONDARY, 15));
        if (liked) like.setForeground(Theme.LIKE);
        like.addActionListener(e -> mutate("LIKE_SET", of("postId", postId, "liked", !liked), like, r -> app.refreshCurrent.run()));

        JButton commentBtn = ghost("Bình luận (" + num(post, "commentCount") + ")", new AppIcon(AppIcon.Type.CHAT, Theme.TEXT_SECONDARY, 15), () -> showPost(postId));
        JButton reportBtn = ghost("Báo cáo", new AppIcon(AppIcon.Type.SHIELD, Theme.TEXT_SECONDARY, 15), () -> report("POST", postId));

        JPanel actions = horizontal(like, commentBtn, reportBtn);
        actions.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), new EmptyBorder(6, 0, 0, 0)));
        if (num(post, "authorId") == id(app.currentUser, "user") || id(author, "user") == id(app.currentUser, "user")) {
            actions.add(ghost("Sửa", () -> composePost(post)));
            actions.add(ghost("Xóa", () -> {
                if (confirm("Bạn có chắc muốn xóa bài viết này?")) {
                    mutate("POST_DELETE", of("postId", postId, "expectedVersion", num(post, "version")), null, r -> app.refreshCurrent.run());
                }
            }));
        }
        c.add(actions, BorderLayout.SOUTH);
        return c;
    }

    /** Author name rendered as a quiet text link (no button chrome). */
    JButton authorLink(String text, Runnable action) {
        JButton b = ghost(text, action);
        b.setFont(Theme.font(Font.BOLD, 14));
        b.setForeground(Theme.TEXT);
        b.putClientProperty("FlatLaf.style", "margin:2,6,2,6; foreground:#F5F5F7; hoverBackground:#181B22; arc:6");
        return b;
    }

    String visibility(String code) {
        return switch (code) {
            case "PUBLIC" -> "Công khai";
            case "ONLY_ME" -> "Chỉ mình tôi";
            default -> "Bạn bè";
        };
    }

    void composePost(Map<String, Object> existing) {
        Map<String, Object> editing = existing == null ? null : new LinkedHashMap<>(existing);
        JDialog d = dialog(existing == null ? "Chia sẻ bài viết mới" : "Chỉnh sửa bài viết", 640, 500);
        JPanel p = vertical();
        JTextArea content = text(existing == null ? "" : str(existing, "content"), 8);
        JComboBox<String> privacy = new JComboBox<>(new String[]{"FRIENDS", "PUBLIC", "ONLY_ME"});
        privacy.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(l, visibility(String.valueOf(v)), i, selected, focus);
            }
        });
        if (existing != null) privacy.setSelectedItem(str(existing, "visibility"));

        JLabel composeTitle = label("Bạn muốn chia sẻ điều gì với mọi người?", true);
        composeTitle.setFont(Theme.font(Font.BOLD, 15));
        p.add(composeTitle);
        p.add(Box.createVerticalStrut(10));
        content.putClientProperty("JTextField.placeholderText", "Chia sẻ điều bạn đang nghĩ…");
        p.add(framed(content));
        JLabel counter = muted(content.getText().length() + " / 5.000");
        counter.setAlignmentX(Component.RIGHT_ALIGNMENT);
        counter.setBorder(new EmptyBorder(4, 0, 6, 2));
        JPanel counterRow = new JPanel(new BorderLayout());
        counterRow.setOpaque(false);
        counterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        counterRow.add(counter, BorderLayout.EAST);
        p.add(counterRow);
        content.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void apply() {
                int n = content.getText().length();
                counter.setText(n + " / 5.000");
                counter.setForeground(n > 5000 ? Theme.DANGER : Theme.MUTED);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { apply(); }
        });
        addField(p, "Quyền xem bài viết", privacy);

        List<String> ids = new ArrayList<>();
        JPanel attachments = vertical();
        p.add(attachments);
        JButton submit = Theme.primaryButton(existing == null ? "Đăng bài" : "Lưu thay đổi");
        int[] uploading = {0};

        JButton image = button("Thêm ảnh (tối đa 4)", () -> {
            if (ids.size() + uploading[0] >= 4) {
                warn("Chỉ có thể đính kèm tối đa 4 ảnh.");
                return;
            }
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(d) != JFileChooser.APPROVE_OPTION) return;
            uploading[0]++;
            submit.setEnabled(false);
            JLabel pending = label("Đang tải ảnh lên máy chủ…", false);
            attachments.add(pending);
            attachments.revalidate();
            app.media.upload(d, chooser.getSelectedFile().toPath(), "POST_IMAGE", mediaId -> {
                uploading[0]--;
                ids.add(mediaId);
                attachments.remove(pending);
                attachments.add(horizontal(label("Ảnh #" + mediaId + " đã sẵn sàng", false), button("Bỏ ảnh", () -> {
                    ids.remove(mediaId);
                    pending.setText("Đã bỏ ảnh #" + mediaId);
                    attachments.removeAll();
                    for (String id : ids) attachments.add(label("Ảnh #" + id, false));
                    attachments.revalidate();
                })));
                attachments.revalidate();
                submit.setEnabled(uploading[0] == 0);
            });
        });
        if (existing != null) image.setVisible(false);

        submit.addActionListener(e -> {
            if (content.getText().isBlank() && ids.isEmpty() && existing == null) {
                warn("Vui lòng nhập nội dung hoặc đính kèm ảnh.");
                return;
            }
            if (content.getText().length() > 5000) {
                warn("Bài viết tối đa 5.000 ký tự.");
                return;
            }
            Map<String, Object> payload = existing == null
                    ? of("content", content.getText(), "visibility", privacy.getSelectedItem(), "mediaIds", List.copyOf(ids))
                    : of("postId", id(editing, "post"), "content", content.getText(), "visibility", privacy.getSelectedItem(), "expectedVersion", num(editing, "version"));
            mutate(existing == null ? "POST_CREATE" : "POST_UPDATE", payload, submit, r -> {
                app.ownPostMutationAt = System.currentTimeMillis();
                d.dispose();
                showFeed();
            });
        });

        JButton review = button("Đối chiếu phiên bản máy chủ", () -> query("POST_GET", of("postId", id(editing, "post")), r -> {
            Map<String, Object> latest = map(r.get("post"));
            if (latest.isEmpty()) latest = r;
            if (bool(latest, "unavailable")) {
                warn("Bài viết không còn khả dụng để chỉnh sửa.");
                return;
            }
            JPanel preview = vertical();
            preview.add(label("Phiên bản máy chủ v" + num(latest, "version") + " · " + visibility(str(latest, "visibility")), false));
            preview.add(scroll(readable(str(latest, "content"))));
            preview.setPreferredSize(new Dimension(420, 200));
            if (Dialogs.confirmContent(d, "Dùng phiên bản máy chủ để thay nội dung đang sửa?", preview, "Dùng phiên bản này")) {
                editing.clear();
                editing.putAll(latest);
                info("Đã cập nhật phiên bản đối chiếu.");
            }
        }));
        review.setVisible(existing != null);

        p.add(Box.createVerticalStrut(10));
        p.add(actionRow(horizontal(image, review), button("Đóng", d::dispose), submit));
        d.add(p);
        d.setVisible(true);
    }

    void showPost(long postId) {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JButton back = ghost("Bảng tin", new AppIcon(AppIcon.Type.FEED, Theme.TEXT_SECONDARY, 15), this::showFeed);
        top.add(centered(titled("Bài viết", back), Theme.COLUMN_WIDTH + 48), BorderLayout.NORTH);
        JPanel postHolder = new JPanel(new BorderLayout());
        postHolder.setOpaque(false);
        postHolder.setBorder(new EmptyBorder(0, 24, 6, 24));
        postHolder.add(muted("Đang tải bài viết…"), BorderLayout.NORTH);
        JLabel commentsHeading = contextHeading("BÌNH LUẬN");
        commentsHeading.setBorder(new EmptyBorder(14, 0, 0, 0));
        JPanel postBlock = new JPanel(new BorderLayout());
        postBlock.setOpaque(false);
        postBlock.add(postHolder, BorderLayout.NORTH);
        JPanel headingWrap = new JPanel(new BorderLayout());
        headingWrap.setOpaque(false);
        headingWrap.setBorder(new EmptyBorder(0, 24, 0, 24));
        headingWrap.add(commentsHeading);
        postBlock.add(headingWrap, BorderLayout.CENTER);
        top.add(centered(postBlock, Theme.COLUMN_WIDTH + 48), BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);

        long[] postOwner = {0};
        Page comments = app.new Page("", "COMMENT_LIST", of("postId", postId), comment -> {
            JPanel c = new JPanel(new BorderLayout(12, 4));
            c.setOpaque(false);
            c.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER), new EmptyBorder(12, 4, 10, 4)));
            String author = str(comment, "authorName", "displayName");
            Theme.Avatar face = new Theme.Avatar(author, 32, false);
            JPanel faceSlot = new JPanel(new BorderLayout());
            faceSlot.setOpaque(false);
            faceSlot.add(face, BorderLayout.NORTH);
            c.add(faceSlot, BorderLayout.WEST);

            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            JLabel who = new JLabel(author);
            who.setFont(Theme.font(Font.BOLD, 13));
            who.setForeground(Theme.TEXT);
            who.putClientProperty("html.disable", Boolean.TRUE);
            JPanel nameRow = horizontal(who, muted(time(comment)));
            nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(nameRow);
            JTextArea content = readable(bool(comment, "tombstone") ? "Nội dung bình luận không khả dụng" : str(comment, "content"));
            content.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (bool(comment, "tombstone")) content.setForeground(Theme.MUTED);
            body.add(content);
            if (!bool(comment, "tombstone")) {
                JPanel actions = horizontal(ghost("Báo cáo", () -> report("COMMENT", id(comment, "comment"))));
                if (num(comment, "authorId") == id(app.currentUser, "user") || postOwner[0] == id(app.currentUser, "user")) {
                    actions.add(danger("Xóa", () -> {
                        if (confirm("Xóa bình luận này?")) {
                            mutate("COMMENT_DELETE", of("commentId", id(comment, "comment")), null, r -> showPost(postId));
                        }
                    }));
                }
                actions.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(actions);
            }
            c.add(body, BorderLayout.CENTER);
            return c;
        });
        comments.emptyIcon = AppIcon.Type.CHAT;
        comments.emptyTitle = "Chưa có bình luận";
        comments.emptyHint = "Hãy là người đầu tiên chia sẻ ý kiến về bài viết này.";

        panel.add(comments.panel, BorderLayout.CENTER);
        JTextArea input = text("", 2);
        JButton send = Theme.primaryButton("Bình luận");
        send.addActionListener(e -> {
            if (!input.getText().isBlank()) {
                mutate("COMMENT_CREATE", of("postId", postId, "content", input.getText()), send, r -> {
                    input.setText("");
                    showPost(postId);
                });
            }
        });
        input.putClientProperty("JTextField.placeholderText", "Viết bình luận…");
        JScrollPane commentFrame = framed(input);
        commentFrame.setPreferredSize(new Dimension(Theme.COLUMN_WIDTH - 130, 64));
        JPanel composerBox = new JPanel(new BorderLayout(10, 0));
        composerBox.setOpaque(false);
        composerBox.setBorder(new EmptyBorder(10, 24, 14, 24));
        composerBox.add(commentFrame, BorderLayout.CENTER);
        JPanel sendSlot = new JPanel(new BorderLayout());
        sendSlot.setOpaque(false);
        sendSlot.add(send, BorderLayout.SOUTH);
        composerBox.add(sendSlot, BorderLayout.EAST);
        JPanel commentBar = new JPanel(new BorderLayout());
        commentBar.setBackground(Theme.APP_BACKGROUND);
        commentBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        commentBar.add(centered(composerBox, Theme.COLUMN_WIDTH + 48));
        panel.add(commentBar, BorderLayout.SOUTH);

        setView("post", panel, () -> showPost(postId));
        long token = app.generation;
        query("POST_GET", of("postId", postId), r -> {
            if (token != app.generation) return;
            Map<String, Object> post = map(r.get("post"));
            if (post.isEmpty()) post = r;
            postOwner[0] = num(post, "authorId");
            postHolder.removeAll();
            postHolder.add(postCard(post), BorderLayout.NORTH);
            commentsHeading.setText("BÌNH LUẬN · " + num(post, "commentCount"));
            postHolder.revalidate();
            postHolder.repaint();
            if (bool(post, "unavailable") || "HIDDEN".equals(str(post, "status"))) {
                input.setEnabled(false);
                send.setEnabled(false);
                panel.remove(comments.panel);
                JPanel gone = new JPanel(new GridBagLayout());
                gone.setOpaque(false);
                gone.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.SHIELD, Theme.MUTED, 28), "Bài viết không khả dụng",
                        "Bình luận và tương tác đã bị tắt cho bài viết này."));
                panel.add(gone, BorderLayout.CENTER);
                panel.revalidate();
            } else {
                comments.load(true);
            }
        });
    }
}
