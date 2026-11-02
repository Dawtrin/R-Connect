package com.p2p.client.ui.social;

import com.p2p.client.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/**
 * Modern floating emoji palette adhering strictly to R-Connect Obsidian styling.
 * Uses an undecorated popup window to guarantee reliable button click dispatching.
 */
public final class EmojiPicker {
    private static final String[] SMILEYS = {
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳",
            "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫",
            "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳",
            "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤔", "🤭", "🤫",
            "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮",
            "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮"
    };

    private static final String[] GESTURES = {
            "👍", "👎", "👏", "🙌", "👐", "🤲", "🤝", "👊", "✊", "🤛",
            "🤜", "🤞", "✌️", "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉",
            "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤙", "💪",
            "🙏", "✍️", "💅", "🤳", "💃", "🕺", "🚶", "🏃", "🧍", "🧎"
    };

    private static final String[] SYMBOLS = {
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "✨", "🌟",
            "⭐", "💫", "🔥", "💥", "💯", "✅", "❌", "⚠️", "❓", "❗",
            "💬", "💭", "💡", "📌", "📍", "🎯", "🚀", "🎉", "🏆", "👑"
    };

    private static JWindow activeWindow;

    private EmojiPicker() { }

    public static void show(Component anchor, Consumer<String> onSelect) {
        if (activeWindow != null) {
            activeWindow.dispose();
            activeWindow = null;
        }

        Window parent = SwingUtilities.getWindowAncestor(anchor);
        JWindow window = new JWindow(parent);
        activeWindow = window;

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(Theme.SURFACE);
        root.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Theme.BORDER, 1),
                new EmptyBorder(8, 8, 8, 8)
        ));
        root.setPreferredSize(new Dimension(320, 260));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.font(Font.BOLD, 12));
        tabs.setBackground(Theme.SURFACE);
        tabs.setForeground(Theme.TEXT_SECONDARY);

        tabs.addTab("Cảm xúc", createGrid(SMILEYS, onSelect, window));
        tabs.addTab("Cử chỉ", createGrid(GESTURES, onSelect, window));
        tabs.addTab("Ký hiệu", createGrid(SYMBOLS, onSelect, window));

        root.add(tabs, BorderLayout.CENTER);
        window.setContentPane(root);
        window.pack();

        try {
            Point pt = anchor.getLocationOnScreen();
            int x = pt.x;
            int y = pt.y - window.getHeight() - 6;
            if (y < 10) y = pt.y + anchor.getHeight() + 6;
            window.setLocation(x, y);
        } catch (Exception ex) {
            window.setLocationRelativeTo(anchor);
        }

        window.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                window.dispose();
                if (activeWindow == window) activeWindow = null;
            }
        });

        root.registerKeyboardAction(e -> {
            window.dispose();
            if (activeWindow == window) activeWindow = null;
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        window.setVisible(true);
    }

    private static JScrollPane createGrid(String[] emojis, Consumer<String> onSelect, JWindow window) {
        JPanel grid = new JPanel(new GridLayout(0, 8, 4, 4));
        grid.setBackground(Theme.SURFACE);

        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 18);

        for (String emoji : emojis) {
            JButton btn = new JButton(emoji);
            btn.setFont(emojiFont);
            btn.setPreferredSize(new Dimension(32, 32));
            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(false);
            btn.setBorder(new EmptyBorder(2, 2, 2, 2));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    btn.setContentAreaFilled(true);
                    btn.setBackground(Theme.SURFACE_ELEVATED);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    btn.setContentAreaFilled(false);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    onSelect.accept(emoji);
                    window.dispose();
                    if (activeWindow == window) activeWindow = null;
                }
            });

            btn.addActionListener(e -> {
                onSelect.accept(emoji);
                window.dispose();
                if (activeWindow == window) activeWindow = null;
            });

            grid.add(btn);
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.setBackground(Theme.SURFACE);
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }
}
