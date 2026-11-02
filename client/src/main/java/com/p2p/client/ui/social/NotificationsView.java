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

/** NotificationsView screen. Extracted from SocialWindow; behaviour is unchanged. */
final class NotificationsView extends ViewSupport {
    NotificationsView(SocialWindow app) { super(app); }

    // ================= NOTIFICATIONS =================
    void showNotifications() {
        Set<Long> unreadIds = new LinkedHashSet<>();
        JButton readAll = Theme.secondaryButton("Đánh dấu tất cả đã đọc");
        readAll.setIcon(new AppIcon(AppIcon.Type.CHECK, Theme.TEXT, 14));
        readAll.setIconTextGap(8);
        readAll.addActionListener(e -> {
            if (unreadIds.isEmpty()) { warn("Không có thông báo chưa đọc trong danh sách đang tải."); return; }
            List<Long> batch = new ArrayList<>(unreadIds).subList(0, Math.min(50, unreadIds.size()));
            mutate("NOTIFICATION_READ", of("notificationIds", new ArrayList<>(batch)), readAll, r -> {
                refreshCount();
                showNotifications();
            });
        });
        Page page = app.new Page("Thông báo của bạn", "NOTIFICATION_LIST", of("unreadOnly", false), n -> {
            JPanel c = card();
            long nid = id(n, "notification");
            boolean available = !Boolean.FALSE.equals(n.get("available"));
            boolean read = bool(n, "read") || bool(n, "isRead") || num(n, "isRead") > 0;
            if (!read) {
                c.setBackground(Theme.PRIMARY_SOFT); // Highlight unread
                if (nid > 0) unreadIds.add(nid);
            }

            JLabel headline = new JLabel(notificationTitle(str(n, "kind", "title", "type")));
            headline.setFont(Theme.font(read ? Font.PLAIN : Font.BOLD, 14));
            headline.setForeground(read ? Theme.TEXT_SECONDARY : Theme.TEXT);
            headline.setIcon(new AppIcon(AppIcon.Type.DOT, read ? Theme.BORDER_STRONG : Theme.PRIMARY, 10));
            headline.setIconTextGap(10);
            headline.putClientProperty("html.disable", Boolean.TRUE);
            JPanel titleBlock = new JPanel();
            titleBlock.setOpaque(false);
            titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
            headline.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel when = muted(time(n) + (read ? " · Đã đọc" : ""));
            when.setBorder(new EmptyBorder(2, 20, 0, 0));
            when.setAlignmentX(Component.LEFT_ALIGNMENT);
            titleBlock.add(headline);
            titleBlock.add(when);
            c.add(titleBlock, BorderLayout.WEST);
            String body = available ? str(n, "content", "body", "preview") : "Nội dung không còn khả dụng.";
            if (!body.isBlank()) {
                JTextArea bodyText = readable(body);
                bodyText.setBorder(new EmptyBorder(0, 20, 0, 0));
                c.add(bodyText, BorderLayout.CENTER);
            }

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            actions.setOpaque(false);
            if (!read) {
                actions.add(ghost("Đánh dấu đã đọc", new AppIcon(AppIcon.Type.CHECK, Theme.TEXT_SECONDARY, 14), () -> mutate("NOTIFICATION_READ", of("notificationIds", List.of(nid)), null, r -> {
                    refreshCount();
                    showNotifications();
                })));
            }
            if (available) {
                actions.add(button("Mở nội dung", () -> {
                    mutate("NOTIFICATION_READ", of("notificationIds", List.of(nid)), null, r -> refreshCount());
                    Map<String, Object> target = map(n.get("target"));
                    String type = str(n, "targetType");
                    if (type.isBlank()) type = str(target, "type", "targetType");
                    long id = num(n, "targetId");
                    if (id == 0) id = num(target, "id", "targetId");
                    switch (type) {
                        case "POST", "COMMENT" -> showPost(num(n, "postId") > 0 ? num(n, "postId") : id);
                        case "USER", "PROFILE" -> showProfile(id);
                        case "CONVERSATION", "MESSAGE", "GROUP" -> {
                            long cid = num(n, "conversationId");
                            if (cid == 0) cid = id;
                            openChat(of("conversationId", cid, "title", "Cuộc trò chuyện #" + cid));
                        }
                        case "RELATION", "FRIEND_REQUEST" -> showPeople();
                        default -> showConversations();
                    }
                    if (type.isBlank()) {
                        if (num(n, "postId") > 0) showPost(num(n, "postId"));
                        else if (num(n, "relationId") > 0) showPeople();
                        else if (num(n, "conversationId") > 0) openChat(of("conversationId", num(n, "conversationId"), "title", "Cuộc trò chuyện #" + num(n, "conversationId")));
                        else showConversations();
                    }
                }));
            }
            c.add(actions, BorderLayout.EAST);
            return c;
        }, readAll);
        page.emptyIcon = AppIcon.Type.NOTIFICATION;
        page.emptyTitle = "Chưa có thông báo";
        page.emptyHint = "Lượt thích, bình luận và lời mời mới sẽ hiện ở đây.";

        setView("notifications", page.panel, this::showNotifications);
        page.load(true);
        refreshCount();
    }

    String notificationTitle(String type) {
        return switch (type) {
            case "FRIEND_REQUEST" -> "Bạn có lời mời kết bạn mới";
            case "FRIEND_ACCEPT" -> "Lời mời kết bạn được chấp nhận";
            case "LIKE" -> "Có người đã thích bài viết của bạn";
            case "COMMENT" -> "Có bình luận mới trên bài viết";
            case "MESSAGE" -> "Bạn nhận được tin nhắn mới";
            case "GROUP_INVITE" -> "Bạn được mời tham gia nhóm";
            default -> type;
        };
    }
}
