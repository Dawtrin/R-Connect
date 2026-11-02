package com.p2p.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.Map;

/** R-Connect brand lockups: Connection Monogram + R-CONNECT wordmark. */
public final class Brand {
    public static final String WORDMARK = "R-CONNECT";

    private Brand() { }

    public static Icon mark(int size, Color color) {
        return new AppIcon(AppIcon.Type.R_CONNECT_MARK, color, size);
    }

    public static Font wordmarkFont(float size) {
        return Theme.font(Font.BOLD, size).deriveFont(Map.of(TextAttribute.TRACKING, 0.14f));
    }

    /** Horizontal lockup: monogram followed by the wordmark. */
    public static JLabel lockup(int markSize, float fontSize) {
        JLabel l = new JLabel(WORDMARK);
        l.setIcon(mark(markSize, Theme.PRIMARY));
        l.setIconTextGap(Math.max(8, markSize / 2));
        l.setFont(wordmarkFont(fontSize));
        l.setForeground(Theme.TEXT);
        l.getAccessibleContext().setAccessibleName("R-Connect");
        return l;
    }

    /** Monogram on a rounded tile, used for the auth hero and the window icon. */
    public static JComponent tile(int size) {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.PRIMARY_SOFT);
                g.fillRoundRect(0, 0, size, size, size / 3, size / 3);
                g.setColor(Theme.PRIMARY_BORDER);
                g.drawRoundRect(0, 0, size - 1, size - 1, size / 3, size / 3);
                int inner = Math.round(size * 0.58f);
                mark(inner, Theme.PRIMARY).paintIcon(this, g, (size - inner) / 2, (size - inner) / 2);
                g.dispose();
            }
        };
        Dimension d = new Dimension(size, size);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        return c;
    }

    /** Window/taskbar icon (rendered at several sizes by the OS). */
    public static java.awt.Image windowIcon(int size) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.APP_BACKGROUND);
        g.fillRoundRect(0, 0, size, size, size / 4, size / 4);
        g.setColor(Theme.PRIMARY_BORDER);
        g.drawRoundRect(0, 0, size - 1, size - 1, size / 4, size / 4);
        int inner = Math.round(size * 0.66f);
        mark(inner, Theme.PRIMARY).paintIcon(null, g, (size - inner) / 2, (size - inner) / 2);
        g.dispose();
        return img;
    }
}
