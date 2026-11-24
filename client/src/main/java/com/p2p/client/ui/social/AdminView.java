package com.p2p.client.ui.social;

import com.p2p.client.ui.AppIcon;
import com.p2p.client.ui.ModernUI;
import com.p2p.client.ui.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import static com.p2p.client.ui.social.Values.*;
import com.p2p.client.ui.social.SocialWindow.Page;

/** Administration: bilingual report queue, audit log (list/detail), and recovery tools. */
final class AdminView extends ViewSupport {
    private final Map<Long, ListRow> reportRows = new LinkedHashMap<>();
    private final Map<Long, ListRow> auditRows = new LinkedHashMap<>();
    private JPanel reportDetail;
    private Page reportsPage;
    private JPanel auditDetail;

    AdminView(SocialWindow app) { super(app); }

    void showAdmin() {
        reportRows.clear();
        auditRows.clear();
        reportDetail = new JPanel(new BorderLayout());
        reportDetail.setOpaque(false);
        auditDetail = new JPanel(new BorderLayout());
        auditDetail.setOpaque(false);
        placeholder(reportDetail, tr("Chọn một báo cáo", "Select a Report"),
                tr("Chi tiết và quyết định xử lý hiện ở đây.", "Report details and moderation actions appear here."));
        placeholder(auditDetail, tr("Chọn một mục nhật ký", "Select an Audit Entry"),
                tr("Nội dung thao tác quản trị hiện ở đây.", "Administrative audit details appear here."));

        app.compactNext = true;
        Page reports = app.new Page("", "ADMIN_REPORT_LIST", of("filter", ""), this::reportRow);
        reportsPage = reports;
        reports.emptyIcon = AppIcon.Type.SHIELD;
        reports.emptyTitle = tr("Không có báo cáo", "No reports");
        reports.emptyHint = tr("Báo cáo vi phạm từ người dùng sẽ xuất hiện ở đây.", "User violation reports will appear here.");
        app.compactNext = true;
        Page audit = app.new Page("", "ADMIN_AUDIT_LIST", of(), this::auditRow);
        audit.emptyIcon = AppIcon.Type.FILE;
        audit.emptyTitle = tr("Chưa có nhật ký", "No audit logs");
        audit.emptyHint = tr("Mọi thao tác quản trị được ghi lại tại đây.", "Every administrative action is recorded here.");

        JTabbedPane tabs = new JTabbedPane();
        styleTabs(tabs);
        tabs.addTab(tr("Hàng đợi báo cáo", "Report Queue"), master(reports, reportDetail));
        tabs.addTab(tr("Nhật ký kiểm toán", "Audit Logs"), master(audit, auditDetail));
        tabs.addTab(tr("Khôi phục & Điều phối", "Recovery & Governance"), columnScroll(recoveryTools(), Theme.COLUMN_WIDTH));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(centered(titled(tr("Quản trị hệ thống", "System Administration")), Theme.COLUMN_WIDTH + 48), BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
        setView("admin", panel, this::showAdmin);
        reports.load(true);
        audit.load(true);
    }

    private JPanel master(Page list, JPanel detail) {
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(true);
        left.setBackground(Theme.SURFACE);
        left.setPreferredSize(new Dimension(340, 100));
        left.setMinimumSize(new Dimension(300, 100));
        left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        left.add(list.panel);
        JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(false);
        shell.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        shell.add(left, BorderLayout.WEST);
        shell.add(detail, BorderLayout.CENTER);
        return shell;
    }

    private void placeholder(JPanel host, String title, String hint) {
        host.removeAll();
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.SHIELD, Theme.MUTED, 28), title, hint));
        host.add(center);
        host.revalidate();
        host.repaint();
    }

    private JComponent twoLineRow(long key, Map<Long, ListRow> registry, String title, String sub, Runnable open) {
        ListRow row = new ListRow(Theme.SURFACE, false, () -> {
            registry.forEach((k, r) -> r.setSelected(k == key));
            open.run();
        });
        registry.put(key, row);
        JLabel t = new JLabel(title);
        t.setFont(Theme.font(Font.BOLD, 13));
        t.setForeground(Theme.TEXT);
        t.putClientProperty("html.disable", Boolean.TRUE);
        JLabel d = new JLabel(sub.isBlank() ? "—" : sub.replace('\n', ' '));
        d.setFont(Theme.font(Font.PLAIN, 12));
        d.setForeground(Theme.MUTED);
        d.putClientProperty("html.disable", Boolean.TRUE);
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        d.setAlignmentX(Component.LEFT_ALIGNMENT);
        texts.add(t);
        texts.add(Box.createVerticalStrut(2));
        texts.add(d);
        JPanel slot = new JPanel(new GridBagLayout());
        slot.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        slot.add(texts, gc);
        row.add(slot, BorderLayout.CENTER);
        row.getAccessibleContext().setAccessibleName(title);
        return row;
    }

    private JComponent reportRow(Map<String, Object> r) {
        long rid = id(r, "report");
        String status = str(r, "status");
        return twoLineRow(rid, reportRows,
                targetText(str(r, "targetType")) + " #" + num(r, "targetId") + " · " + statusText(status),
                str(r, "reason"), () -> reviewReport(rid));
    }

    private JComponent auditRow(Map<String, Object> r) {
        long aid = id(r, "audit");
        return twoLineRow(aid, auditRows, str(r, "action") + " · " + time(r), str(r, "reason", "summary"),
                () -> query("ADMIN_AUDIT_GET", of("auditId", aid), data -> showAuditDetail(aid, data)));
    }

    private void showAuditDetail(long aid, Map<String, Object> data) {
        auditDetail.removeAll();
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(24, 28, 24, 28));
        JLabel h = new JLabel(tr("Nhật ký quản trị #", "Audit Entry #") + aid);
        h.setFont(Theme.font(Font.BOLD, 18));
        h.setForeground(Theme.TEXT);
        p.add(h, BorderLayout.NORTH);
        p.add(recordView(data), BorderLayout.CENTER);
        auditDetail.add(p);
        auditDetail.revalidate();
        auditDetail.repaint();
    }

    private JScrollPane recordView(Map<String, Object> data) {
        JTextArea area = readable(recordText(data));
        area.setOpaque(true);
        area.setBackground(Theme.SURFACE);
        area.setBorder(new EmptyBorder(12, 14, 12, 14));
        area.setFont(Theme.font(Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(new RoundedLineBorder(Theme.BORDER, Theme.RADIUS_MD));
        sp.getViewport().setBackground(Theme.SURFACE);
        return sp;
    }

    void reviewReport(long reportId) {
        query("ADMIN_REPORT_GET", of("reportId", reportId), data -> {
            Map<String, Object> report = map(data.get("report"));
            if (report.isEmpty()) report = data;
            Map<String, Object> r = report;
            if (reportDetail == null) return;
            reportDetail.removeAll();

            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBorder(new EmptyBorder(24, 28, 20, 28));

            JLabel h = new JLabel(targetText(str(r, "targetType")) + " #" + num(r, "targetId"));
            h.setFont(Theme.font(Font.BOLD, 20));
            h.setForeground(Theme.TEXT);
            String status = str(r, "status");
            JLabel chip = ModernUI.chip(statusText(status).toUpperCase(),
                    "OPEN".equals(status) ? ModernUI.Tone.WARNING : "IN_REVIEW".equals(status) ? ModernUI.Tone.ACCENT : ModernUI.Tone.NEUTRAL);
            JPanel titleRow = horizontal(h, chip);
            titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            p.add(titleRow);
            JLabel meta = muted(tr("Báo cáo #", "Report #") + reportId + tr(" · Lý do: ", " · Reason: ") + str(r, "reason"));
            meta.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(meta);
            p.add(Box.createVerticalStrut(14));
            JTextArea summary = readable(tr("Người báo cáo: #", "Reporter: #") + num(r, "reporterId")
                    + tr("\nMô tả: ", "\nDescription: ")
                    + (str(r, "description").isBlank() ? tr("(không có)", "(none)") : str(r, "description")));
            summary.setForeground(Theme.TEXT_SECONDARY);
            summary.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(summary);
            p.add(Box.createVerticalStrut(10));
            JLabel rawTitle = contextHeading(tr("DỮ LIỆU GỐC", "SNAPSHOT DATA"));
            p.add(rawTitle);
            JScrollPane record = recordView(data);
            record.setAlignmentX(Component.LEFT_ALIGNMENT);
            record.setPreferredSize(new Dimension(100, 130));
            record.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
            p.add(record);
            p.add(Box.createVerticalStrut(16));

            JComboBox<String> action = new JComboBox<>(new String[]{"NO_ACTION", "HIDE_POST", "HIDE_COMMENT", "SUSPEND_USER"});
            action.setRenderer(new DefaultListCellRenderer() {
                public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                    String text = switch (String.valueOf(v)) {
                        case "HIDE_POST" -> tr("Ẩn bài viết", "Hide post");
                        case "HIDE_COMMENT" -> tr("Ẩn bình luận", "Hide comment");
                        case "SUSPEND_USER" -> tr("Khóa tài khoản", "Suspend account");
                        default -> tr("Không xử lý (Bỏ qua)", "Dismiss (No action)");
                    };
                    return super.getListCellRendererComponent(l, text, i, sel, focus);
                }
            });
            JTextArea reason = text("", 3);
            reason.putClientProperty("JTextField.placeholderText",
                    tr("Ghi rõ căn cứ của quyết định (bắt buộc)", "Provide justification for this decision (required)"));
            addField(p, tr("Hành động xử lý", "Moderation Action"), action);
            addField(p, tr("Lý do quyết định", "Decision Reason"), framed(reason));
            p.add(Box.createVerticalStrut(14));

            JButton claim = Theme.secondaryButton(tr("Nhận xử lý", "Claim Report"));
            JButton resolve = Theme.primaryButton(tr("Xác nhận quyết định", "Apply Decision"));
            claim.setEnabled("OPEN".equals(status));
            resolve.setEnabled("IN_REVIEW".equals(status) && num(r, "assignedAdminId") == id(app.currentUser, "user"));
            claim.addActionListener(e -> mutate("ADMIN_REPORT_CLAIM", of("reportId", reportId, "expectedVersion", num(r, "version")), claim, updated -> {
                reviewReport(reportId);
                if (reportsPage != null) reportsPage.load(true);
            }));
            resolve.addActionListener(e -> {
                if (reason.getText().isBlank()) {
                    warn(tr("Vui lòng nhập lý do quyết định.", "Please enter a decision reason."));
                    return;
                }
                if (confirm(tr("Áp dụng quyết định quản trị và ghi nhật ký kiểm toán?", "Apply this moderation decision and record it in the audit log?"))) {
                    mutate("ADMIN_REPORT_RESOLVE", of("reportId", reportId, "action", action.getSelectedItem(), "reason", reason.getText(), "expectedVersion", num(r, "version")), resolve, updated -> showAdmin());
                }
            });
            JPanel footer = actionRow(muted(resolve.isEnabled() ? " " : "OPEN".equals(status)
                    ? tr("Nhận xử lý trước khi ra quyết định.", "Claim this report before resolving.")
                    : tr("Báo cáo này không thể xử lý ở trạng thái hiện tại.", "This report cannot be modified in its current state.")), claim, resolve);
            p.add(footer);
            p.add(Box.createVerticalGlue());

            reportDetail.add(new JScrollPane(p, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {{
                setBorder(null);
                setOpaque(false);
                getViewport().setOpaque(false);
            }});
            reportDetail.revalidate();
            reportDetail.repaint();
        });
    }

    void showRecord(String title, Map<String, Object> record) {
        JDialog d = dialog(title, 650, 500);
        d.add(scroll(readable(recordText(record))));
        d.setVisible(true);
    }

    private JPanel recoveryTools() {
        JPanel controls = vertical();
        controls.setBorder(new EmptyBorder(20, 0, 28, 0));

        JTextField user = idField(), conversation = idField(), newOwner = idField(), version = new JTextField("1");
        Theme.styleInput(version);
        JTextArea reason = text("", 3);
        reason.putClientProperty("JTextField.placeholderText",
                tr("Bắt buộc với mọi thao tác bên dưới; được ghi vào nhật ký kiểm toán", "Required for all actions below; recorded in the audit log"));

        controls.add(section(tr("Lý do thao tác", "Audit Justification"),
                tr("Áp dụng cho mọi thao tác khôi phục bên dưới.", "Applied to all recovery operations below.")));
        controls.add(framed(reason));
        controls.add(Box.createVerticalStrut(22));

        controls.add(section(tr("Mở khóa tài khoản", "Unsuspend Account"),
                tr("Khôi phục quyền truy cập của một người dùng đang bị khóa.", "Restore access for a suspended user account.")));
        addField(controls, tr("User ID cần mở khóa", "Target User ID"), user);
        controls.add(Box.createVerticalStrut(6));
        controls.add(horizontal(primary(tr("Mở khóa tài khoản", "Unsuspend User"), () -> {
            try {
                mutate("ADMIN_USER_UNSUSPEND", of("targetUserId", Long.parseLong(user.getText().trim()), "reason", reason.getText()), null,
                        r -> info(tr("Đã mở khóa tài khoản và ghi nhật ký.", "User unsuspended and logged.")));
            } catch (NumberFormatException ex) {
                warn(tr("Nhập User ID dạng số hợp lệ.", "Enter a valid numeric User ID."));
            }
        })));
        controls.add(Box.createVerticalStrut(22));

        controls.add(section(tr("Nhóm bị khóa chủ", "Suspended Group Owner Recovery"),
                tr("Chuyển quyền chủ nhóm hoặc đóng nhóm không còn chủ hoạt động.", "Transfer ownership or close a group whose owner is suspended.")));
        addField(controls, tr("Conversation ID nhóm", "Group Conversation ID"), conversation);
        addField(controls, tr("User ID chủ nhóm mới", "New Owner User ID"), newOwner);
        addField(controls, tr("Phiên bản nhóm hiện tại", "Expected Group Version"), version);
        controls.add(Box.createVerticalStrut(6));
        controls.add(horizontal(
                primary(tr("Chuyển chủ nhóm", "Transfer Ownership"), () -> {
                    try {
                        mutate("ADMIN_GROUP_OWNER_TRANSFER", of("conversationId", Long.parseLong(conversation.getText().trim()), "targetUserId", Long.parseLong(newOwner.getText().trim()), "expectedVersion", Long.parseLong(version.getText().trim()), "reason", reason.getText()), null,
                                r -> info(tr("Đã chuyển chủ nhóm và ghi nhật ký.", "Group ownership transferred and logged.")));
                    } catch (NumberFormatException ex) {
                        warn(tr("Nhập ID và phiên bản dạng số hợp lệ.", "Enter valid numeric IDs and version."));
                    }
                }),
                danger(tr("Đóng nhóm", "Close Group"), () -> {
                    try {
                        if (confirm(tr("Đóng nhóm này bằng quyền quản trị?", "Close this group administratively?"))) {
                            mutate("ADMIN_GROUP_CLOSE", of("conversationId", Long.parseLong(conversation.getText().trim()), "expectedVersion", Long.parseLong(version.getText().trim()), "reason", reason.getText()), null,
                                    r -> info(tr("Đã đóng nhóm và ghi nhật ký.", "Group closed and logged.")));
                        }
                    } catch (NumberFormatException ex) {
                        warn(tr("Nhập ID và phiên bản dạng số hợp lệ.", "Enter valid numeric ID and version."));
                    }
                })));
        return controls;
    }

    private JTextField idField() {
        JTextField f = new JTextField();
        Theme.styleInput(f);
        f.putClientProperty("JTextField.placeholderText", tr("Chỉ nhập số", "Numeric ID"));
        return f;
    }

    private JPanel section(String title, String hint) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(Theme.font(Font.BOLD, 15));
        t.setForeground(Theme.TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel h = muted(hint);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        h.setBorder(new EmptyBorder(2, 0, 10, 0));
        p.add(t);
        p.add(h);
        return p;
    }
}
