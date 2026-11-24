package com.p2p.client.ui.social;

import com.p2p.client.ui.AppIcon;
import com.p2p.client.ui.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import static com.p2p.client.ui.social.Values.*;
import com.p2p.client.ui.social.SocialWindow.Page;

/** NotificationsView screen: Rich actor attribution, accurate deep-linking, and NOTIFICATION_READ_THROUGH bulk read. */
final class NotificationsView extends ViewSupport {
    NotificationsView(SocialWindow app) { super(app); }

    void showNotifications() {
        Set<Long> unreadIds = new LinkedHashSet<>();
        Page[] pageRef = new Page[1];

        JButton readAll = Theme.secondaryButton(tr("Đánh dấu tất cả đã đọc", "Mark all as read"));
        readAll.setIcon(new AppIcon(AppIcon.Type.CHECK, Theme.TEXT, 14));
        readAll.setIconTextGap(8);
        readAll.addActionListener(e -> {
            Page pg = pageRef[0];
            if (pg != null && pg.throughCursor != null && !pg.throughCursor.isBlank()) {
                mutate("NOTIFICATION_READ_THROUGH", of("throughCursor", pg.throughCursor), readAll, r -> {
                    refreshCount();
                    showNotifications();
                });
                return;
            }
            if (unreadIds.isEmpty()) {
                warn(tr("Không có thông báo chưa đọc trong danh sách.", "No unread notifications."));
                return;
            }
            List<Long> batch = new ArrayList<>(unreadIds).subList(0, Math.min(50, unreadIds.size()));
            mutate("NOTIFICATION_READ", of("notificationIds", new ArrayList<>(batch)), readAll, r -> {
                refreshCount();
                showNotifications();
            });
        });

        Page page = app.new Page(tr("Thông báo của bạn", "Your Notifications"), "NOTIFICATION_LIST", of("unreadOnly", false), n -> {
            JPanel c = card();
            long nid = id(n, "notification");
            boolean available = !Boolean.FALSE.equals(n.get("available"));
            boolean read = bool(n, "read") || bool(n, "isRead") || num(n, "isRead") > 0;
            if (!read) {
                c.setBackground(Theme.PRIMARY_SOFT);
                if (nid > 0) unreadIds.add(nid);
            }

            String nType = str(n, "kind", "title", "type");
            JLabel headline = new JLabel(notificationTitle(nType));
            headline.setFont(Theme.font(read ? Font.PLAIN : Font.BOLD, 14));
            headline.setForeground(read ? Theme.TEXT_SECONDARY : Theme.TEXT);
            headline.setIcon(new AppIcon(AppIcon.Type.DOT, read ? Theme.BORDER_STRONG : Theme.PRIMARY, 10));
            headline.setIconTextGap(10);
            headline.putClientProperty("html.disable", Boolean.TRUE);
            JPanel titleBlock = new JPanel();
            titleBlock.setOpaque(false);
            titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
            headline.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel when = muted(time(n) + (read ? tr(" · Đã đọc", " · Read") : ""));
            when.setBorder(new EmptyBorder(2, 20, 0, 0));
            when.setAlignmentX(Component.LEFT_ALIGNMENT);
            titleBlock.add(headline);
            titleBlock.add(when);

            String detail = buildNotificationDetail(n, nType, available);
            if (!detail.isBlank()) {
                JTextArea bodyText = readable(detail);
                bodyText.setForeground(available ? Theme.TEXT_SECONDARY : Theme.MUTED);
                bodyText.setBorder(new EmptyBorder(4, 20, 0, 0));
                bodyText.setAlignmentX(Component.LEFT_ALIGNMENT);
                titleBlock.add(bodyText);
            }
            c.add(titleBlock, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            actions.setOpaque(false);
            if (!read) {
                actions.add(ghost(tr("Đánh dấu đã đọc", "Mark read"), new AppIcon(AppIcon.Type.CHECK, Theme.TEXT_SECONDARY, 14),
                        () -> mutate("NOTIFICATION_READ", of("notificationIds", List.of(nid)), null, r -> {
                            refreshCount();
                            showNotifications();
                        })));
            }
            if (available && canOpenTarget(n, nType)) {
                actions.add(button(tr("Mở nội dung", "Open"), () -> {
                    if (!read && nid > 0) {
                        mutate("NOTIFICATION_READ", of("notificationIds", List.of(nid)), null, r -> refreshCount());
                    }
                    navigateNotification(n, nType);
                }));
            }
            c.add(actions, BorderLayout.EAST);
            return c;
        }, readAll);
        pageRef[0] = page;
        page.emptyIcon = AppIcon.Type.NOTIFICATION;
        page.emptyTitle = tr("Chưa có thông báo", "No notifications yet");
        page.emptyHint = tr("Lượt thích, bình luận, tin nhắn và lời mời mới sẽ hiện ở đây.",
                "Likes, comments, new messages, and invitations will appear here.");

        setView("notifications", page.panel, this::showNotifications);
        page.load(true);
        refreshCount();
    }

    private boolean canOpenTarget(Map<String, Object> n, String nType) {
        return switch (nType) {
            case "LIKE", "COMMENT" -> num(n, "postId") > 0;
            case "FRIEND_REQUEST", "FRIEND_ACCEPT" -> true;
            case "GROUP_INVITE" -> true;
            case "MESSAGE" -> num(n, "conversationId") > 0;
            case "MODERATION" -> num(n, "postId") > 0;
            default -> num(n, "postId") > 0 || num(n, "conversationId") > 0 || num(n, "relationId") > 0;
        };
    }

    private void navigateNotification(Map<String, Object> n, String nType) {
        long postId = num(n, "postId");
        long convId = num(n, "conversationId");
        long actorId = num(n, "actorId");

        switch (nType) {
            case "LIKE", "COMMENT" -> {
                if (postId > 0) showPost(postId);
            }
            case "FRIEND_REQUEST" -> app.peopleView.openMode("INCOMING");
            case "FRIEND_ACCEPT" -> {
                if (actorId > 0) showProfile(actorId);
                else showPeople();
            }
            case "GROUP_INVITE" -> {
                showConversations();
                showGroupInvites();
            }
            case "MESSAGE" -> {
                if (convId > 0) {
                    // Load full conversation metadata first so group vs direct state is preserved accurately
                    query("CONVERSATION_LIST", of("limit", 50), r -> {
                        Map<String, Object> found = rows(r.get("items")).stream()
                                .filter(cv -> id(cv, "conversation") == convId)
                                .findFirst().orElse(null);
                        if (found != null) {
                            openChat(found);
                        } else {
                            showConversations();
                        }
                    });
                } else {
                    showConversations();
                }
            }
            case "MODERATION" -> {
                if (postId > 0) showPost(postId);
                else showFeed();
            }
            default -> {
                if (postId > 0) showPost(postId);
                else if (convId > 0) {
                    query("CONVERSATION_LIST", of("limit", 50), r -> {
                        Map<String, Object> found = rows(r.get("items")).stream()
                                .filter(cv -> id(cv, "conversation") == convId)
                                .findFirst().orElse(null);
                        if (found != null) openChat(found);
                        else showConversations();
                    });
                } else if (num(n, "groupInviteId") > 0) {
                    showConversations();
                    showGroupInvites();
                } else if (num(n, "relationId") > 0) {
                    showPeople();
                }
            }
        }
    }

    private String buildNotificationDetail(Map<String, Object> n, String nType, boolean available) {
        if (!available) {
            return tr("Nội dung không còn khả dụng hoặc đã thay đổi quyền xem.", "Content is no longer available.");
        }
        String explicit = str(n, "content", "body", "preview");
        if (!explicit.isBlank()) return explicit;

        String actor = str(n, "actorName");
        String handle = str(n, "actorUsername");
        String who = actor.isBlank() ? "" : (handle.isBlank() ? actor : actor + " (@" + handle + ")");

        return switch (nType) {
            case "FRIEND_REQUEST" -> who.isBlank() ? "" : tr(who + " đã gửi lời mời kết bạn cho bạn.", who + " sent you a friend request.");
            case "FRIEND_ACCEPT" -> who.isBlank() ? "" : tr(who + " đã đồng ý kết bạn với bạn.", who + " accepted your friend request.");
            case "LIKE" -> who.isBlank() ? "" : tr(who + " đã thả tim bài viết của bạn.", who + " liked your post.");
            case "COMMENT" -> who.isBlank() ? "" : tr(who + " đã bình luận trên bài viết của bạn.", who + " commented on your post.");
            case "MESSAGE" -> {
                String convTitle = str(n, "conversationTitle");
                if (!who.isBlank() && !convTitle.isBlank()) {
                    yield tr("Từ " + actor + " · " + convTitle, "From " + actor + " · " + convTitle);
                } else if (!who.isBlank()) {
                    yield tr("Tin nhắn mới từ " + who, "New message from " + who);
                }
                yield "";
            }
            case "GROUP_INVITE" -> {
                String gTitle = str(n, "groupTitle");
                if (!who.isBlank() && !gTitle.isBlank()) {
                    yield tr(actor + " mời bạn vào nhóm “" + gTitle + "”.", actor + " invited you to group “" + gTitle + "”.");
                } else if (!gTitle.isBlank()) {
                    yield tr("Nhóm: " + gTitle, "Group: " + gTitle);
                }
                yield "";
            }
            case "MODERATION" -> {
                String modReason = str(n, "moderationReason");
                String res = str(n, "resolution");
                yield (res.isBlank() ? "" : res + " · ") + (modReason.isBlank()
                        ? tr("Nội dung hoặc tài khoản của bạn đã được ban quản trị cập nhật trạng thái.", "Your content or account status was updated by an administrator.")
                        : tr("Lý do: ", "Reason: ") + modReason);
            }
            case "REPORT_RESOLVED" -> {
                String res = str(n, "resolution");
                yield tr("Báo cáo bạn gửi đã được xử lý", "Your report has been reviewed") + (res.isBlank() ? "." : " (" + res + ").");
            }
            default -> "";
        };
    }

    String notificationTitle(String type) {
        return switch (type) {
            case "FRIEND_REQUEST" -> tr("Bạn có lời mời kết bạn mới", "New friend request");
            case "FRIEND_ACCEPT" -> tr("Lời mời kết bạn được chấp nhận", "Friend request accepted");
            case "LIKE" -> tr("Có người đã thích bài viết của bạn", "Someone liked your post");
            case "COMMENT" -> tr("Có bình luận mới trên bài viết", "New comment on your post");
            case "MESSAGE" -> tr("Bạn nhận được tin nhắn mới", "New message received");
            case "GROUP_INVITE" -> tr("Bạn được mời tham gia nhóm", "New group invitation");
            case "MODERATION" -> tr("Thông báo kiểm duyệt hệ thống", "Moderation notice");
            case "REPORT_RESOLVED" -> tr("Kết quả xử lý báo cáo vi phạm", "Report resolution update");
            default -> type;
        };
    }
}
