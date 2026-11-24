package com.p2p.client.ui.social;

import java.awt.Component;
import java.awt.Window;
import javax.swing.SwingUtilities;

/**
 * Lightweight, instantaneous bilingual UI helper (Vietnamese / English) scoped per SocialWindow instance.
 */
public final class I18n {
    private static volatile String fallbackLanguage = "vi";

    private I18n() {}

    public static void setFallbackLanguage(String lang) {
        fallbackLanguage = isEnglishCode(lang) ? "en" : "vi";
    }

    public static boolean isEnglishCode(String lang) {
        return lang != null && lang.trim().toLowerCase().startsWith("en");
    }

    public static String pick(String lang, String vi, String en) {
        return isEnglishCode(lang) ? en : vi;
    }

    public static String fromComponent(Component c, String vi, String en) {
        if (c instanceof SocialWindow sw) {
            return sw.tr(vi, en);
        }
        if (c != null) {
            Window w = SwingUtilities.getWindowAncestor(c);
            if (w instanceof SocialWindow sw) {
                return sw.tr(vi, en);
            }
            if (w != null && w.getOwner() instanceof SocialWindow swOwner) {
                return swOwner.tr(vi, en);
            }
        }
        return pick(fallbackLanguage, vi, en);
    }
}
