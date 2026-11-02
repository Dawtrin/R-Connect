package com.p2p.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Sidebar navigation row: icon + label + optional count. Self-painted so that default, hover, pressed,
 * active, focus and disabled states are identical everywhere. Remains a real {@link JButton}, so
 * keyboard activation (Space/Enter), tab focus and accessibility names work as usual.
 */
public final class NavItem extends JButton {
    private final AppIcon.Type iconType;
    private final String label;
    private boolean active;
    private int badge;

    public NavItem(String label, AppIcon.Type iconType) {
        super(label);
        this.label = label;
        this.iconType = iconType;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setHorizontalAlignment(SwingConstants.LEFT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        putClientProperty("html.disable", Boolean.TRUE);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setPreferredSize(new Dimension(Theme.SIDEBAR_WIDTH, 38));
        setMinimumSize(new Dimension(80, 38));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        getAccessibleContext().setAccessibleName(label);
    }

    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            repaint();
        }
    }

    public boolean isActive() { return active; }

    /** Shows an unread/count pill; 0 hides it. */
    public void setBadge(int count) {
        if (badge != count) {
            badge = count;
            getAccessibleContext().setAccessibleName(count > 0 ? label + ", " + count : label);
            repaint();
        }
    }

    public int getBadge() { return badge; }

    /** Plain label (without count) for lookups and tests. */
    public String label() { return label; }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        boolean enabled = isEnabled();
        boolean hover = enabled && getModel().isRollover();
        boolean pressed = enabled && getModel().isArmed() && getModel().isPressed();

        Color fill = null;
        if (active) fill = Theme.PRIMARY_SOFT;
        else if (pressed) fill = Theme.SURFACE_HIGH;
        else if (hover) fill = Theme.SURFACE_ELEVATED;
        if (fill != null) {
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, Theme.RADIUS_MD, Theme.RADIUS_MD));
        }
        if (active) {
            g.setColor(Theme.PRIMARY);
            g.fill(new RoundRectangle2D.Float(0, h / 2f - 9, 3, 18, 3, 3));
        }
        if (isFocusOwner() && !getModel().isPressed()) {
            g.setColor(Theme.alpha(Theme.PRIMARY, 150));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new RoundRectangle2D.Float(.6f, .6f, w - 1.2f, h - 1.2f, Theme.RADIUS_MD, Theme.RADIUS_MD));
        }

        Color iconColor = !enabled ? Theme.MUTED : active ? Theme.PRIMARY : hover ? Theme.TEXT : Theme.TEXT_SECONDARY;
        Color textColor = !enabled ? Theme.MUTED : active ? Theme.TEXT : hover ? Theme.TEXT : Theme.TEXT_SECONDARY;
        new AppIcon(iconType, iconColor, 17).paintIcon(this, g, 14, (h - 17) / 2);

        g.setFont(Theme.font(active ? Font.BOLD : Font.PLAIN, 13));
        FontMetrics fm = g.getFontMetrics();
        int textX = 14 + 17 + 11;
        int baseline = (h - fm.getHeight()) / 2 + fm.getAscent();

        int right = w - 12;
        if (badge > 0) {
            String text = badge > 99 ? "99+" : String.valueOf(badge);
            Font small = Theme.font(Font.BOLD, 11);
            FontMetrics bm = g.getFontMetrics(small);
            int bw = Math.max(20, bm.stringWidth(text) + 12), bh = 18;
            int bx = right - bw, by = (h - bh) / 2;
            g.setColor(active ? Theme.PRIMARY : Theme.PRIMARY_DARK);
            g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
            g.setColor(Theme.ON_PRIMARY);
            g.setFont(small);
            g.drawString(text, bx + (bw - bm.stringWidth(text)) / 2, by + (bh - bm.getHeight()) / 2 + bm.getAscent());
            right = bx - 8;
            g.setFont(Theme.font(active ? Font.BOLD : Font.PLAIN, 13));
        }
        g.setColor(textColor);
        String shown = ellipsize(label, fm, right - textX);
        g.drawString(shown, textX, baseline);
        g.dispose();
    }

    private static String ellipsize(String text, FontMetrics fm, int max) {
        if (fm.stringWidth(text) <= max) return text;
        String dots = "…";
        int end = text.length();
        while (end > 1 && fm.stringWidth(text.substring(0, end) + dots) > max) end--;
        return text.substring(0, end) + dots;
    }
}
