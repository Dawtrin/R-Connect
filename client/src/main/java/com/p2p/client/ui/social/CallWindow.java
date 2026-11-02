package com.p2p.client.ui.social;

import com.p2p.client.network.social.VideoCallStreamer;
import com.p2p.client.network.social.VoiceCallStreamer;
import com.p2p.client.ui.AppIcon;
import com.p2p.client.ui.Theme;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Messenger-style standalone floating voice and video call window for R-Connect.
 * Features live physical webcam integration via Sarxos Webcam Capture,
 * instant local camera preview (PiP), screen sharing, and UDP voice streaming.
 */
public final class CallWindow extends JFrame {
    public enum State {
        OUTGOING_RINGING,
        INCOMING_RINGING,
        CONNECTED,
        ENDED
    }

    private final SocialWindow app;
    private final long targetUserId;
    private final String targetDisplayName;
    private final long conversationId;
    private final String callId;
    private final boolean isCaller;

    private String remoteHost;
    private int remoteAudioPort;
    private int remoteVideoPort;

    private VoiceCallStreamer voiceStreamer;
    private VideoCallStreamer videoStreamer;
    private State state;
    private boolean isVideoMode;
    private long callStartTime = 0;
    private javax.swing.Timer durationTimer;
    private javax.swing.Timer visualizerTimer;

    private final JLabel statusLabel;
    private final JLabel nameLabel;
    private final JPanel avatarPanel;
    private final JPanel visualizerPanel;
    private final JPanel controlBar;
    private final CardLayout centerCards = new CardLayout();
    private final JPanel centerContainer;
    private final JPanel videoViewport;
    private final JPanel pipPreview;

    private JButton muteButton;
    private JButton videoButton;
    private JButton screenShareButton;
    private JButton endCallButton;
    private JButton acceptButton;

    private volatile BufferedImage remoteVideoImage;
    private volatile BufferedImage localVideoImage;

    private final AtomicBoolean ringtoneRunning = new AtomicBoolean(false);
    private Thread ringtoneThread;
    private float[] eqLevels = new float[]{0.3f, 0.6f, 0.9f, 0.5f, 0.2f};

    public CallWindow(SocialWindow app, long targetUserId, String targetDisplayName, long conversationId, String callId, boolean isCaller, boolean isVideoMode) {
        super("Cuộc gọi · R-Connect");
        this.app = app;
        this.targetUserId = targetUserId;
        this.targetDisplayName = (targetDisplayName == null || targetDisplayName.isBlank()) ? "Người dùng #" + targetUserId : targetDisplayName;
        this.conversationId = conversationId;
        this.callId = (callId == null) ? UUID.randomUUID().toString() : callId;
        this.isCaller = isCaller;
        this.isVideoMode = isVideoMode;
        this.state = isCaller ? State.OUTGOING_RINGING : State.INCOMING_RINGING;

        int initW = isVideoMode ? 680 : 360;
        int initH = isVideoMode ? 520 : 480;
        setSize(initW, initH);
        setLocationRelativeTo(app);
        setResizable(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(Theme.APP_BACKGROUND);
        setLayout(new BorderLayout());

        // Header bar with status pill
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(14, 20, 8, 20));

        JLabel brand = new JLabel("R-CONNECT " + (isVideoMode ? "VIDEO" : "VOICE"));
        brand.setFont(Theme.font(Font.BOLD, 12));
        brand.setForeground(Theme.PRIMARY);

        JLabel p2pBadge = new JLabel("P2P · UDP");
        p2pBadge.setFont(Theme.font(Font.PLAIN, 11));
        p2pBadge.setForeground(Theme.MUTED);
        p2pBadge.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(2, 6, 2, 6)
        ));

        header.add(brand, BorderLayout.WEST);
        header.add(p2pBadge, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Center card container
        centerContainer = new JPanel(centerCards);
        centerContainer.setOpaque(false);

        // 1. Voice View Card
        JPanel voiceCard = new JPanel();
        voiceCard.setLayout(new BoxLayout(voiceCard, BoxLayout.Y_AXIS));
        voiceCard.setOpaque(false);
        voiceCard.setBorder(new EmptyBorder(24, 20, 20, 20));

        avatarPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int size = 96;
                int x = (w - size) / 2;
                int y = (h - size) / 2;

                if (state == State.CONNECTED) {
                    g2.setColor(new Color(0x38, 0x8B, 0xF5, 0x30));
                    g2.fill(new Ellipse2D.Float(x - 8, y - 8, size + 16, size + 16));
                } else if (state == State.OUTGOING_RINGING || state == State.INCOMING_RINGING) {
                    g2.setColor(new Color(0x23, 0x86, 0x36, 0x25));
                    g2.fill(new Ellipse2D.Float(x - 8, y - 8, size + 16, size + 16));
                }

                g2.setColor(Theme.SURFACE);
                g2.fill(new Ellipse2D.Float(x, y, size, size));

                g2.setColor(Theme.BORDER);
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(new Ellipse2D.Float(x, y, size, size));

                String initial = targetDisplayName.isEmpty() ? "R" : targetDisplayName.substring(0, 1).toUpperCase();
                g2.setFont(Theme.font(Font.BOLD, 36));
                g2.setColor(Theme.PRIMARY);
                FontMetrics fm = g2.getFontMetrics();
                int strW = fm.stringWidth(initial);
                int strH = fm.getAscent();
                g2.drawString(initial, x + (size - strW) / 2, y + (size + strH) / 2 - 4);
                g2.dispose();
            }
        };
        avatarPanel.setPreferredSize(new Dimension(140, 120));
        avatarPanel.setMaximumSize(new Dimension(140, 120));
        avatarPanel.setOpaque(false);
        avatarPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        nameLabel = new JLabel(this.targetDisplayName);
        nameLabel.setFont(Theme.font(Font.BOLD, 18));
        nameLabel.setForeground(Theme.TEXT);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel = new JLabel(isCaller ? "Đang gọi..." : "Cuộc gọi đến...");
        statusLabel.setFont(Theme.font(Font.PLAIN, 13));
        statusLabel.setForeground(Theme.TEXT_SECONDARY);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        visualizerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (state != State.CONNECTED) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int totalW = 5 * 12 + 4 * 6;
                int startX = (getWidth() - totalW) / 2;
                int baseY = getHeight() / 2;

                for (int i = 0; i < 5; i++) {
                    float level = eqLevels[i];
                    int barH = Math.max(4, (int) (level * 24));
                    g2.setColor(Theme.PRIMARY);
                    g2.fill(new RoundRectangle2D.Float(startX + i * 18, baseY - barH / 2, 8, barH, 4, 4));
                }
                g2.dispose();
            }
        };
        visualizerPanel.setPreferredSize(new Dimension(200, 36));
        visualizerPanel.setMaximumSize(new Dimension(200, 36));
        visualizerPanel.setOpaque(false);
        visualizerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        voiceCard.add(Box.createVerticalStrut(10));
        voiceCard.add(avatarPanel);
        voiceCard.add(Box.createVerticalStrut(14));
        voiceCard.add(nameLabel);
        voiceCard.add(Box.createVerticalStrut(6));
        voiceCard.add(statusLabel);
        voiceCard.add(Box.createVerticalStrut(16));
        voiceCard.add(visualizerPanel);

        // 2. Video View Card (Robust layout with automatic PiP positioning)
        JPanel videoCard = new JPanel(new BorderLayout());
        videoCard.setOpaque(false);
        videoCard.setBorder(new EmptyBorder(8, 16, 8, 16));

        videoViewport = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int w = getWidth();
                int h = getHeight();

                g2.setColor(new Color(0x0A, 0x0C, 0x10));
                g2.fillRect(0, 0, w, h);

                if (remoteVideoImage != null) {
                    int imgW = remoteVideoImage.getWidth();
                    int imgH = remoteVideoImage.getHeight();
                    double scale = Math.min((double) w / imgW, (double) h / imgH);
                    int drawW = (int) (imgW * scale);
                    int drawH = (int) (imgH * scale);
                    int dx = (w - drawW) / 2;
                    int dy = (h - drawH) / 2;
                    g2.drawImage(remoteVideoImage, dx, dy, drawW, drawH, null);
                } else {
                    g2.setColor(new Color(0x18, 0x1B, 0x22));
                    int size = 80;
                    int x = (w - size) / 2;
                    int y = (h - size) / 2 - 20;
                    g2.fillOval(x, y, size, size);
                    g2.setColor(Theme.BORDER);
                    g2.drawOval(x, y, size, size);

                    String init = targetDisplayName.isEmpty() ? "R" : targetDisplayName.substring(0, 1).toUpperCase();
                    g2.setFont(Theme.font(Font.BOLD, 32));
                    g2.setColor(Theme.PRIMARY);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(init, x + (size - fm.stringWidth(init)) / 2, y + (size + fm.getAscent()) / 2 - 4);

                    g2.setFont(Theme.font(Font.PLAIN, 13));
                    g2.setColor(Theme.TEXT_SECONDARY);
                    String msg = (state == State.CONNECTED) ? "Đối phương chưa bật camera" : "Đang kết nối...";
                    fm = g2.getFontMetrics();
                    g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, y + size + 28);
                }
                g2.dispose();
            }
        };
        videoViewport.setBackground(new Color(0x0A, 0x0C, 0x10));

        // Picture-in-Picture Local Video Preview (always displays local camera/screen)
        pipPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                g2.setColor(new Color(0x16, 0x1B, 0x22));
                g2.fillRoundRect(0, 0, w, h, 12, 12);

                if (localVideoImage != null && (videoStreamer == null || videoStreamer.isVideoEnabled())) {
                    g2.setClip(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
                    g2.drawImage(localVideoImage, 0, 0, w, h, null);
                    g2.setClip(null);
                } else {
                    g2.setFont(Theme.font(Font.PLAIN, 11));
                    g2.setColor(Theme.MUTED);
                    String text = "Camera tắt";
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(text, (w - fm.stringWidth(text)) / 2, (h + fm.getAscent()) / 2 - 2);
                }

                g2.setColor(Theme.BORDER_STRONG);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
                g2.dispose();
            }
        };
        pipPreview.setOpaque(false);

        // JLayeredPane with deterministic doLayout
        JLayeredPane layeredVideo = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0) return;
                videoViewport.setBounds(0, 0, w, h);
                int pw = 150;
                int ph = 105;
                pipPreview.setBounds(Math.max(10, w - pw - 14), Math.max(10, h - ph - 14), pw, ph);
            }
        };

        layeredVideo.add(videoViewport, Integer.valueOf(1));
        layeredVideo.add(pipPreview, Integer.valueOf(2));
        videoCard.add(layeredVideo, BorderLayout.CENTER);

        centerContainer.add(voiceCard, "VOICE");
        centerContainer.add(videoCard, "VIDEO");
        centerCards.show(centerContainer, isVideoMode ? "VIDEO" : "VOICE");
        add(centerContainer, BorderLayout.CENTER);

        // Control Bar
        controlBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 16));
        controlBar.setOpaque(false);
        controlBar.setBackground(Theme.APP_BACKGROUND);
        buildControls();
        add(controlBar, BorderLayout.SOUTH);

        // Timers
        durationTimer = new javax.swing.Timer(1000, e -> updateDuration());
        visualizerTimer = new javax.swing.Timer(100, e -> {
            if (state == State.CONNECTED) {
                boolean isMuted = voiceStreamer != null && voiceStreamer.isMuted();
                for (int i = 0; i < 5; i++) {
                    eqLevels[i] = isMuted ? 0.15f : (0.2f + (float) Math.random() * 0.75f);
                }
                visualizerPanel.repaint();
            }
        });
        visualizerTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                hangup();
            }
        });
    }

    private void buildControls() {
        controlBar.removeAll();

        if (state == State.INCOMING_RINGING) {
            JButton decline = roundButton(new Color(0xDA, 0x36, 0x33), new AppIcon(AppIcon.Type.CALL_END, Color.WHITE, 22), "Từ chối");
            decline.addActionListener(e -> declineCall());

            acceptButton = roundButton(new Color(0x23, 0x86, 0x36), new AppIcon(isVideoMode ? AppIcon.Type.VIDEO : AppIcon.Type.CALL, Color.WHITE, 22), "Trả lời");
            acceptButton.addActionListener(e -> acceptCall());

            controlBar.add(decline);
            controlBar.add(acceptButton);
        } else {
            // 1. Mute Mic button
            boolean isMuted = voiceStreamer != null && voiceStreamer.isMuted();
            muteButton = roundButton(
                    isMuted ? new Color(0x49, 0x1E, 0x22) : Theme.SURFACE,
                    new AppIcon(isMuted ? AppIcon.Type.MIC_OFF : AppIcon.Type.MIC, isMuted ? Theme.DANGER : Theme.TEXT, 20),
                    isMuted ? "Bật mic" : "Tắt mic"
            );
            muteButton.setBorder(new LineBorder(isMuted ? Theme.DANGER : Theme.BORDER, 1, true));
            muteButton.addActionListener(e -> toggleMute());

            // 2. Camera Toggle button
            boolean isCamOff = videoStreamer != null && !videoStreamer.isVideoEnabled();
            videoButton = roundButton(
                    isCamOff ? new Color(0x49, 0x1E, 0x22) : (isVideoMode ? new Color(0x1B, 0x27, 0x3E) : Theme.SURFACE),
                    new AppIcon(isCamOff ? AppIcon.Type.VIDEO_OFF : AppIcon.Type.VIDEO, isCamOff ? Theme.DANGER : (isVideoMode ? Theme.PRIMARY : Theme.TEXT), 20),
                    isCamOff ? "Bật camera" : "Tắt camera"
            );
            videoButton.setBorder(new LineBorder(isCamOff ? Theme.DANGER : (isVideoMode ? Theme.PRIMARY : Theme.BORDER), 1, true));
            videoButton.addActionListener(e -> toggleCamera());

            // 3. Screen Share button
            boolean isScreenSharing = videoStreamer != null && videoStreamer.getSourceMode() == VideoCallStreamer.SourceMode.SCREEN_SHARE;
            screenShareButton = roundButton(
                    isScreenSharing ? new Color(0x18, 0x36, 0x28) : Theme.SURFACE,
                    new AppIcon(AppIcon.Type.SCREEN, isScreenSharing ? Theme.SUCCESS : Theme.TEXT, 20),
                    isScreenSharing ? "Dừng chia sẻ màn hình" : "Chia sẻ màn hình"
            );
            screenShareButton.setBorder(new LineBorder(isScreenSharing ? Theme.SUCCESS : Theme.BORDER, 1, true));
            screenShareButton.addActionListener(e -> toggleScreenShare());

            // 4. End Call button
            endCallButton = roundButton(new Color(0xDA, 0x36, 0x33), new AppIcon(AppIcon.Type.CALL_END, Color.WHITE, 22), "Kết thúc");
            endCallButton.addActionListener(e -> hangup());

            controlBar.add(muteButton);
            controlBar.add(videoButton);
            controlBar.add(screenShareButton);
            controlBar.add(endCallButton);
        }

        controlBar.revalidate();
        controlBar.repaint();
    }

    private JButton roundButton(Color bg, AppIcon icon, String tooltip) {
        JButton b = new JButton(icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setPreferredSize(new Dimension(52, 52));
        b.setBackground(bg);
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static CallWindow startOutgoing(SocialWindow app, long targetUserId, String targetDisplayName, long conversationId, boolean isVideo) {
        String callId = UUID.randomUUID().toString();
        CallWindow window = new CallWindow(app, targetUserId, targetDisplayName, conversationId, callId, true, isVideo);
        window.setVisible(true);

        try {
            window.voiceStreamer = new VoiceCallStreamer();
            window.videoStreamer = new VideoCallStreamer(String.valueOf(app.currentUser.getOrDefault("displayName", "Tôi")));
            window.initVideoStreamerListeners();
            window.videoStreamer.startPreview(); // local camera preview starts immediately

            int audioPort = window.voiceStreamer.getLocalPort();
            int videoPort = window.videoStreamer.getLocalPort();

            app.query("CALL_SIGNAL", Map.of(
                    "action", "INVITE",
                    "targetUserId", targetUserId,
                    "conversationId", conversationId,
                    "udpPort", audioPort,
                    "videoPort", videoPort,
                    "isVideo", isVideo,
                    "callerName", app.currentUser.getOrDefault("displayName", "R-Connect User"),
                    "callId", callId
            ), res -> {
                if (Boolean.FALSE.equals(res.get("delivered"))) {
                    window.onCallRejected("Người dùng hiện không trực tuyến");
                } else {
                    window.statusLabel.setText("Đang đổ chuông...");
                    window.startRingtone(false);
                }
            });
        } catch (Exception ex) {
            window.onCallEnded("Không thể mở thiết bị mạng UDP");
        }

        return window;
    }

    public static CallWindow showIncoming(SocialWindow app, long callerUserId, String callerDisplayName, long conversationId, int callerAudioPort, int callerVideoPort, String callerHost, String callId, boolean isVideo) {
        CallWindow window = new CallWindow(app, callerUserId, callerDisplayName, conversationId, callId, false, isVideo);
        window.remoteHost = callerHost;
        window.remoteAudioPort = callerAudioPort;
        window.remoteVideoPort = callerVideoPort;

        try {
            window.videoStreamer = new VideoCallStreamer(String.valueOf(app.currentUser.getOrDefault("displayName", "Tôi")));
            window.initVideoStreamerListeners();
            if (isVideo) {
                window.videoStreamer.startPreview();
            }
        } catch (Exception ignored) {}

        window.setVisible(true);
        window.startRingtone(true);
        window.toFront();
        return window;
    }

    private void initVideoStreamerListeners() {
        if (videoStreamer == null) return;
        videoStreamer.onLocalFrame(frame -> SwingUtilities.invokeLater(() -> {
            this.localVideoImage = frame;
            if (pipPreview != null) pipPreview.repaint();
        }));
        videoStreamer.onRemoteFrame(frame -> SwingUtilities.invokeLater(() -> {
            this.remoteVideoImage = frame;
            if (videoViewport != null) videoViewport.repaint();
        }));
    }

    public void onAcceptedByPeer(String remoteHost, int remoteAudioPort, int remoteVideoPort) {
        stopRingtone();
        this.remoteHost = remoteHost;
        this.remoteAudioPort = remoteAudioPort;
        this.remoteVideoPort = remoteVideoPort;
        this.state = State.CONNECTED;
        this.callStartTime = System.currentTimeMillis();
        statusLabel.setText("00:00 · Đang đàm thoại P2P (UDP)");
        avatarPanel.repaint();
        buildControls();
        durationTimer.start();

        try {
            if (voiceStreamer == null) voiceStreamer = new VoiceCallStreamer();
            InetAddress addr = InetAddress.getByName(remoteHost);
            voiceStreamer.start(addr, remoteAudioPort);

            if (videoStreamer == null) {
                videoStreamer = new VideoCallStreamer(String.valueOf(app.currentUser.getOrDefault("displayName", "Tôi")));
                initVideoStreamerListeners();
            }
            if (remoteVideoPort > 0) {
                videoStreamer.start(addr, remoteVideoPort);
            }
        } catch (Exception ex) {
            statusLabel.setText("Lỗi kết nối âm thanh/hình ảnh UDP");
        }
    }

    private void acceptCall() {
        stopRingtone();
        try {
            if (this.voiceStreamer == null) this.voiceStreamer = new VoiceCallStreamer();
            if (this.videoStreamer == null) {
                this.videoStreamer = new VideoCallStreamer(String.valueOf(app.currentUser.getOrDefault("displayName", "Tôi")));
                initVideoStreamerListeners();
            }

            int audioPort = voiceStreamer.getLocalPort();
            int videoPort = videoStreamer.getLocalPort();

            app.query("CALL_SIGNAL", Map.of(
                    "action", "ACCEPT",
                    "targetUserId", targetUserId,
                    "conversationId", conversationId,
                    "udpPort", audioPort,
                    "videoPort", videoPort,
                    "callId", callId
            ), res -> {});

            this.state = State.CONNECTED;
            this.callStartTime = System.currentTimeMillis();
            statusLabel.setText("00:00 · Đang đàm thoại P2P (UDP)");
            avatarPanel.repaint();
            buildControls();
            durationTimer.start();

            if (remoteHost != null && remoteAudioPort > 0) {
                InetAddress addr = InetAddress.getByName(remoteHost);
                voiceStreamer.start(addr, remoteAudioPort);
                if (remoteVideoPort > 0) {
                    videoStreamer.start(addr, remoteVideoPort);
                }
            }
        } catch (Exception ex) {
            onCallEnded("Không thể mở thiết bị kết nối UDP");
        }
    }

    private void toggleCamera() {
        if (!isVideoMode) {
            setVideoMode(true);
        }
        if (videoStreamer != null) {
            boolean next = !videoStreamer.isVideoEnabled();
            videoStreamer.setVideoEnabled(next);
            if (next && videoStreamer.getSourceMode() == VideoCallStreamer.SourceMode.SCREEN_SHARE) {
                videoStreamer.setSourceMode(VideoCallStreamer.SourceMode.CAMERA);
            }
            videoStreamer.startPreview();
            buildControls();
            if (pipPreview != null) pipPreview.repaint();
        }
    }

    private void toggleScreenShare() {
        if (!isVideoMode) {
            setVideoMode(true);
        }
        if (videoStreamer != null) {
            if (videoStreamer.getSourceMode() == VideoCallStreamer.SourceMode.SCREEN_SHARE) {
                videoStreamer.setSourceMode(VideoCallStreamer.SourceMode.CAMERA);
            } else {
                videoStreamer.setSourceMode(VideoCallStreamer.SourceMode.SCREEN_SHARE);
                videoStreamer.setVideoEnabled(true);
            }
            videoStreamer.startPreview();
            buildControls();
            if (pipPreview != null) pipPreview.repaint();
        }
    }

    public void setVideoMode(boolean video) {
        this.isVideoMode = video;
        centerCards.show(centerContainer, video ? "VIDEO" : "VOICE");
        if (video) {
            setSize(Math.max(680, getWidth()), Math.max(520, getHeight()));
        } else {
            setSize(360, 480);
        }
        buildControls();
    }

    private void declineCall() {
        stopRingtone();
        try {
            app.query("CALL_SIGNAL", Map.of(
                    "action", "REJECT",
                    "targetUserId", targetUserId,
                    "conversationId", conversationId,
                    "callId", callId
            ), res -> {});
        } catch (Exception ignored) {}
        onCallEnded("Đã từ chối cuộc gọi");
    }

    public void onCallRejected(String reason) {
        stopRingtone();
        statusLabel.setText(reason == null ? "Người dùng từ chối cuộc gọi" : reason);
        state = State.ENDED;
        avatarPanel.repaint();
        cleanup();
        closeDelayed(1800);
    }

    public void hangup() {
        stopRingtone();
        if (state != State.ENDED) {
            try {
                app.query("CALL_SIGNAL", Map.of(
                        "action", "HANGUP",
                        "targetUserId", targetUserId,
                        "conversationId", conversationId,
                        "callId", callId
                ), res -> {});
            } catch (Exception ignored) {}
        }
        onCallEnded("Cuộc gọi đã kết thúc");
    }

    public void onCallEnded(String reason) {
        stopRingtone();
        state = State.ENDED;
        statusLabel.setText(reason == null ? "Cuộc gọi đã kết thúc" : reason);
        avatarPanel.repaint();
        cleanup();
        closeDelayed(1500);
    }

    private void toggleMute() {
        if (voiceStreamer != null) {
            boolean nextMute = !voiceStreamer.isMuted();
            voiceStreamer.setMuted(nextMute);
            buildControls();
        }
    }

    private void updateDuration() {
        if (state == State.CONNECTED && callStartTime > 0) {
            long sec = (System.currentTimeMillis() - callStartTime) / 1000;
            long m = sec / 60;
            long s = sec % 60;
            statusLabel.setText(String.format("%02d:%02d · Đang đàm thoại P2P (UDP)", m, s));
        }
    }

    private void cleanup() {
        if (durationTimer != null) durationTimer.stop();
        if (visualizerTimer != null) visualizerTimer.stop();
        if (voiceStreamer != null) {
            voiceStreamer.stop();
            voiceStreamer = null;
        }
        if (videoStreamer != null) {
            videoStreamer.stop();
            videoStreamer = null;
        }
    }

    private void closeDelayed(int ms) {
        javax.swing.Timer t = new javax.swing.Timer(ms, e -> dispose());
        t.setRepeats(false);
        t.start();
    }

    public String getCallId() {
        return callId;
    }

    public long getTargetUserId() {
        return targetUserId;
    }

    private void startRingtone(boolean isIncoming) {
        if (ringtoneRunning.getAndSet(true)) return;
        ringtoneThread = new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(8000f, 8, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format, 1024);
                line.start();

                byte[] buffer = new byte[800];
                int phase = 0;
                while (ringtoneRunning.get()) {
                    if (isIncoming) {
                        int[] freqs = new int[]{523, 659, 784};
                        for (int f : freqs) {
                            for (int i = 0; i < 1200 && ringtoneRunning.get(); i += buffer.length) {
                                for (int j = 0; j < buffer.length; j++) {
                                    double angle = 2.0 * Math.PI * phase * f / 8000.0;
                                    buffer[j] = (byte) (Math.sin(angle) * 70);
                                    phase++;
                                }
                                line.write(buffer, 0, buffer.length);
                            }
                        }
                        for (int i = 0; i < 12000 && ringtoneRunning.get(); i += buffer.length) {
                            Arrays.fill(buffer, (byte) 0);
                            line.write(buffer, 0, buffer.length);
                        }
                    } else {
                        for (int i = 0; i < 8000 && ringtoneRunning.get(); i += buffer.length) {
                            for (int j = 0; j < buffer.length; j++) {
                                double a1 = 2.0 * Math.PI * phase * 440 / 8000.0;
                                double a2 = 2.0 * Math.PI * phase * 480 / 8000.0;
                                buffer[j] = (byte) ((Math.sin(a1) + Math.sin(a2)) * 35);
                                phase++;
                            }
                            line.write(buffer, 0, buffer.length);
                        }
                        for (int i = 0; i < 16000 && ringtoneRunning.get(); i += buffer.length) {
                            Arrays.fill(buffer, (byte) 0);
                            line.write(buffer, 0, buffer.length);
                        }
                    }
                }
                line.drain();
                line.stop();
                line.close();
            } catch (Exception ignored) {}
        }, "RingtoneThread");
        ringtoneThread.setDaemon(true);
        ringtoneThread.start();
    }

    private void stopRingtone() {
        ringtoneRunning.set(false);
        if (ringtoneThread != null) {
            ringtoneThread.interrupt();
            ringtoneThread = null;
        }
    }
}
