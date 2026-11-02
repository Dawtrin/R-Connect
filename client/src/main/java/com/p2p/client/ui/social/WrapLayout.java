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

final class WrapLayout extends FlowLayout {
    WrapLayout() { super(FlowLayout.LEFT, 8, 6); }
    @Override public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int width = target.getWidth();
            if (width <= 0 && target.getParent() != null) width = target.getParent().getWidth();
            if (width <= 0) width = 620;
            Insets in = target.getInsets();
            int available = Math.max(1, width - in.left - in.right - 2 * getHgap());
            int x = 0, y = 0, rowHeight = 0, max = 0;
            for (Component c : target.getComponents()) {
                if (c.isVisible()) {
                    Dimension d = c.getPreferredSize();
                    if (x > 0 && x + getHgap() + d.width > available) {
                        max = Math.max(max, x);
                        y += rowHeight + getVgap();
                        x = 0;
                        rowHeight = 0;
                    }
                    if (x > 0) x += getHgap();
                    x += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }
            return new Dimension(Math.max(max, x) + in.left + in.right + 2 * getHgap(), y + rowHeight + in.top + in.bottom + 2 * getVgap());
        }
    }
}
