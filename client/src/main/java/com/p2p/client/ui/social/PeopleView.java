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

/** Friends, requests and people search as a list/detail screen. */
final class PeopleView extends ViewSupport {
    private enum Mode { FRIENDS, INCOMING, OUTGOING, SEARCH }

    private Mode mode = Mode.FRIENDS;
    private long selectedUid;
    private Page listPage;
    private JPanel listHolder;
    private JPanel detailHost;
    private JPanel segmentBar;
    private final Map<Mode, JButton> segments = new java.util.EnumMap<>(Mode.class);
    private JTextField searchField;
    private final Map<Long, ListRow> rowsByUser = new LinkedHashMap<>();

    PeopleView(SocialWindow app) { super(app); }

    /** Opens the screen on the friends list (navigation entry point). */
    void showPeople() {
        mode = Mode.FRIENDS;
        selectedUid = 0;
        build();
    }

    /** Opens the screen on a given list: FRIENDS, INCOMING, OUTGOING or SEARCH. */
    void openMode(String kind) {
        mode = Mode.valueOf(kind);
        selectedUid = 0;
        build();
    }

    /** Rebuilds after a change while keeping the current list. */
    void refreshPeople() {
        selectedUid = 0;
        build();
    }

    private void build() {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(false);

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(true);
        left.setBackground(Theme.SURFACE);
        int width = app.getWidth() > 0 && app.getWidth() < 1150 ? 280 : 340;
        left.setPreferredSize(new Dimension(width, 100));
        left.setMinimumSize(new Dimension(width, 100));
        left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(18, 16, 10, 16));
        JLabel title = new JLabel("Bạn bè");
        title.setFont(Theme.font(Font.BOLD, 18));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(12));
        segmentBar = new JPanel(new GridLayout(1, 4, 4, 0));
        segmentBar.setOpaque(false);
        segmentBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        segments.clear();
        for (Mode m : Mode.values()) {
            boolean on = m == mode;
            JButton b = Theme.style(new JButton(), on ? Theme.ButtonStyle.SECONDARY : Theme.ButtonStyle.GHOST);
            b.setText(switch (m) {
                case FRIENDS -> "Bạn bè";
                case INCOMING -> "Nhận";
                case OUTGOING -> "Đã gửi";
                case SEARCH -> "Tìm";
            });
            b.setFont(Theme.font(Font.BOLD, 12));
            b.setToolTipText(switch (m) {
                case FRIENDS -> "Danh sách bạn bè";
                case INCOMING -> "Lời mời kết bạn đã nhận";
                case OUTGOING -> "Lời mời kết bạn đã gửi";
                case SEARCH -> "Tìm người dùng";
            });
            b.addActionListener(e -> { mode = m; selectedUid = 0; build(); });
            segments.put(m, b);
            segmentBar.add(b);
        }
        styleSegments();
        segmentBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        top.add(segmentBar);

        searchField = new JTextField();
        searchField.setName("userSearch");
        Theme.styleInput(searchField);
        searchField.putClientProperty("JTextField.placeholderText", "Tên hiển thị hoặc tên đăng nhập");
        searchField.getAccessibleContext().setAccessibleName("Tìm người dùng");
        searchField.setPreferredSize(new Dimension(100, 36));
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (mode == Mode.SEARCH) {
            top.add(Box.createVerticalStrut(10));
            top.add(searchField);
        }
        left.add(top, BorderLayout.NORTH);

        listHolder = new JPanel(new BorderLayout());
        listHolder.setOpaque(false);
        left.add(listHolder, BorderLayout.CENTER);

        detailHost = new JPanel(new BorderLayout());
        detailHost.setOpaque(false);
        showEmptyDetail();

        shell.add(left, BorderLayout.WEST);
        shell.add(detailHost, BorderLayout.CENTER);
        setView("people", shell, this::refreshPeople);
        loadList();
    }

    private void styleSegments() {
        segments.forEach((m, b) -> {
            boolean on = m == mode;
            b.setForeground(on ? Theme.PRIMARY_LIGHT : Theme.TEXT_SECONDARY);
            b.putClientProperty("FlatLaf.style", b.getClientProperty("FlatLaf.style") + "; margin:5,2,5,2");
            if (on) b.setBackground(Theme.PRIMARY_SOFT);
        });
    }

    private void loadList() {
        listHolder.removeAll();
        rowsByUser.clear();
        if (mode == Mode.SEARCH) {
            app.compactNext = true;
            listPage = app.new Page("", "USER_SEARCH", of("query", ""), this::searchRow);
            listPage.status.setText("Nhập ít nhất 2 ký tự để tìm kiếm.");
            listPage.more.setEnabled(false);
            listPage.emptyIcon = AppIcon.Type.SEARCH;
            listPage.emptyTitle = "Không tìm thấy ai";
            listPage.emptyHint = "Thử một tên hoặc tên đăng nhập khác.";
            JPanel hint = new JPanel(new GridBagLayout());
            listHolder.add(listPage.panel);
            searchField.addActionListener(e -> {
                String q = searchField.getText().trim();
                if (q.length() < 2) { warn("Nhập ít nhất 2 ký tự để tìm kiếm."); return; }
                listPage.base.put("query", q);
                listPage.load(true);
                hint.setVisible(false);
            });
            hint.setOpaque(false);
            hint.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.SEARCH, Theme.MUTED, 26), "Tìm người dùng",
                    "Nhập tên rồi nhấn Enter để tìm."));
            listHolder.add(hint, BorderLayout.NORTH);
            hint.setName("searchHint");
            SwingUtilities.invokeLater(() -> searchField.requestFocusInWindow());
        } else {
            String kind = mode.name();
            app.compactNext = true;
            listPage = app.new Page("", "RELATION_LIST", of("kind", kind), r -> relationRow(r, kind));
            listPage.emptyIcon = AppIcon.Type.PEOPLE;
            switch (mode) {
                case FRIENDS -> { listPage.emptyTitle = "Chưa có bạn bè"; listPage.emptyHint = "Dùng tab “Tìm” để kết nối với mọi người."; }
                case INCOMING -> { listPage.emptyTitle = "Không có lời mời mới"; listPage.emptyHint = "Lời mời kết bạn bạn nhận được sẽ hiện ở đây."; }
                default -> { listPage.emptyTitle = "Chưa gửi lời mời nào"; listPage.emptyHint = "Lời mời bạn đã gửi đang chờ phản hồi sẽ hiện ở đây."; }
            }
            listHolder.add(listPage.panel);
            listPage.load(true);
        }
        listHolder.revalidate();
        listHolder.repaint();
    }

    // ---- rows -------------------------------------------------------------------------------------------------

    private JComponent relationRow(Map<String, Object> r, String kind) {
        Map<String, Object> u = relationUser(r);
        long uid = num(u, "userId", "id", "otherUserId", "targetUserId");
        if (uid == 0) {
            long requester = num(r, "requesterId"), addressee = num(r, "addresseeId");
            uid = requester == id(app.currentUser, "user") ? addressee : requester;
        }
        long target = uid;
        JLabel sub = muted("FRIENDS".equals(kind) ? "Đang cập nhật…" : "INCOMING".equals(kind) ? "Muốn kết bạn với bạn" : "Đang chờ phản hồi");
        sub.putClientProperty("presenceUserId", target);
        return personRow(u, target, sub, () -> showDetail(target, u, r));
    }

    private JComponent searchRow(Map<String, Object> user) {
        long uid = id(user, "user");
        boolean online = bool(user, "online");
        JLabel sub = muted("@" + str(user, "username") + (online ? " · Đang online" : ""));
        return personRow(user, uid, sub, () -> showDetail(uid, user, null));
    }

    private JComponent personRow(Map<String, Object> u, long uid, JLabel sub, Runnable onSelect) {
        ListRow row = new ListRow(Theme.SURFACE, uid == selectedUid, () -> { selectedUid = uid; onSelect.run(); refreshSelection(); });
        rowsByUser.put(uid, row);
        Theme.Avatar face = new Theme.Avatar(name(u), 38, false);
        row.ring(face);
        String mid = str(u, "avatarMediaId");
        if (!mid.isBlank()) app.thumbnails.load(mid, 76, icon -> face.setImage(icon.getImage()));
        JPanel faceSlot = new JPanel(new GridBagLayout());
        faceSlot.setOpaque(false);
        faceSlot.add(face);
        JLabel nm = new JLabel(name(u));
        nm.setFont(Theme.font(Font.PLAIN, 13));
        nm.setForeground(Theme.TEXT);
        nm.putClientProperty("html.disable", Boolean.TRUE);
        sub.setFont(Theme.font(Font.PLAIN, 12));
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        nm.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        texts.add(nm);
        texts.add(Box.createVerticalStrut(2));
        texts.add(sub);
        JPanel textSlot = new JPanel(new GridBagLayout());
        textSlot.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        textSlot.add(texts, gc);
        row.add(faceSlot, BorderLayout.WEST);
        row.add(textSlot, BorderLayout.CENTER);
        row.getAccessibleContext().setAccessibleName("Xem " + name(u));
        return row;
    }

    private void refreshSelection() {
        rowsByUser.forEach((uid, row) -> row.setSelected(uid == selectedUid));
    }

    // ---- detail ----------------------------------------------------------------------------------------------

    private void showEmptyDetail() {
        detailHost.removeAll();
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.PEOPLE, Theme.MUTED, 30), "Chọn một người",
                "Xem hồ sơ, nhắn tin hoặc quản lý lời mời ở đây."));
        detailHost.add(center);
        detailHost.revalidate();
        detailHost.repaint();
    }

    private void showDetail(long uid, Map<String, Object> basic, Map<String, Object> relationRecord) {
        detailHost.removeAll();
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(26, 28, 18, 28));
        Theme.Avatar face = new Theme.Avatar(name(basic), 84, false);
        face.setRing(Theme.APP_BACKGROUND);
        String basicMedia = str(basic, "avatarMediaId");
        if (!basicMedia.isBlank()) app.thumbnails.load(basicMedia, 168, icon -> face.setImage(icon.getImage()));
        JPanel faceSlot = new JPanel(new BorderLayout());
        faceSlot.setOpaque(false);
        faceSlot.add(face, BorderLayout.NORTH);
        header.add(faceSlot, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel nm = new JLabel(name(basic));
        nm.setFont(Theme.font(Font.BOLD, 22));
        nm.setForeground(Theme.TEXT);
        nm.putClientProperty("html.disable", Boolean.TRUE);
        nm.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel handle = muted(str(basic, "username").isBlank() ? " " : "@" + str(basic, "username"));
        handle.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextArea bio = readable("");
        bio.setForeground(Theme.TEXT_SECONDARY);
        bio.setAlignmentX(Component.LEFT_ALIGNMENT);
        bio.setVisible(false);
        info.add(Box.createVerticalStrut(4));
        info.add(nm);
        info.add(handle);
        info.add(Box.createVerticalStrut(8));
        info.add(bio);
        info.add(Box.createVerticalStrut(14));
        info.add(actionsFor(uid, basic, relationRecord));
        header.add(info, BorderLayout.CENTER);
        page.add(header, BorderLayout.NORTH);

        app.compactNext = false;
        Page posts = app.new Page("Bài viết", "PROFILE_POSTS_GET", of("authorId", uid), this::postCard);
        posts.emptyIcon = AppIcon.Type.FEED;
        posts.emptyTitle = "Chưa có bài viết";
        posts.emptyHint = "Bài viết bạn được phép xem của người này sẽ hiện ở đây.";
        page.add(posts.panel, BorderLayout.CENTER);

        detailHost.add(page);
        detailHost.revalidate();
        detailHost.repaint();
        posts.load(true);

        query("PROFILE_GET", of("targetUserId", uid), r -> {
            Map<String, Object> u = map(r.get("profile"));
            if (u.isEmpty()) u = r;
            if (selectedUid != uid) return;
            nm.setText(name(u));
            handle.setText("@" + str(u, "username"));
            String about = str(u, "bio");
            if (!about.isBlank()) {
                bio.setText(about);
                bio.setRows(Math.min(6, about.length() / 60 + 1));
                bio.setVisible(true);
            }
            String mid = str(u, "avatarMediaId");
            if (!mid.isBlank()) app.thumbnails.load(mid, 168, icon -> face.setImage(icon.getImage()));
            header.revalidate();
            header.repaint();
        });
    }

    private JPanel actionsFor(long uid, Map<String, Object> basic, Map<String, Object> r) {
        JPanel actions = horizontal();
        if (uid == id(app.currentUser, "user")) {
            actions.add(primary("Chỉnh sửa hồ sơ", this::showSettings));
            return actions;
        }
        switch (mode) {
            case FRIENDS -> {
                long relation = r == null ? 0 : id(r, "relation");
                actions.add(primary("Nhắn tin", () -> openDirect(uid)));
                actions.add(button("Hủy kết bạn", () -> {
                    if (confirm("Hủy kết bạn với người này? Bạn sẽ không thể gửi tin nhắn mới cho đến khi kết bạn lại.")) {
                        mutate("FRIEND_REMOVE", of("relationId", relation), null, x -> refreshPeople());
                    }
                }));
            }
            case INCOMING -> {
                long relation = id(r, "relation");
                long version = num(r, "version");
                actions.add(primary("Chấp nhận", () -> mutate("FRIEND_ACCEPT", of("relationId", relation, "expectedVersion", version), null, x -> refreshPeople())));
                actions.add(button("Từ chối", () -> mutate("FRIEND_REJECT", of("relationId", relation, "expectedVersion", version), null, x -> refreshPeople())));
            }
            case OUTGOING -> {
                long relation = id(r, "relation");
                long version = num(r, "version");
                actions.add(button("Hủy lời mời", () -> mutate("FRIEND_CANCEL", of("relationId", relation, "expectedVersion", version), null, x -> refreshPeople())));
            }
            case SEARCH -> {
                String relation = str(basic, "relationshipStatus");
                if (relation.isBlank()) {
                    actions.add(primary("Gửi lời mời kết bạn", () -> mutate("FRIEND_REQUEST", of("targetUserId", uid), null, x -> info("Đã gửi lời mời kết bạn thành công!"))));
                } else {
                    JButton state = button("ACCEPTED".equals(relation) ? "Đã là bạn bè" : "Đang chờ xử lý", () -> { });
                    state.setEnabled(false);
                    actions.add(state);
                }
                actions.add(button("Nhắn tin", () -> openDirect(uid)));
            }
        }
        actions.add(ghost("Báo cáo", () -> report("USER", uid)));
        actions.add(danger("Chặn", () -> block(uid, true)));
        return actions;
    }

    // ---- shared helpers (also used by other screens) -------------------------------------------------------

    Map<String, Object> relationUser(Map<String, Object> r) {
        Map<String, Object> u = map(r.get("user"));
        if (u.isEmpty()) u = map(r.get("otherUser"));
        if (u.isEmpty()) u = map(r.get("targetUser"));
        if (u.isEmpty()) u = r;
        return u;
    }

    void updatePresence(Container parent, long userId, boolean online, long asOf) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JLabel label && Objects.equals(label.getClientProperty("presenceUserId"), userId)) {
                label.setText(online ? "Đang online" : "Offline");
                label.setForeground(online ? Theme.ONLINE : Theme.MUTED);
                label.setIcon(new AppIcon(AppIcon.Type.DOT, online ? Theme.ONLINE : Theme.MUTED, 10));
                label.setIconTextGap(6);
                label.setToolTipText("Trạng thái TCP · " + Values.time(of("createdAt", asOf)));
            } else if (c instanceof Container child) {
                updatePresence(child, userId, online, asOf);
            }
        }
    }

    void block(long uid, boolean blocked) {
        if (blocked && !confirm("Chặn người dùng này? Hai bạn vẫn có thể nhìn thấy tin nhắn trong nhóm chung.")) return;
        mutate("BLOCK_SET", of("targetUserId", uid, "blocked", blocked), null, r -> refreshPeople());
    }
}
