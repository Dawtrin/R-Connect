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

/** Lays out one child horizontally centred with a maximum width; height follows the child's preferred height. */
final class CenteredLayout implements LayoutManager {
    private final int maxWidth;
    CenteredLayout(int maxWidth) { this.maxWidth = maxWidth; }
    public void addLayoutComponent(String name, Component comp) { }
    public void removeLayoutComponent(Component comp) { }
    public Dimension preferredLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        Component c = parent.getComponentCount() > 0 ? parent.getComponent(0) : null;
        if (c == null) return new Dimension(0, 0);
        int width = Math.min(maxWidth, Math.max(1, parent.getWidth() - in.left - in.right));
        if (parent.getWidth() <= 0) width = maxWidth;
        c.setSize(width, c.getHeight());
        Dimension d = c.getPreferredSize();
        return new Dimension(d.width + in.left + in.right, d.height + in.top + in.bottom);
    }
    public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, preferredLayoutSize(parent).height); }
    public void layoutContainer(Container parent) {
        if (parent.getComponentCount() == 0) return;
        Insets in = parent.getInsets();
        int avail = parent.getWidth() - in.left - in.right;
        int w = Math.min(maxWidth, avail);
        Component c = parent.getComponent(0);
        c.setBounds(in.left + (avail - w) / 2, in.top, w, parent.getHeight() - in.top - in.bottom);
    }
}
