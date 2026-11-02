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

/** ReportView screen. Extracted from SocialWindow; behaviour is unchanged. */
final class ReportView extends ViewSupport {
    ReportView(SocialWindow app) { super(app); }

    void report(String type, long target) {
        JDialog d = dialog("Báo cáo vi phạm nội dung", 500, 370);
        JPanel p = vertical();
        JComboBox<String> reason = new JComboBox<>(new String[]{"SPAM", "HARASSMENT", "INAPPROPRIATE", "OTHER"});
        reason.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean selected, boolean focus) {
                String text = switch (String.valueOf(v)) {
                    case "SPAM" -> "Spam hoặc quảng cáo";
                    case "HARASSMENT" -> "Quấy rối hoặc xúc phạm";
                    case "INAPPROPRIATE" -> "Nội dung không phù hợp";
                    default -> "Lý do khác";
                };
                return super.getListCellRendererComponent(l, text, i, selected, focus);
            }
        });
        JTextArea description = text("", 5);
        description.putClientProperty("JTextField.placeholderText", "Mô tả ngắn gọn điều gì không ổn (không bắt buộc)");
        JLabel reportTitle = label("Báo cáo " + targetText(type).toLowerCase() + " #" + target, true);
        reportTitle.setFont(Theme.font(Font.BOLD, 15));
        p.add(reportTitle);
        p.add(Box.createVerticalStrut(10));
        addField(p, "Lý do báo cáo", reason);
        addField(p, "Mô tả chi tiết", framed(description));
        p.add(Box.createVerticalStrut(12));

        JButton submit = Theme.primaryButton("Gửi báo cáo");
        submit.addActionListener(e -> mutate("REPORT_CREATE", of("targetType", type, "targetId", target, "reason", reason.getSelectedItem(), "description", description.getText()), submit, r -> {
            d.dispose();
            info("Đã ghi nhận báo cáo. Ban quản trị sẽ sớm xem xét.");
        }));
        p.add(Box.createVerticalStrut(10));
        p.add(actionRow(null, button("Đóng", d::dispose), submit));
        d.getRootPane().setDefaultButton(submit);
        d.add(p);
        d.setVisible(true);
    }
}
