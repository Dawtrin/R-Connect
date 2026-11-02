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

final class StackPanel extends JPanel implements Scrollable {
    private final float alignment;
    StackPanel() { this(false); }
    StackPanel(boolean centered) {
        alignment = centered ? Component.CENTER_ALIGNMENT : Component.LEFT_ALIGNMENT;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }
    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 20; }
    public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(20, r.height - 30); }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return false; }
    @Override protected void addImpl(Component c, Object constraints, int index) {
        if (c instanceof JComponent jc) jc.setAlignmentX(alignment);
        super.addImpl(c, constraints, index);
    }
}
