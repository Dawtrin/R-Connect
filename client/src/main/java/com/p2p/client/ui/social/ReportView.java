package com.p2p.client.ui.social;

import com.p2p.client.ui.Theme;
import javax.swing.*;
import java.awt.*;
import static com.p2p.client.ui.social.Values.*;

/** ReportView screen: Complete 6-reason content & user reporting modal with bilingual UI. */
final class ReportView extends ViewSupport {
    ReportView(SocialWindow app) { super(app); }

    void report(String type, long target) {
        JDialog d = dialog(tr("Báo cáo vi phạm nội dung", "Report Content / User"), 500, 380);
        JPanel p = vertical();
        JComboBox<String> reason = new JComboBox<>(new String[]{
                "SPAM", "HARASSMENT", "HATE_SPEECH", "INAPPROPRIATE", "IMPERSONATION", "OTHER"
        });
        reason.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean selected, boolean focus) {
                String text = switch (String.valueOf(v)) {
                    case "SPAM" -> tr("Spam hoặc quảng cáo", "Spam or advertising");
                    case "HARASSMENT" -> tr("Quấy rối hoặc xúc phạm", "Harassment or bullying");
                    case "HATE_SPEECH" -> tr("Ngôn từ gây thù ghét", "Hate speech");
                    case "INAPPROPRIATE" -> tr("Nội dung không phù hợp", "Inappropriate content");
                    case "IMPERSONATION" -> tr("Giả mạo người khác", "Impersonation");
                    default -> tr("Lý do khác", "Other reason");
                };
                return super.getListCellRendererComponent(l, text, i, selected, focus);
            }
        });
        JTextArea description = text("", 5);
        description.putClientProperty("JTextField.placeholderText",
                tr("Mô tả ngắn gọn điều gì không ổn (không bắt buộc)", "Briefly describe the issue (optional)"));
        JLabel reportTitle = label(tr("Báo cáo ", "Report ") + targetText(type).toLowerCase() + " #" + target, true);
        reportTitle.setFont(Theme.font(Font.BOLD, 15));
        p.add(reportTitle);
        p.add(Box.createVerticalStrut(10));
        addField(p, tr("Lý do báo cáo", "Reason"), reason);
        addField(p, tr("Mô tả chi tiết", "Additional Details"), framed(description));
        p.add(Box.createVerticalStrut(12));

        JButton submit = Theme.primaryButton(tr("Gửi báo cáo", "Submit Report"));
        submit.addActionListener(e -> {
            if (description.getText().length() > 1000) {
                warn(tr("Mô tả báo cáo tối đa 1.000 ký tự.", "Description cannot exceed 1,000 characters."));
                return;
            }
            mutate("REPORT_CREATE", of("targetType", type, "targetId", target, "reason", reason.getSelectedItem(), "description", description.getText()), submit, r -> {
                d.dispose();
                info(tr("Đã ghi nhận báo cáo. Ban quản trị sẽ sớm xem xét.", "Report submitted. Our moderation team will review it shortly."));
            });
        });
        p.add(Box.createVerticalStrut(10));
        p.add(actionRow(null, button(tr("Đóng", "Cancel"), d::dispose), submit));
        d.getRootPane().setDefaultButton(submit);
        d.add(p);
        d.setVisible(true);
    }
}
