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

final class CardPanel extends JPanel {
    CardPanel() {
        super(new BorderLayout(10, 10));
        setOpaque(false);
    }
    @Override public Dimension getMaximumSize() {
        if (Boolean.TRUE.equals(getClientProperty("CardPanel.fillWidth"))) {
            return new Dimension(Theme.COLUMN_WIDTH, getPreferredSize().height);
        }
        return super.getMaximumSize();
    }
    @Override protected void paintComponent(Graphics graphics) {
        if (Boolean.TRUE.equals(getClientProperty("CardPanel.flat"))) {
            super.paintComponent(graphics);
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getBackground());
        g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), Theme.RADIUS_LG, Theme.RADIUS_LG));
        g.setColor(Theme.CARD_BORDER);
        g.draw(new RoundRectangle2D.Float(.5f, .5f, getWidth() - 1f, getHeight() - 1f, Theme.RADIUS_LG, Theme.RADIUS_LG));
        g.dispose();
        super.paintComponent(graphics);
    }
}
