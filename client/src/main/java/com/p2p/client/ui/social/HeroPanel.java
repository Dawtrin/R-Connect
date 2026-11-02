package com.p2p.client.ui.social;

import com.p2p.client.ui.AppIcon;
import com.p2p.client.ui.Brand;
import com.p2p.client.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * The one expressive moment of the product: the sign-in side panel. A large Connection Monogram sits inside a quiet
 * graph of nodes and links, echoing the three nodes of the mark. Everything else in the app stays plain.
 */
final class HeroPanel extends JPanel {
    // Normalised node positions (x, y in 0..1) laid out once; the monogram's own nodes are drawn on top.
    private static final double[][] NODES = {
            {0.12, 0.18}, {0.30, 0.10}, {0.82, 0.16}, {0.90, 0.38}, {0.08, 0.52}, {0.20, 0.78},
            {0.74, 0.74}, {0.92, 0.66}, {0.55, 0.90}, {0.40, 0.30}, {0.66, 0.30}
    };
    private static final int[][] LINKS = {
            {0, 1}, {1, 9}, {9, 10}, {10, 2}, {2, 3}, {0, 4}, {4, 5}, {5, 8}, {8, 6}, {6, 7}, {3, 7}, {9, 4}, {10, 6}
    };

    HeroPanel(String headline, String detail) {
        setOpaque(true);
        setBackground(Theme.NAV_BACKGROUND);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        JLabel lockup = Brand.lockup(24, 14f);
        lockup.setBorder(BorderFactory.createEmptyBorder(30, 34, 0, 0));
        add(lockup, BorderLayout.NORTH);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.setBorder(BorderFactory.createEmptyBorder(0, 34, 40, 34));
        JTextArea title = wrapped(headline, Theme.font(Font.BOLD, 24), Theme.TEXT);
        JTextArea sub = wrapped(detail, Theme.font(Font.PLAIN, 13), Theme.TEXT_SECONDARY);
        copy.add(title);
        copy.add(Box.createVerticalStrut(10));
        copy.add(sub);
        add(copy, BorderLayout.SOUTH);
    }

    private static JTextArea wrapped(String text, Font font, Color color) {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setFocusable(false);
        ((javax.swing.text.DefaultCaret) a.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        a.setText(text);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setBorder(null);
        a.setFont(font);
        a.setForeground(color);
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        return a;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int top = 70, bottom = h - 150;            // keep the graph clear of the lockup and the copy
        double gw = w - 80, gh = Math.max(120, bottom - top);
        int ox = 40;

        g.setStroke(new BasicStroke(1f));
        for (int[] link : LINKS) {
            double[] a = NODES[link[0]], b = NODES[link[1]];
            g.setColor(Theme.alpha(Theme.PRIMARY, 34));
            g.draw(new Line2D.Double(ox + a[0] * gw, top + a[1] * gh, ox + b[0] * gw, top + b[1] * gh));
        }
        for (double[] n : NODES) {
            double x = ox + n[0] * gw, y = top + n[1] * gh;
            g.setColor(Theme.alpha(Theme.PRIMARY, 70));
            g.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
        }

        int mark = Math.min(230, (int) Math.min(gw * 0.62, gh * 0.82));
        int mx = ox + (int) (gw - mark) / 2, my = top + (int) (gh - mark) / 2;
        g.setColor(Theme.alpha(Theme.PRIMARY, 22));
        g.fill(new Ellipse2D.Double(mx - mark * 0.18, my - mark * 0.18, mark * 1.36, mark * 1.36));
        new AppIcon(AppIcon.Type.R_CONNECT_MARK, Theme.PRIMARY, mark).paintIcon(this, g, mx, my);
        g.dispose();
    }
}
