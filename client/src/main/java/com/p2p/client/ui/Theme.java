package com.p2p.client.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * R-Connect design tokens and component factories (Obsidian system).
 *
 * <p>Every colour, radius, spacing step and type size used by the client is defined here. Screens should
 * reference these tokens instead of literal values so that the whole product can be re-tuned in one place.
 *
 * <p>Accent policy: the accent ("Ion", a muted periwinkle) is reserved for primary actions, the active
 * navigation item, focus rings, selection and a few status indicators. Everything else is neutral.
 */
public final class Theme {
    // ---- Surfaces (Obsidian) -------------------------------------------------------------------------
    public static final Color APP_BACKGROUND = hex(0x090A0C);
    public static final Color SURFACE = hex(0x111318);
    public static final Color SURFACE_ELEVATED = hex(0x181B22);
    public static final Color SURFACE_HIGH = hex(0x20242D);
    public static final Color NAV_BACKGROUND = hex(0x0D0E11);
    public static final Color CHAT_BACKGROUND = APP_BACKGROUND;
    public static final Color COMPOSER_BACKGROUND = SURFACE;
    public static final Color CARD_BACKGROUND = SURFACE;
    public static final Color FILE_CARD = SURFACE_ELEVATED;

    // ---- Borders -------------------------------------------------------------------------------------
    public static final Color BORDER = hex(0x242832);
    public static final Color BORDER_STRONG = hex(0x303541);
    public static final Color CARD_BORDER = BORDER;

    // ---- Text ----------------------------------------------------------------------------------------
    public static final Color TEXT = hex(0xF5F5F7);
    public static final Color TEXT_SECONDARY = hex(0xA1A6B3);
    public static final Color MUTED = hex(0x868C9B);

    // ---- Accent ("Ion") ------------------------------------------------------------------------------
    public static final Color PRIMARY = hex(0x86A0EE);
    public static final Color PRIMARY_HOVER = hex(0xA0B6F4);
    public static final Color PRIMARY_DARK = hex(0x6F88D6);      // pressed
    public static final Color PRIMARY_LIGHT = hex(0xB4C4F7);     // accent used as text on dark surfaces
    public static final Color PRIMARY_SOFT = hex(0x171C2B);      // faint accent wash (selected rows, own bubbles)
    public static final Color PRIMARY_BORDER = hex(0x2A3556);    // border that goes with PRIMARY_SOFT
    public static final Color ON_PRIMARY = hex(0x0A0B10);        // text/icon on accent fill

    // ---- Semantic ------------------------------------------------------------------------------------
    public static final Color ONLINE = hex(0x4FBF8F);
    public static final Color SUCCESS = hex(0x4FBF8F);
    public static final Color DANGER = hex(0xE5696B);
    public static final Color DANGER_SOFT = hex(0x2A1517);
    public static final Color WARNING = hex(0xD9A441);
    public static final Color INFO = hex(0x7FA6E0);
    public static final Color LIKE = hex(0xE5696B);

    // ---- Chat ----------------------------------------------------------------------------------------
    public static final Color BUBBLE_OWN = hex(0x1C2338);
    public static final Color BUBBLE_OWN_BORDER = PRIMARY_BORDER;
    public static final Color BUBBLE_OTHER = hex(0x14171D);

    // ---- Spacing / radius / sizes --------------------------------------------------------------------
    public static final int SPACE_1 = 4, SPACE_2 = 8, SPACE_3 = 12, SPACE_4 = 16, SPACE_5 = 24, SPACE_6 = 32;
    public static final int RADIUS_SM = 6, RADIUS_MD = 8, RADIUS_LG = 12;
    public static final int CONTROL_HEIGHT = 36;
    public static final int SIDEBAR_WIDTH = 232;
    /** Maximum readable width of list/column content (feeds, cards). */
    public static final int COLUMN_WIDTH = 760;

    // ---- Typography ----------------------------------------------------------------------------------
    /** First installed family from this list; all of them cover Vietnamese diacritics. */
    private static final String[] FONT_CANDIDATES = {
            "Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Noto Sans", "Roboto", "Ubuntu", "DejaVu Sans", "SansSerif"};
    public static final String FONT_FAMILY = resolveFamily();

    /**
     * Be Vietnam Pro (SIL OFL, bundled in resources/fonts): a typeface drawn for Vietnamese, so stacked diacritics
     * keep their spacing at small sizes. Regular carries body text, SemiBold carries emphasis and titles.
     * Falls back to the system family when the resources are not on the classpath.
     */
    private static final Font BODY_FACE = loadFace("BeVietnamPro-Regular.ttf");
    private static final Font EMPHASIS_FACE = loadFace("BeVietnamPro-SemiBold.ttf");

    private static Font loadFace(String file) {
        try (java.io.InputStream in = Theme.class.getResourceAsStream("/fonts/" + file)) {
            if (in == null) return null;
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (Exception | Error e) {
            return null;
        }
    }

    private Theme() { }

    private static String resolveFamily() {
        Set<String> installed = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String candidate : FONT_CANDIDATES) {
            if (installed.contains(candidate)) return candidate;
        }
        return Font.SANS_SERIF;
    }

    public static Color hex(int rgb) {
        return new Color(rgb);
    }

    private static String css(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static Font font(int style, float size) {
        Font face = (style & Font.BOLD) != 0 ? EMPHASIS_FACE : BODY_FACE;
        if (face != null) {
            Font sized = face.deriveFont(size);
            return (style & Font.ITALIC) != 0 ? sized.deriveFont(Font.ITALIC) : sized;
        }
        return new Font(FONT_FAMILY, style, Math.round(size));
    }

    /** Composite font that provides seamless Windows fallback for Unicode emojis and Vietnamese text. */
    public static Font messageFont(int style, float size) {
        return new Font(Font.SANS_SERIF, style, Math.round(size));
    }

    public static Font heroFont(float size) {
        return font(Font.BOLD, size);
    }

    /** Typographic scale: 11 caption, 12 small, 13 body, 14 emphasis, 18 section title, 22 page title. */
    public static Font caption() { return font(Font.PLAIN, 11); }
    public static Font small() { return font(Font.PLAIN, 12); }
    public static Font body() { return font(Font.PLAIN, 13); }
    public static Font bodyStrong() { return font(Font.BOLD, 13); }
    public static Font title() { return font(Font.BOLD, 22); }

    public static Border padding(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }

    public static Border cardBorder() {
        return new CompoundBorder(new LineBorder(CARD_BORDER, 1), new EmptyBorder(14, 16, 14, 16));
    }

    // ===============================================================================================
    // Look & Feel installation
    // ===============================================================================================

    /**
     * Installs FlatLaf with R-Connect tokens. Call once before creating any Swing component.
     * FlatLaf derives scrollbars, tabs, popups, tables, combo boxes, etc. from the variables below.
     */
    public static void install() {
        FlatLaf.setGlobalExtraDefaults(Map.ofEntries(
                Map.entry("@background", css(APP_BACKGROUND)),
                Map.entry("@foreground", css(TEXT)),
                Map.entry("@componentBackground", css(SURFACE)),
                Map.entry("@buttonBackground", css(SURFACE_ELEVATED)),
                Map.entry("@menuBackground", css(SURFACE_ELEVATED)),
                Map.entry("@textBackground", css(SURFACE)),
                Map.entry("@accentColor", css(PRIMARY)),
                Map.entry("@selectionBackground", css(hex(0x222B47))),
                Map.entry("@selectionForeground", css(TEXT)),
                Map.entry("@disabledForeground", css(MUTED)),
                Map.entry("@borderColor", css(BORDER)),
                Map.entry("@focusColor", css(PRIMARY)),
                Map.entry("@scrollbarThumb", css(hex(0x2A2F3A)))
        ));
        FlatDarkLaf.setup();

        UIManager.put("defaultFont", font(Font.PLAIN, 13));
        UIManager.put("Component.arc", RADIUS_MD);
        UIManager.put("Button.arc", RADIUS_MD);
        UIManager.put("TextComponent.arc", RADIUS_MD);
        UIManager.put("ProgressBar.arc", 6);
        UIManager.put("CheckBox.arc", 4);
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.borderColor", BORDER_STRONG);
        UIManager.put("Component.disabledBorderColor", BORDER);
        UIManager.put("Component.focusedBorderColor", PRIMARY);
        UIManager.put("Component.focusColor", PRIMARY);
        UIManager.put("Component.hoverBorderColor", hex(0x434A5A));
        UIManager.put("Component.error.borderColor", DANGER);
        UIManager.put("Component.error.focusedBorderColor", DANGER);

        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextField.placeholderForeground", MUTED);
        UIManager.put("TextField.margin", new Insets(6, 10, 6, 10));
        UIManager.put("PasswordField.margin", new Insets(6, 10, 6, 10));
        UIManager.put("TextArea.background", SURFACE);
        UIManager.put("TextArea.margin", new Insets(6, 8, 6, 8));
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.padding", new Insets(5, 10, 5, 6));
        UIManager.put("ComboBox.buttonBackground", SURFACE);
        UIManager.put("ComboBox.buttonArrowColor", TEXT_SECONDARY);
        UIManager.put("PopupMenu.background", SURFACE_ELEVATED);
        UIManager.put("PopupMenu.borderColor", BORDER_STRONG);
        UIManager.put("List.background", SURFACE);
        UIManager.put("List.selectionBackground", hex(0x222B47));
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("Panel.background", APP_BACKGROUND);
        UIManager.put("RootPane.background", APP_BACKGROUND);
        UIManager.put("OptionPane.background", SURFACE_ELEVATED);
        UIManager.put("OptionPane.messageForeground", TEXT);

        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.thumb", hex(0x2A2F3A));
        UIManager.put("ScrollBar.hoverThumbColor", hex(0x3A4150));
        UIManager.put("ScrollBar.pressedThumbColor", hex(0x4A5266));
        UIManager.put("ScrollBar.track", APP_BACKGROUND);
        UIManager.put("ScrollBar.showButtons", false);

        UIManager.put("TabbedPane.underlineColor", PRIMARY);
        UIManager.put("TabbedPane.selectedForeground", TEXT);
        UIManager.put("TabbedPane.foreground", TEXT_SECONDARY);
        UIManager.put("TabbedPane.hoverColor", SURFACE_ELEVATED);
        UIManager.put("TabbedPane.tabHeight", 38);
        UIManager.put("TabbedPane.tabInsets", new Insets(4, 14, 4, 14));
        UIManager.put("TabbedPane.contentAreaColor", APP_BACKGROUND);
        UIManager.put("TabbedPane.background", APP_BACKGROUND);

        UIManager.put("ToolTip.background", SURFACE_HIGH);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", new CompoundBorder(new LineBorder(BORDER_STRONG, 1), new EmptyBorder(4, 8, 4, 8)));

        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.rowHeight", 36);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("TableHeader.background", SURFACE_ELEVATED);
        UIManager.put("TableHeader.foreground", TEXT_SECONDARY);
        UIManager.put("Table.alternateRowColor", hex(0x0F1115));
        UIManager.put("Table.selectionBackground", hex(0x222B47));
        UIManager.put("Table.selectionForeground", TEXT);

        UIManager.put("ProgressBar.background", SURFACE_HIGH);
        UIManager.put("ProgressBar.foreground", PRIMARY);
        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("SplitPane.dividerSize", 1);
        UIManager.put("SplitPaneDivider.gripColor", BORDER);
    }

    // ===============================================================================================
    // Buttons
    // ===============================================================================================

    public enum ButtonStyle { PRIMARY_BUTTON, SECONDARY, GHOST, DANGER_OUTLINE, DANGER_FILLED }

    /** Applies one of the five R-Connect button styles (hover/pressed/focus/disabled handled by FlatLaf). */
    public static <T extends AbstractButton> T style(T b, ButtonStyle kind) {
        b.setFont(font(Font.BOLD, 13));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("html.disable", Boolean.TRUE);
        String common = "arc:" + RADIUS_MD + "; margin:8,14,8,14; focusedBorderColor:" + css(PRIMARY) + "; disabledText:" + css(MUTED) + "; ";
        switch (kind) {
            case PRIMARY_BUTTON -> {
                b.setForeground(ON_PRIMARY);
                b.setBackground(PRIMARY);
                b.putClientProperty("FlatLaf.style", common
                        + "borderWidth:0; background:" + css(PRIMARY) + "; foreground:" + css(ON_PRIMARY)
                        + "; hoverBackground:" + css(PRIMARY_HOVER) + "; pressedBackground:" + css(PRIMARY_DARK)
                        + "; default.background:" + css(PRIMARY) + "; default.foreground:" + css(ON_PRIMARY)
                        + "; default.borderWidth:0; focusedBackground:" + css(PRIMARY)
                        + "; disabledBackground:" + css(SURFACE_HIGH));
            }
            case SECONDARY -> {
                b.setForeground(TEXT);
                b.setBackground(SURFACE_ELEVATED);
                b.putClientProperty("FlatLaf.style", common
                        + "borderWidth:1; borderColor:" + css(BORDER_STRONG) + "; background:" + css(SURFACE_ELEVATED)
                        + "; foreground:" + css(TEXT) + "; hoverBackground:" + css(SURFACE_HIGH)
                        + "; hoverBorderColor:" + css(hex(0x434A5A)) + "; pressedBackground:" + css(hex(0x272C37))
                        + "; focusedBackground:" + css(SURFACE_ELEVATED) + "; disabledBackground:" + css(SURFACE)
                        + "; disabledBorderColor:" + css(BORDER));
            }
            case GHOST -> {
                b.setForeground(TEXT_SECONDARY);
                b.setOpaque(false);
                b.putClientProperty("JButton.buttonType", "borderless");
                b.putClientProperty("FlatLaf.style", common
                        + "foreground:" + css(TEXT_SECONDARY) + "; hoverBackground:" + css(SURFACE_ELEVATED)
                        + "; pressedBackground:" + css(SURFACE_HIGH) + "; focusedBackground:" + css(SURFACE_ELEVATED));
            }
            case DANGER_OUTLINE -> {
                b.setForeground(DANGER);
                b.setBackground(SURFACE_ELEVATED);
                b.putClientProperty("FlatLaf.style", common
                        + "borderWidth:1; borderColor:" + css(hex(0x4A2A2D)) + "; background:" + css(SURFACE_ELEVATED)
                        + "; foreground:" + css(DANGER) + "; hoverBackground:" + css(DANGER_SOFT)
                        + "; hoverBorderColor:" + css(DANGER) + "; pressedBackground:" + css(hex(0x351A1D))
                        + "; focusedBackground:" + css(SURFACE_ELEVATED));
            }
            case DANGER_FILLED -> {
                b.setForeground(Color.WHITE);
                b.setBackground(DANGER);
                b.putClientProperty("FlatLaf.style", common
                        + "borderWidth:0; background:" + css(DANGER) + "; foreground:#FFFFFF; hoverBackground:"
                        + css(hex(0xEE7F81)) + "; pressedBackground:" + css(hex(0xCC5558))
                        + "; focusedBackground:" + css(DANGER) + "; default.background:" + css(DANGER) + "; default.foreground:#FFFFFF");
            }
        }
        return b;
    }

    public static JButton primaryButton(String text) { return style(new JButton(text), ButtonStyle.PRIMARY_BUTTON); }
    public static JButton secondaryButton(String text) { return style(new JButton(text), ButtonStyle.SECONDARY); }
    public static JButton ghostButton(String text) { return style(new JButton(text), ButtonStyle.GHOST); }
    public static JButton dangerButton(String text) { return style(new JButton(text), ButtonStyle.DANGER_FILLED); }
    public static JButton dangerOutlineButton(String text) { return style(new JButton(text), ButtonStyle.DANGER_OUTLINE); }

    public static JButton iconButton(Icon icon, String tooltip, boolean primary) {
        JButton button = style(new JButton(icon), primary ? ButtonStyle.PRIMARY_BUTTON : ButtonStyle.SECONDARY);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(CONTROL_HEIGHT, CONTROL_HEIGHT));
        button.setMinimumSize(new Dimension(CONTROL_HEIGHT, CONTROL_HEIGHT));
        button.putClientProperty("FlatLaf.style", button.getClientProperty("FlatLaf.style") + "; margin:6,6,6,6");
        return button;
    }

    public static JButton navButton(Icon icon, String tooltip, boolean selected) {
        JButton button = style(new JButton(icon), ButtonStyle.GHOST);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(44, 44));
        button.setMinimumSize(new Dimension(44, 44));
        button.setForeground(selected ? PRIMARY_LIGHT : MUTED);
        return button;
    }

    public static JButton actionButton(String text, Icon icon, boolean primary) {
        JButton button = primary ? primaryButton(text) : secondaryButton(text);
        button.setIcon(icon);
        button.setIconTextGap(8);
        button.setFocusable(false);
        return button;
    }

    // ===============================================================================================
    // Inputs & small widgets
    // ===============================================================================================

    /** Standard R-Connect single-line input: 38px high, surface fill, accent focus border. */
    public static <T extends JTextField> T styleInput(T input) {
        input.setFont(font(Font.PLAIN, 14));
        input.setBackground(SURFACE);
        input.setForeground(TEXT);
        input.setCaretColor(TEXT);
        input.setSelectionColor(hex(0x2B3760));
        input.setPreferredSize(new Dimension(Math.max(input.getColumns() > 0 ? 80 : 160, input.getPreferredSize().width), 38));
        input.putClientProperty("FlatLaf.style", "focusedBorderColor:" + css(PRIMARY) + "; arc:" + RADIUS_MD);
        return input;
    }

    public static JLabel badge(String text, Color bg, Color fg) {
        return ModernUI.badge(text, bg, fg);
    }

    public static JPanel roundedPanel(Color background, int arc) {
        return new RoundedPanel(background, arc);
    }

    /** Paints a filled rounded rectangle; content is laid out normally. */
    public static final class RoundedPanel extends JPanel {
        private Color fill;
        private Color stroke;
        private final int arc;

        public RoundedPanel(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        public void setFill(Color fill) { this.fill = fill; repaint(); }
        public void setStroke(Color stroke) { this.stroke = stroke; repaint(); }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            if (stroke != null) {
                g.setColor(stroke);
                g.draw(new RoundRectangle2D.Float(.5f, .5f, getWidth() - 1f, getHeight() - 1f, arc, arc));
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    /** Circular avatar: uploaded photo when available, otherwise a tonal initial. Optional presence dot. */
    public static final class Avatar extends JComponent {
        private String name;
        private final int size;
        private boolean online;
        private boolean showPresence;
        private Image image;
        private Color ring = SURFACE;

        public Avatar(String name, int size, boolean online) {
            this.name = name == null ? "?" : name;
            this.size = size;
            this.online = online;
            this.showPresence = online;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        public void setOnline(boolean online) { this.online = online; this.showPresence = true; repaint(); }
        public void setName(String name) { this.name = name == null ? "?" : name; repaint(); }
        public void setRing(Color ring) { this.ring = ring; repaint(); }
        public void setImage(Image image) { this.image = image; repaint(); }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            Ellipse2D disc = new Ellipse2D.Float(0, 0, size, size);
            if (image != null) {
                Shape oldClip = g.getClip();
                g.clip(disc);
                int iw = image.getWidth(null), ih = image.getHeight(null);
                if (iw > 0 && ih > 0) {
                    double scale = Math.max((double) size / iw, (double) size / ih);
                    int w = (int) Math.round(iw * scale), h = (int) Math.round(ih * scale);
                    g.drawImage(image, (size - w) / 2, (size - h) / 2, w, h, null);
                }
                g.setClip(oldClip);
            } else {
                float hue = Math.floorMod(name.hashCode(), 360) / 360f;
                g.setColor(Color.getHSBColor(hue, 0.32f, 0.30f));
                g.fill(disc);
                g.setColor(Color.getHSBColor(hue, 0.22f, 0.98f));
                g.setFont(font(Font.BOLD, size * 0.40f));
                String initial = name.isBlank() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase();
                FontMetrics metrics = g.getFontMetrics();
                int x = (size - metrics.stringWidth(initial)) / 2;
                int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(initial, x, y);
            }
            g.setColor(alpha(Color.WHITE, 22));
            g.draw(new Ellipse2D.Float(.5f, .5f, size - 1f, size - 1f));
            if (showPresence) {
                int dot = Math.max(8, size / 4);
                int dx = size - dot - 1, dy = size - dot - 1;
                g.setColor(ring);
                g.fillOval(dx - 2, dy - 2, dot + 4, dot + 4);
                g.setColor(online ? ONLINE : MUTED);
                g.fillOval(dx, dy, dot, dot);
            }
            g.dispose();
        }
    }

    /** Renders a small image icon cropped to a circle (used when an avatar needs an Icon). */
    public static Icon circleIcon(Image source, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.clip(new Ellipse2D.Float(0, 0, size, size));
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return new ImageIcon(out);
    }
}
