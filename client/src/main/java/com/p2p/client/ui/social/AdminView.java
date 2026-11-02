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

/** Administration: report queue and audit log (list/detail) plus recovery tools. */
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
        placeholder(reportDetail, "Chọn một báo cáo", "Chi tiết và quyết định xử lý hiện ở đây.");
        placeholder(auditDetail, "Chọn một mục nhật ký", "Nội dung thao tác quản trị hiện ở đây.");

        app.compactNext = true;
        Page reports = app.new Page("", "ADMIN_REPORT_LIST", of("filter", ""), r -> reportRow(r));
        reportsPage = reports;
        reports.emptyIcon = AppIcon.Type.SHIELD;
        reports.emptyTitle = "Không có báo cáo";
        reports.emptyHint = "Báo cáo vi phạm từ người dùng sẽ xuất hiện ở đây.";
        app.compactNext = true;
        Page audit = app.new Page("", "ADMIN_AUDIT_LIST", of(), r -> auditRow(r));
        audit.emptyIcon = AppIcon.Type.FILE;
        audit.emptyTitle = "Chưa có nhật ký";
        audit.emptyHint = "Mọi thao tác quản trị được ghi lại tại đây.";

        JTabbedPane tabs = new JTabbedPane();
        styleTabs(tabs);
        tabs.addTab("Hàng đợi báo cáo", master(reports, reportDetail));
        tabs.addTab("Nhật ký kiểm toán", master(audit, auditDetail));
        tabs.addTab("Khôi phục & Điều phối", columnScroll(recoveryTools(), Theme.COLUMN_WIDTH));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(centered(titled("Quản trị hệ thống"), Theme.COLUMN_WIDTH + 48), BorderLayout.NORTH);
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
        JComponent row = twoLineRow(rid, reportRows,
                targetText(str(r, "targetType")) + " #" + num(r, "targetId") + " · " + statusText(status),
                str(r, "reason"), () -> reviewReport(rid));
        return row;
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
        JLabel h = new JLabel("Nhật ký quản trị #" + aid);
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

    /** Report detail and decision form, shown inline next to the queue. */
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
            JLabel meta = muted("Báo cáo #" + reportId + " · Lý do: " + str(r, "reason"));
            meta.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(meta);
            p.add(Box.createVerticalStrut(14));
            JTextArea summary = readable("Người báo cáo: #" + num(r, "reporterId") + "\nMô tả: "
                    + (str(r, "description").isBlank() ? "(không có)" : str(r, "description")));
            summary.setForeground(Theme.TEXT_SECONDARY);
            summary.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(summary);
            p.add(Box.createVerticalStrut(10));
            JLabel rawTitle = contextHeading("DỮ LIỆU GỐC");
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
                        case "HIDE_POST" -> "Ẩn bài viết";
                        case "HIDE_COMMENT" -> "Ẩn bình luận";
                        case "SUSPEND_USER" -> "Khóa tài khoản";
                        default -> "Không xử lý";
                    };
                    return super.getListCellRendererComponent(l, text, i, sel, focus);
                }
            });
            JTextArea reason = text("", 3);
            reason.putClientProperty("JTextField.placeholderText", "Ghi rõ căn cứ của quyết định (bắt buộc)");
            addField(p, "Hành động xử lý", action);
            addField(p, "Lý do quyết định", framed(reason));
            p.add(Box.createVerticalStrut(14));

            JButton claim = Theme.secondaryButton("Nhận xử lý");
            JButton resolve = Theme.primaryButton("Xác nhận quyết định");
            claim.setEnabled("OPEN".equals(status));
            resolve.setEnabled("IN_REVIEW".equals(status) && num(r, "assignedAdminId") == id(app.currentUser, "user"));
            claim.addActionListener(e -> mutate("ADMIN_REPORT_CLAIM", of("reportId", reportId, "expectedVersion", num(r, "version")), claim, updated -> {
                reviewReport(reportId);
                if (reportsPage != null) reportsPage.load(true);
            }));
            resolve.addActionListener(e -> {
                if (reason.getText().isBlank()) {
                    warn("Vui lòng nhập lý do quyết định.");
                    return;
                }
                if (confirm("Áp dụng quyết định quản trị và ghi nhật ký kiểm toán?")) {
                    mutate("ADMIN_REPORT_RESOLVE", of("reportId", reportId, "action", action.getSelectedItem(), "reason", reason.getText(), "expectedVersion", num(r, "version")), resolve, updated -> showAdmin());
                }
            });
            JPanel footer = actionRow(muted(resolve.isEnabled() ? " " : "OPEN".equals(status) ? "Nhận xử lý trước khi ra quyết định." : "Báo cáo này không thể xử lý ở trạng thái hiện tại."), claim, resolve);
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

    // ---- recovery tools ------------------------------------------------------------------------------------

    private JPanel recoveryTools() {
        JPanel controls = vertical();
        controls.setBorder(new EmptyBorder(20, 0, 28, 0));

        JTextField user = idField(), conversation = idField(), newOwner = idField(), version = new JTextField("1");
        Theme.styleInput(version);
        JTextArea reason = text("", 3);
        reason.putClientProperty("JTextField.placeholderText", "Bắt buộc với mọi thao tác bên dưới; được ghi vào nhật ký kiểm toán");

        controls.add(section("Lý do thao tác", "Áp dụng cho mọi thao tác khôi phục bên dưới."));
        controls.add(framed(reason));
        controls.add(Box.createVerticalStrut(22));

        controls.add(section("Mở khóa tài khoản", "Khôi phục quyền truy cập của một người dùng đang bị khóa."));
        addField(controls, "User ID cần mở khóa", user);
        controls.add(Box.createVerticalStrut(6));
        controls.add(horizontal(primary("Mở khóa tài khoản", () -> {
            try {
                mutate("ADMIN_USER_UNSUSPEND", of("targetUserId", Long.parseLong(user.getText().trim()), "reason", reason.getText()), null, r -> info("Đã mở khóa tài khoản và ghi nhật ký."));
            } catch (NumberFormatException ex) {
                warn("Nhập User ID dạng số hợp lệ.");
            }
        })));
        controls.add(Box.createVerticalStrut(22));

        controls.add(section("Nhóm bị khóa chủ", "Chuyển quyền chủ nhóm hoặc đóng nhóm không còn chủ hoạt động."));
        addField(controls, "Conversation ID nhóm", conversation);
        addField(controls, "User ID chủ nhóm mới", newOwner);
        addField(controls, "Phiên bản nhóm hiện tại", version);
        controls.add(Box.createVerticalStrut(6));
        controls.add(horizontal(
                primary("Chuyển chủ nhóm", () -> {
                    try {
                        mutate("ADMIN_GROUP_OWNER_TRANSFER", of("conversationId", Long.parseLong(conversation.getText().trim()), "targetUserId", Long.parseLong(newOwner.getText().trim()), "expectedVersion", Long.parseLong(version.getText().trim()), "reason", reason.getText()), null, r -> info("Đã chuyển chủ nhóm và ghi nhật ký."));
                    } catch (NumberFormatException ex) {
                        warn("Nhập ID và phiên bản dạng số hợp lệ.");
                    }
                }),
                danger("Đóng nhóm", () -> {
                    try {
                        if (confirm("Đóng nhóm này bằng quyền quản trị?")) {
                            mutate("ADMIN_GROUP_CLOSE", of("conversationId", Long.parseLong(conversation.getText().trim()), "expectedVersion", Long.parseLong(version.getText().trim()), "reason", reason.getText()), null, r -> info("Đã đóng nhóm và ghi nhật ký."));
                        }
                    } catch (NumberFormatException ex) {
                        warn("Nhập ID và phiên bản dạng số hợp lệ.");
                    }
                })));
        return controls;
    }

    private JTextField idField() {
        JTextField f = new JTextField();
        Theme.styleInput(f);
        f.putClientProperty("JTextField.placeholderText", "Chỉ nhập số");
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
