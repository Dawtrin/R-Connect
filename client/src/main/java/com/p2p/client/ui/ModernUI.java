package com.p2p.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/** Reusable R-Connect UI helpers: chips, empty states, inline notices, checklist rows and toasts. */
public final class ModernUI {
    private ModernUI() { }

    public enum Tone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }

    private static Color toneColor(Tone tone) {
        return switch (tone) {
            case ACCENT -> Theme.PRIMARY_LIGHT;
            case SUCCESS -> Theme.SUCCESS;
            case WARNING -> Theme.WARNING;
            case DANGER -> Theme.DANGER;
            case NEUTRAL -> Theme.TEXT_SECONDARY;
        };
    }

    /** Small tinted status chip (tinted fill + hairline border + toned text). */
    public static JLabel chip(String text, Tone tone) {
        Color c = toneColor(tone);
        return paintedChip(text, Theme.alpha(c, tone == Tone.NEUTRAL ? 24 : 30), Theme.alpha(c, 70), c);
    }

    /** Compatibility entry point: the supplied fill is used as a tint, never as a loud solid block. */
    public static JLabel badge(String text, Color bg, Color fg) {
        return paintedChip(text, Theme.alpha(bg, 80), Theme.alpha(bg, 140), Theme.TEXT);
    }

    private static JLabel paintedChip(String rawText, Color fill, Color stroke, Color fg) {
        String text = rawText.isEmpty() ? rawText : rawText.substring(0, 1).toUpperCase() + rawText.substring(1).toLowerCase();
        JLabel l = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(stroke);
                g2.draw(new RoundRectangle2D.Float(.5f, .5f, getWidth() - 1f, getHeight() - 1f, 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setFont(Theme.font(Font.BOLD, 11));
        l.setForeground(fg);
        l.setOpaque(false);
        l.setBorder(new EmptyBorder(3, 9, 3, 9));
        l.putClientProperty("html.disable", Boolean.TRUE);
        return l;
    }

    public static JPanel emptyState(Icon icon, String title, String subtitle) {
        return emptyState(icon, title, subtitle, null);
    }

    /** Centered empty state: icon medallion, title, one-line guidance and an optional next action. */
    public static JPanel emptyState(Icon icon, String title, String subtitle, JComponent action) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(48, 20, 48, 20));
        p.setAlignmentX(Component.CENTER_ALIGNMENT);

        if (icon != null) {
            JPanel medallion = new JPanel(new GridBagLayout()) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.SURFACE_ELEVATED);
                    g2.fill(new Ellipse2D.Float(0, 0, getWidth() - 1, getHeight() - 1));
                    g2.setColor(Theme.BORDER);
                    g2.draw(new Ellipse2D.Float(.5f, .5f, getWidth() - 2, getHeight() - 2));
                    g2.dispose();
                }
            };
            medallion.setOpaque(false);
            Dimension d = new Dimension(64, 64);
            medallion.setPreferredSize(d);
            medallion.setMinimumSize(d);
            medallion.setMaximumSize(d);
            medallion.add(new JLabel(icon));
            medallion.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(medallion);
            p.add(Box.createVerticalStrut(16));
        }

        JLabel t = new JLabel(title);
        t.setFont(Theme.font(Font.BOLD, 15));
        t.setForeground(Theme.TEXT);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        t.putClientProperty("html.disable", Boolean.TRUE);

        JLabel s = new JLabel("<html><div style='text-align:center;width:250px'>"
                + subtitle.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
        s.setFont(Theme.font(Font.PLAIN, 13));
        s.setForeground(Theme.TEXT_SECONDARY);
        s.setHorizontalAlignment(SwingConstants.CENTER);
        s.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(t);
        p.add(Box.createVerticalStrut(6));
        p.add(s);
        if (action != null) {
            p.add(Box.createVerticalStrut(18));
            action.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(action);
        }
        return p;
    }

    /** Inline notice bar (validation, connection problems). */
    public static JPanel alertBox(String text, Color bg, Color border, Color fg) {
        JPanel p = new Theme.RoundedPanel(bg, Theme.RADIUS_MD);
        ((Theme.RoundedPanel) p).setStroke(border);
        p.setLayout(new BorderLayout(8, 0));
        p.setBorder(new EmptyBorder(9, 12, 9, 12));
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.PLAIN, 13));
        l.setForeground(fg);
        l.putClientProperty("html.disable", Boolean.TRUE);
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    public static JLabel checklistItem(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.PLAIN, 12));
        l.setForeground(Theme.MUTED);
        l.setIcon(new AppIcon(AppIcon.Type.DOT, Theme.MUTED, 12));
        l.setIconTextGap(8);
        return l;
    }

    public enum ToastType { SUCCESS, ERROR, WARNING, INFO }

    /** Non-blocking confirmation anchored to the bottom-right of the window; disappears by itself. */
    public static void showToast(JFrame parent, String message, ToastType type) {
        if (parent == null || !parent.isShowing()) return;
        Color tone = switch (type) {
            case SUCCESS -> Theme.SUCCESS;
            case ERROR -> Theme.DANGER;
            case WARNING -> Theme.WARNING;
            case INFO -> Theme.PRIMARY;
        };
        JDialog toast = new JDialog(parent);
        toast.setUndecorated(true);
        toast.setFocusableWindowState(false);
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(Theme.SURFACE_HIGH);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_STRONG, 1),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, tone),
                        new EmptyBorder(10, 14, 10, 16))));
        JLabel lbl = new JLabel(message);
        lbl.setFont(Theme.font(Font.PLAIN, 13));
        lbl.setForeground(Theme.TEXT);
        lbl.putClientProperty("html.disable", Boolean.TRUE);
        p.add(lbl, BorderLayout.CENTER);
        toast.setContentPane(p);
        toast.pack();
        try {
            Point loc = parent.getLocationOnScreen();
            toast.setLocation(loc.x + parent.getWidth() - toast.getWidth() - 24, loc.y + parent.getHeight() - toast.getHeight() - 56);
            toast.setVisible(true);
            Timer timer = new Timer(3200, e -> toast.dispose());
            timer.setRepeats(false);
            timer.start();
        } catch (Exception ignored) { }
    }

    public static void updateChecklist(JLabel l, boolean valid, String text) {
        l.setText(text);
        if (valid) {
            l.setIcon(new AppIcon(AppIcon.Type.CHECK, Theme.SUCCESS, 12));
            l.setForeground(Theme.SUCCESS);
        } else {
            l.setIcon(new AppIcon(AppIcon.Type.DOT, Theme.MUTED, 12));
            l.setForeground(Theme.MUTED);
        }
    }
}
