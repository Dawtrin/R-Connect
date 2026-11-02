package com.p2p.client;

import com.p2p.client.ui.Brand;
import com.p2p.client.ui.Theme;
import com.p2p.client.ui.social.SocialWindow;

import javax.swing.SwingUtilities;

/** R-Connect client entry point. Visual tokens and Look and Feel setup live in {@link Theme#install()}. */
public final class ClientMain {
    private ClientMain() { }

    public static void main(String[] args) {
        Theme.install();
        SwingUtilities.invokeLater(() -> {
            SocialWindow window = new SocialWindow();
            window.setIconImages(java.util.List.of(Brand.windowIcon(32), Brand.windowIcon(64), Brand.windowIcon(128)));
            window.setVisible(true);
        });
    }
}
