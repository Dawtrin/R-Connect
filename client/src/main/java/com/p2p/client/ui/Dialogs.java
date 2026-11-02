package com.p2p.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Themed replacements for JOptionPane. All dialogs are modal (they block the caller exactly like
 * JOptionPane did) and must be opened from the Event Dispatch Thread.
 */
public final class Dialogs {
    public enum Kind { INFO, SUCCESS, WARNING, ERROR }

    private Dialogs() { }

    /** Single-button notice. */
    public static void notice(Component parent, String title, String message, Kind kind) {
        show(parent, title, message, kind, "Đã hiểu", null, false);
    }

    /** Two-button confirmation; returns true when the primary button is chosen. */
    public static boolean confirm(Component parent, String title, String message, String confirmText, boolean danger) {
        return show(parent, title, message, danger ? Kind.WARNING : Kind.INFO, confirmText, "Hủy", danger) == 0;
    }

    /** Returns 0 for the primary choice, 1 for the secondary choice or when dismissed. */
    public static int choose(Component parent, String title, String message, Kind kind, String primary, String secondary) {
        return show(parent, title, message, kind, primary, secondary, false);
    }

    private static int show(Component parent, String title, String message, Kind kind, String primaryText,
                            String secondaryText, boolean danger) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner == null && parent instanceof Window w) owner = w;
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        int[] result = {1};

        Color tone = switch (kind) {
            case SUCCESS -> Theme.SUCCESS;
            case WARNING -> Theme.WARNING;
            case ERROR -> Theme.DANGER;
            case INFO -> Theme.PRIMARY;
        };
        AppIcon.Type iconType = switch (kind) {
            case SUCCESS -> AppIcon.Type.CHECK;
            case ERROR, WARNING -> AppIcon.Type.SHIELD;
            case INFO -> AppIcon.Type.R_CONNECT_MARK;
        };

        JPanel content = new JPanel(new BorderLayout(16, 0));
        content.setBackground(Theme.SURFACE_ELEVATED);
        content.setBorder(new EmptyBorder(22, 24, 18, 24));

        JPanel badge = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(tone, 32));
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        badge.setOpaque(false);
        badge.setPreferredSize(new Dimension(40, 40));
        badge.add(new JLabel(new AppIcon(iconType, tone, 18)));
        JPanel badgeHolder = new JPanel(new BorderLayout());
        badgeHolder.setOpaque(false);
        badgeHolder.add(badge, BorderLayout.NORTH);
        content.add(badgeHolder, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.font(Font.BOLD, 15));
        heading.setForeground(Theme.TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.putClientProperty("html.disable", Boolean.TRUE);
        JTextArea body = new JTextArea(message);
        body.setEditable(false);
        body.setFocusable(false);
        body.setOpaque(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(Theme.font(Font.PLAIN, 13));
        body.setForeground(Theme.TEXT_SECONDARY);
        body.setBorder(new EmptyBorder(6, 0, 0, 0));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setColumns(32);
        text.add(heading);
        text.add(body);
        content.add(text, BorderLayout.CENTER);

        JButton primary = danger ? Theme.dangerButton(primaryText) : Theme.primaryButton(primaryText);
        primary.addActionListener(e -> { result[0] = 0; dialog.dispose(); });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(18, 0, 0, 0));
        if (secondaryText != null) {
            JButton secondary = Theme.secondaryButton(secondaryText);
            secondary.addActionListener(e -> dialog.dispose());
            actions.add(secondary);
        }
        actions.add(primary);
        content.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(primary);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(400, dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    /** Single-line text prompt; returns the trimmed text, or null when cancelled. */
    public static String prompt(Component parent, String title, String label, String confirmText) {
        JTextField field = new JTextField();
        field.setName("promptField");
        Theme.styleInput(field);
        field.setPreferredSize(new Dimension(340, 38));
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.font(Font.BOLD, 12));
        l.setForeground(Theme.TEXT_SECONDARY);
        body.add(l, BorderLayout.NORTH);
        body.add(field, BorderLayout.CENTER);
        return form(parent, title, body, confirmText, field) ? field.getText().trim() : null;
    }

    /** Confirmation with custom content; returns true when the primary button is chosen. */
    public static boolean confirmContent(Component parent, String title, JComponent content, String confirmText) {
        return form(parent, title, content, confirmText, null);
    }

    private static boolean form(Component parent, String title, JComponent body, String confirmText, JComponent focus) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner == null && parent instanceof Window w) owner = w;
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] ok = {false};
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(Theme.SURFACE_ELEVATED);
        content.setBorder(new EmptyBorder(22, 24, 18, 24));
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.font(Font.BOLD, 15));
        heading.setForeground(Theme.TEXT);
        heading.putClientProperty("html.disable", Boolean.TRUE);
        content.add(heading, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        JButton primary = Theme.primaryButton(confirmText);
        primary.addActionListener(e -> { ok[0] = true; dialog.dispose(); });
        JButton cancel = Theme.secondaryButton("Hủy");
        cancel.addActionListener(e -> dialog.dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(primary);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(primary);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        if (focus != null) SwingUtilities.invokeLater(focus::requestFocusInWindow);
        dialog.setVisible(true);
        return ok[0];
    }
}
