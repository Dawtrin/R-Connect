package com.p2p.client.ui.social;

import com.p2p.client.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Compact selectable list row (conversations, friends, people): leading avatar, flexible centre, optional trailing
 * content. Handles rest / hover / selected painting so every list in the app behaves identically.
 */
final class ListRow extends JPanel {
    private final Color surface;
    private Color rest;
    private boolean selected;
    private Color fill;
    private Theme.Avatar ringAvatar;

    ListRow(Color surface, boolean selected, Runnable onClick) {
        super(new BorderLayout(10, 0));
        this.surface = surface;
        this.selected = selected;
        this.rest = selected ? Theme.PRIMARY_SOFT : surface;
        this.fill = rest;
        setOpaque(false);
        setBorder(new EmptyBorder(9, 10, 9, 10));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { paintState(ListRow.this.selected ? rest : Theme.SURFACE_ELEVATED); }
            @Override public void mouseExited(MouseEvent e) { paintState(rest); }
            @Override public void mouseClicked(MouseEvent e) { if (onClick != null) onClick.run(); }
        });
    }

    /** Registers the avatar whose presence ring must follow the row background. */
    void ring(Theme.Avatar avatar) {
        this.ringAvatar = avatar;
        avatar.setRing(rest);
    }

    void setSelected(boolean value) {
        selected = value;
        rest = value ? Theme.PRIMARY_SOFT : surface;
        paintState(rest);
    }

    private void paintState(Color c) {
        fill = c;
        if (ringAvatar != null) ringAvatar.setRing(c);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), Theme.RADIUS_MD, Theme.RADIUS_MD));
        if (selected) {
            g.setColor(Theme.PRIMARY_BORDER);
            g.draw(new RoundRectangle2D.Float(.5f, .5f, getWidth() - 1f, getHeight() - 1f, Theme.RADIUS_MD, Theme.RADIUS_MD));
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
