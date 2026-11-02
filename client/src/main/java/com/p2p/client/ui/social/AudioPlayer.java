package com.p2p.client.ui.social;

import javax.sound.sampled.*;
import java.io.File;
import java.util.Objects;

/**
 * Global audio playback manager ensuring single-track playback at any given time.
 * Automatically notifies UI listeners on completion.
 */
public final class AudioPlayer {
    private static Clip activeClip;
    private static File activeFile;
    private static Runnable activeCallback;

    private AudioPlayer() { }

    public static synchronized void togglePlay(File file, Runnable onStateChanged) {
        if (isPlaying(file)) {
            stop();
            if (onStateChanged != null) onStateChanged.run();
            return;
        }

        stop();

        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);

            activeClip = clip;
            activeFile = file;
            activeCallback = onStateChanged;

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    synchronized (AudioPlayer.class) {
                        if (activeClip == clip) {
                            clip.close();
                            activeClip = null;
                            activeFile = null;
                            Runnable cb = activeCallback;
                            activeCallback = null;
                            if (cb != null) {
                                javax.swing.SwingUtilities.invokeLater(cb);
                            }
                        }
                    }
                }
            });

            clip.start();
            if (onStateChanged != null) onStateChanged.run();
        } catch (Exception ex) {
            stop();
            if (onStateChanged != null) onStateChanged.run();
        }
    }

    public static synchronized boolean isPlaying(File file) {
        return activeClip != null && activeClip.isRunning() && Objects.equals(activeFile, file);
    }

    public static synchronized void stop() {
        if (activeClip != null) {
            try {
                activeClip.stop();
                activeClip.close();
            } catch (Exception ignored) {}
            activeClip = null;
            activeFile = null;
            Runnable cb = activeCallback;
            activeCallback = null;
            if (cb != null) {
                javax.swing.SwingUtilities.invokeLater(cb);
            }
        }
    }
}
