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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.prefs.Preferences;
import static com.p2p.client.ui.social.Values.*;
import com.p2p.client.ui.social.SocialWindow.Page;

/** ChatView screen. Extracted from SocialWindow; behaviour is unchanged. */
final class ChatView extends ViewSupport {
    private final AudioRecorder audioRecorder = new AudioRecorder();
    private final Map<String, Path> voiceCache = new ConcurrentHashMap<>();
    private javax.swing.Timer recordTimer;
    private int recordSeconds = 0;

    ChatView(SocialWindow app) { super(app); }

    void openDirect(long target) {
        mutate("CONVERSATION_DIRECT_GET_OR_CREATE", of("targetUserId", target), null, r -> {
            Map<String, Object> conv = map(r.get("conversation"));
            if (conv.isEmpty()) conv = new LinkedHashMap<>(r);
            conv.putIfAbsent("title", "Trò chuyện với người dùng #" + target);
            conv.putIfAbsent("kind", "DIRECT");
            conv.putIfAbsent("otherUserId", target);
            openChat(conv);
        });
    }

    void openChat(Map<String, Object> conv) {
        long cid = id(conv, "conversation");
        if (cid == 0) {
            showError(new IllegalArgumentException("Máy chủ chưa trả mã conversationId"));
            return;
        }

        boolean isGroup = "GROUP".equals(str(conv, "type", "kind"));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);

        // Header
        List<Component> headerActions = new ArrayList<>();
        headerActions.add(ghost("Tin cũ hơn", () -> loadHistory(true)));
        if (isGroup) headerActions.add(button("Thành viên", new AppIcon(AppIcon.Type.PEOPLE, Theme.TEXT, 15), () -> groupInfo(conv)));
        headerActions.add(ghost("Tệp đang tải", () -> app.media.resumeUploads(app)));
        if (!isGroup) {
            JButton voiceCallBtn = ghost("Gọi thoại", () -> app.startCallWithCurrentPeer(false));
            voiceCallBtn.setIcon(new AppIcon(AppIcon.Type.CALL, Theme.TEXT_SECONDARY, 15));
            voiceCallBtn.setIconTextGap(6);
            headerActions.add(voiceCallBtn);

            JButton videoCallBtn = ghost("Gọi video", () -> app.startCallWithCurrentPeer(true));
            videoCallBtn.setIcon(new AppIcon(AppIcon.Type.VIDEO, Theme.PRIMARY, 15));
            videoCallBtn.setIconTextGap(6);
            headerActions.add(videoCallBtn);
        }
        JPanel heading = titled(name(conv), headerActions.toArray(Component[]::new));
        heading.setBorder(new EmptyBorder(16, 24, 12, 24));

        // Subheader badges
        JPanel subheader = new JPanel(new BorderLayout());
        subheader.setOpaque(false);
        JLabel badgeMode = isGroup
                ? ModernUI.chip("GỬI CHO CẢ NHÓM", ModernUI.Tone.SUCCESS)
                : ModernUI.chip("TIN NHẮN RIÊNG", ModernUI.Tone.ACCENT);
        app.typingLabel = label(" ", false);
        app.typingLabel.setFont(Theme.font(Font.ITALIC, 12));
        app.typingLabel.setForeground(Theme.INFO);

        subheader.add(badgeMode, BorderLayout.WEST);
        subheader.add(app.typingLabel, BorderLayout.EAST);
        heading.add(subheader, BorderLayout.SOUTH);
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(Theme.APP_BACKGROUND);
        headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        headerBar.add(centered(heading, SocialWindow.CHAT_WIDTH + 48));
        panel.add(headerBar, BorderLayout.NORTH);

        app.chatMessages = new StackPanel(true);
        app.chatMessages.setBorder(new EmptyBorder(16, 24, 16, 24));
        app.chatScroll = scroll(app.chatMessages);
        panel.add(app.chatScroll, BorderLayout.CENTER);

        app.chatInput = text("", 3);
        JButton send = Theme.primaryButton("Gửi");
        send.setIcon(new AppIcon(AppIcon.Type.SEND, Theme.ON_PRIMARY, 15));
        send.setIconTextGap(8);
        send.addActionListener(e -> sendMessage(cid, null, app.chatInput.getText()));

        JButton attach = ghost("Đính kèm", () -> {
            JFileChooser c = new JFileChooser();
            if (c.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
            String caption = app.chatInput.getText();
            String filename = c.getSelectedFile().getName();
            app.media.upload(app, c.getSelectedFile().toPath(), "CHAT_FILE", mid -> sendMessage(cid, mid, caption.isBlank() ? filename : caption));
        });
        attach.setIcon(new AppIcon(AppIcon.Type.ATTACHMENT, Theme.TEXT_SECONDARY, 15));
        attach.setIconTextGap(8);

        JButton emoji = ghost("Biểu cảm", null);
        emoji.setIcon(new AppIcon(AppIcon.Type.SMILE, Theme.TEXT_SECONDARY, 15));
        emoji.setIconTextGap(8);
        emoji.addActionListener(e -> EmojiPicker.show(emoji, text -> {
            if (app.chatInput.getSelectedText() != null) {
                app.chatInput.replaceSelection(text);
            } else {
                int pos = app.chatInput.getCaretPosition();
                try {
                    app.chatInput.getDocument().insertString(pos, text, null);
                    app.chatInput.setCaretPosition(pos + text.length());
                } catch (Exception ex) {
                    app.chatInput.append(text);
                }
            }
            app.chatInput.requestFocusInWindow();
        }));

        JButton voice = ghost("Ghi âm", null);
        voice.setIcon(new AppIcon(AppIcon.Type.MIC, Theme.TEXT_SECONDARY, 15));
        voice.setIconTextGap(8);

        JButton cancelVoice = ghost("Hủy", null);
        cancelVoice.setIcon(new AppIcon(AppIcon.Type.CLOSE, Theme.TEXT_SECONDARY, 13));
        cancelVoice.setIconTextGap(6);
        cancelVoice.setVisible(false);

        cancelVoice.addActionListener(e -> {
            if (audioRecorder.isRecording()) {
                audioRecorder.cancel();
                if (recordTimer != null) recordTimer.stop();
                recordSeconds = 0;
                voice.setText("Ghi âm");
                voice.setIcon(new AppIcon(AppIcon.Type.MIC, Theme.TEXT_SECONDARY, 15));
                voice.setForeground(Theme.TEXT_SECONDARY);
                cancelVoice.setVisible(false);
            }
        });

        voice.addActionListener(e -> {
            if (!audioRecorder.isRecording()) {
                try {
                    audioRecorder.start();
                    recordSeconds = 0;
                    voice.setText("00:00 · Dừng");
                    voice.setIcon(new AppIcon(AppIcon.Type.STOP, Theme.DANGER, 15));
                    voice.setForeground(Theme.DANGER);
                    cancelVoice.setVisible(true);

                    if (recordTimer != null) recordTimer.stop();
                    recordTimer = new javax.swing.Timer(1000, evt -> {
                        recordSeconds++;
                        int m = recordSeconds / 60;
                        int s = recordSeconds % 60;
                        voice.setText(String.format("%02d:%02d · Dừng", m, s));
                        if (recordSeconds >= 300) {
                            voice.doClick();
                        }
                    });
                    recordTimer.start();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(app, "Không thể bắt đầu thu âm: " + ex.getMessage(), "Lỗi Microphone", JOptionPane.WARNING_MESSAGE);
                }
            } else {
                if (recordTimer != null) recordTimer.stop();
                voice.setText("Ghi âm");
                voice.setIcon(new AppIcon(AppIcon.Type.MIC, Theme.TEXT_SECONDARY, 15));
                voice.setForeground(Theme.TEXT_SECONDARY);
                cancelVoice.setVisible(false);
                try {
                    AudioRecorder.RecordResult res = audioRecorder.stop();
                    if (res != null && res.file().exists() && res.file().length() > 0) {
                        String caption = "Tin nhắn thoại (" + res.durationSeconds() + "s)";
                        app.media.upload(app, res.file().toPath(), "CHAT_FILE", mid -> sendMessage(cid, mid, caption));
                    }
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        });

        app.chatInput.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send-message");
        app.chatInput.getActionMap().put("send-message", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { send.doClick(); }
        });
        app.chatInput.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break");

        app.chatInput.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn · Enter để gửi, Shift+Enter xuống dòng");
        JPanel composerInner = new JPanel(new BorderLayout(0, 8));
        composerInner.setOpaque(false);
        composerInner.setBorder(new EmptyBorder(12, 24, 14, 24));
        JScrollPane inputFrame = framed(app.chatInput);
        inputFrame.setPreferredSize(new Dimension(100, 84));
        composerInner.add(inputFrame, BorderLayout.CENTER);
        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftActions.setOpaque(false);
        leftActions.add(attach);
        leftActions.add(emoji);
        leftActions.add(voice);
        leftActions.add(cancelVoice);

        JPanel composerActions = new JPanel(new BorderLayout());
        composerActions.setOpaque(false);
        composerActions.add(leftActions, BorderLayout.WEST);
        composerActions.add(send, BorderLayout.EAST);
        composerInner.add(composerActions, BorderLayout.SOUTH);
        JPanel composer = new JPanel(new BorderLayout());
        composer.setBackground(Theme.APP_BACKGROUND);
        composer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        composer.add(centered(composerInner, SocialWindow.CHAT_WIDTH + 48));

        if (Boolean.FALSE.equals(conv.get("canSend")) || Boolean.FALSE.equals(conv.get("active"))) {
            app.chatInput.setEnabled(false);
            send.setEnabled(false);
            attach.setEnabled(false);
            emoji.setEnabled(false);
            voice.setEnabled(false);
            app.typingLabel.setText("Chỉ đọc lịch sử · Bạn không còn quyền gửi tin mới trong nhóm này");
        }
        panel.add(composer, BorderLayout.SOUTH);

        app.selectedConvId = cid;
        setView("chat", messagesShell(panel), () -> syncChat(cid));
        setContext(chatContext(conv));
        if (app.convPage != null) app.convPage.load(true);
        scheduleConversationRefresh();
        app.activeConversation = cid;
        app.activeConversationData = new LinkedHashMap<>(conv);
        app.earliestSeq = 0;
        app.latestSeq = 0;
        loadHistory(false);
    }

    void loadHistory(boolean older) {
        long cid = app.activeConversation;
        if (cid == 0) return;
        Map<String, Object> p = of("conversationId", cid, "limit", 50);
        if (older && app.earliestSeq > 0) p.put("beforeSeq", app.earliestSeq);
        query("MESSAGE_HISTORY", p, r -> {
            if (app.activeConversation != cid) return;
            for (Map<String, Object> m : rows(r.get("items"))) upsertMessage(m);
            app.latestSeq = Math.max(app.latestSeq, num(r, "latestSeq"));
            renderMessages(!older);
            ackMessages();
        });
    }

    void syncChat(long cid) {
        if (cid != app.activeConversation) return;
        query("MESSAGE_HISTORY", of("conversationId", cid, "afterSeq", app.latestSeq, "limit", 100), r -> {
            if (cid != app.activeConversation) return;
            for (Map<String, Object> m : rows(r.get("items"))) upsertMessage(m);
            renderMessages(false);
            ackMessages();
        });
    }

    void upsertMessage(Map<String, Object> m) {
        scheduleConversationRefresh();
        long seq = num(m, "conversationSeq", "seq"), mid = id(m, "message");
        if (mid == 0) mid = seq;
        if (mid == 0) return;
        Map<String, Object> merged = new LinkedHashMap<>(app.messages.getOrDefault(mid, Map.of()));
        String previousStatus = str(merged, "status");
        merged.putAll(m);
        if (statusRank(previousStatus) > statusRank(str(merged, "status"))) merged.put("status", previousStatus);
        app.messages.put(mid, merged);
        if (app.earliestSeq == 0 || seq < app.earliestSeq) app.earliestSeq = seq;
        app.latestSeq = Math.max(app.latestSeq, seq);
        String clientId = str(m, "clientMessageId");
        app.pendingMessages.remove(clientId);
        while (app.messages.size() > 200) app.messages.remove(app.messages.keySet().iterator().next());
    }

    int statusRank(String s) {
        return switch (s) {
            case "READ" -> 3;
            case "DELIVERED" -> 2;
            case "STORED" -> 1;
            default -> 0;
        };
    }

    void ackMessages() {
        if (!app.authenticated || app.latestSeq <= 0 || app.activeConversation <= 0) return;
        long cid = app.activeConversation;
        long seq = app.messages.values().stream().mapToLong(m -> num(m, "conversationSeq", "seq")).max().orElse(0);
        if (seq == 0) return;
        mutate("MESSAGE_DELIVERED", of("conversationId", cid, "throughSeq", seq), null, r -> {});
        if (isActive()) mutate("MESSAGE_READ", of("conversationId", cid, "throughSeq", seq), null, r -> {});
    }

    String translationKey(Map<String, Object> message) {
        long messageId = id(message, "message");
        return messageId == 0 ? "" : messageId + ":" + app.displayLanguage;
    }

    void requestTranslation(Map<String, Object> message) {
        String key = translationKey(message);
        if (key.isBlank() || app.translationPending.contains(key) || app.translations.containsKey(key)) return;
        String content = str(message, "content");
        if (content.isBlank()) return;
        SocialClient expected = app.client;
        if (!app.authenticated || expected == null) return;
        app.translationPending.add(key);
        long messageId = id(message, "message");
        String target = app.displayLanguage;
        CompletableFuture.supplyAsync(() -> blocking(expected, "MESSAGE_TRANSLATE",
                of("messageId", messageId, "targetLanguage", target), null), app.workers).whenComplete((result, error) -> invoke(() -> {
            app.translationPending.remove(key);
            if (app.client != expected || !app.authenticated) return;
            if (error == null) {
                app.translations.put(key, new LinkedHashMap<>(result));
                if (app.activeConversation > 0) renderMessages(false);
            }
        }));
    }

    JComponent translatedMessage(Map<String, Object> message) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String original = str(message, "content");
        String key = translationKey(message);
        Map<String, Object> translation = key.isBlank()
                ? Map.of()
                : app.translations.getOrDefault(key, Map.of());
        boolean showOriginal = app.originalMessages.contains(key);
        String shown = original;
        if (!translation.isEmpty() && bool(translation, "translated") && !showOriginal) {
            shown = str(translation, "translatedContent");
        }

        JTextArea contentArea = readableMessage(shown);
        contentArea.setForeground(Theme.TEXT);
        contentArea.setFont(Theme.messageFont(Font.PLAIN, 13));
        contentArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(contentArea);

        if (!translation.isEmpty() && bool(translation, "translated")) {
            JButton toggle = button(showOriginal ? "Xem bản dịch" : "Xem bản gốc", () -> {
                if (showOriginal) app.originalMessages.remove(key);
                else app.originalMessages.add(key);
                renderMessages(false);
            });
            toggle.setFont(Theme.font(Font.PLAIN, 11));
            toggle.setMaximumSize(new Dimension(150, 26));
            toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(Box.createVerticalStrut(3));
            panel.add(toggle);
        } else if (app.translationPending.contains(key)) {
            JLabel pending = label("Đang dịch…", false);
            pending.setFont(Theme.font(Font.PLAIN, 11));
            pending.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(pending);
            requestTranslation(message);
        } else if (!key.isBlank()) {
            requestTranslation(message);
        }
        return panel;
    }

    /** Staggered Messenger/Telegram-like left-and-right bubble rows. */
    void renderMessages(boolean bottom) {
        if (app.chatMessages == null) return;
        JScrollBar bar = app.chatScroll.getVerticalScrollBar();
        int old = bar.getValue(), height = bar.getMaximum();
        boolean nearBottom = old + bar.getVisibleAmount() >= height - 40;

        app.chatMessages.removeAll();
        List<Map<String, Object>> sorted = new ArrayList<>(app.messages.values());
        sorted.sort(Comparator.comparingLong(m -> num(m, "conversationSeq", "seq")));

        boolean isGroup = "GROUP".equals(str(app.activeConversationData, "kind", "type"));

        for (Map<String, Object> m : sorted) {
            boolean isOwn = num(m, "senderId") == id(app.currentUser, "user");

            // Row wrapper spanning full container width
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Bubble container (staggered right for own, left for other)
            JPanel bubbleWrapper = new JPanel();
            bubbleWrapper.setLayout(new BoxLayout(bubbleWrapper, BoxLayout.Y_AXIS));
            bubbleWrapper.setOpaque(false);

            // Sender name on top of received bubble in Group chat
            if (!isOwn && isGroup) {
                String sender = str(m, "senderName", "displayName");
                if (sender.isBlank()) sender = "Thành viên #" + num(m, "senderId");
                JLabel senderLbl = new JLabel(sender);
                senderLbl.setFont(Theme.font(Font.BOLD, 12));
                senderLbl.setForeground(Theme.PRIMARY_LIGHT);
                senderLbl.setBorder(new EmptyBorder(0, 4, 3, 4));
                senderLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubbleWrapper.add(senderLbl);
            }

            // Bubble card with distinct styling
            Color bubbleColor = isOwn ? Theme.BUBBLE_OWN : Theme.BUBBLE_OTHER;
            Theme.RoundedPanel bubbleBox = (Theme.RoundedPanel) Theme.roundedPanel(bubbleColor, 14);
            bubbleBox.setStroke(isOwn ? Theme.BUBBLE_OWN_BORDER : Theme.CARD_BORDER);
            bubbleBox.setLayout(new BoxLayout(bubbleBox, BoxLayout.Y_AXIS));
            bubbleBox.setBorder(new EmptyBorder(9, 13, 9, 13));

            // Message text stays a JTextArea and adds a small original/translation toggle.
            bubbleBox.add(translatedMessage(m));

            // Attachment download button
            Map<String, Object> attachment = map(m.get("media"));
            if (attachment.isEmpty()) attachment = map(m.get("attachment"));
            String mid = str(m, "mediaId");
            if (mid.isBlank()) mid = str(attachment, "mediaId", "id");
            if (!mid.isBlank()) {
                String mediaId = mid;
                String fileName = str(attachment, "fileName", "originalName");
                if (fileName.isBlank()) fileName = str(m, "fileName");
                String suggestedName = fileName;
                // Voice playback support
                String contentStr = str(m, "content");
                boolean isAudio = fileName.toLowerCase().endsWith(".wav")
                        || fileName.toLowerCase().startsWith("rconnect_voice_")
                        || contentStr.contains("Tin nhắn thoại");

                if (isAudio) {
                    JButton voicePlay = new JButton("Phát tin nhắn thoại");
                    voicePlay.setFont(Theme.font(Font.BOLD, 12));
                    voicePlay.setForeground(Theme.PRIMARY);
                    voicePlay.setBackground(Theme.SURFACE);
                    voicePlay.setBorder(BorderFactory.createCompoundBorder(new LineBorder(Theme.BORDER, 1), new EmptyBorder(6, 12, 6, 12)));
                    voicePlay.setFocusPainted(false);
                    voicePlay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    voicePlay.setIcon(new AppIcon(AppIcon.Type.PLAY, Theme.PRIMARY, 14));
                    voicePlay.setIconTextGap(8);
                    voicePlay.setAlignmentX(Component.LEFT_ALIGNMENT);

                    Runnable updateState = () -> {
                        Path cached = voiceCache.get(mediaId);
                        if (cached != null && AudioPlayer.isPlaying(cached.toFile())) {
                            voicePlay.setText("Tạm dừng");
                            voicePlay.setIcon(new AppIcon(AppIcon.Type.PAUSE, Theme.PRIMARY, 14));
                        } else {
                            voicePlay.setText("Phát tin nhắn thoại");
                            voicePlay.setIcon(new AppIcon(AppIcon.Type.PLAY, Theme.PRIMARY, 14));
                        }
                    };

                    voicePlay.addActionListener(e -> {
                        Path cached = voiceCache.get(mediaId);
                        if (cached != null && Files.exists(cached)) {
                            AudioPlayer.togglePlay(cached.toFile(), updateState);
                            updateState.run();
                        } else {
                            voicePlay.setText("Đang tải...");
                            voicePlay.setEnabled(false);
                            app.media.fetchMedia(app, mediaId, ".wav", fetchedPath -> {
                                voiceCache.put(mediaId, fetchedPath);
                                voicePlay.setEnabled(true);
                                AudioPlayer.togglePlay(fetchedPath.toFile(), updateState);
                                updateState.run();
                            });
                        }
                    });

                    bubbleBox.add(Box.createVerticalStrut(6));
                    bubbleBox.add(voicePlay);
                }

                // Button text must retain fileName for unit test assertions
                JButton dlBtn = button("Tải tệp · " + (fileName.isBlank() ? "#" + mid : fileName), () -> app.media.download(app, mediaId, suggestedName));
                dlBtn.setIcon(new AppIcon(AppIcon.Type.DOWNLOAD, Theme.TEXT, 14));
                dlBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubbleBox.add(Box.createVerticalStrut(6));
                bubbleBox.add(dlBtn);
            }

            bubbleBox.setAlignmentX(isOwn ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
            bubbleWrapper.add(bubbleBox);

            // Sub-label with timestamp and receipt status
            String receipt = isGroup
                    ? "Đã lưu · " + num(m, "deliveredByCount") + " đã nhận / " + num(m, "readByCount") + " đã đọc"
                    : messageStatus(str(m, "status"));
            String metaText = time(m) + (isOwn ? " · " + receipt : "");
            JLabel metaLabel = new JLabel(metaText);
            metaLabel.setFont(Theme.font(Font.PLAIN, 11));
            metaLabel.setForeground(Theme.MUTED);
            metaLabel.setBorder(new EmptyBorder(2, 4, 0, 4));
            metaLabel.setAlignmentX(isOwn ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
            bubbleWrapper.add(metaLabel);

            if (isOwn) {
                // Sent message: right aligned, staggered with left gap
                row.add(bubbleWrapper, BorderLayout.EAST);
                row.add(Box.createHorizontalStrut(32), BorderLayout.WEST);
            } else {
                // Received message: left aligned with sender avatar, staggered with right gap
                JPanel leftBox = new JPanel(new BorderLayout(8, 0));
                leftBox.setOpaque(false);
                Map<String, Object> senderMap = of("displayName", str(m, "senderName", "displayName"), "userId", num(m, "senderId"));
                JComponent senderAvatar = avatar(senderMap, 30);
                JPanel avatarAlign = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                avatarAlign.setOpaque(false);
                avatarAlign.add(senderAvatar);

                leftBox.add(avatarAlign, BorderLayout.WEST);
                leftBox.add(bubbleWrapper, BorderLayout.CENTER);

                row.add(leftBox, BorderLayout.WEST);
                row.add(Box.createHorizontalStrut(32), BorderLayout.EAST);
            }

            row.setMaximumSize(new Dimension(SocialWindow.CHAT_WIDTH, row.getPreferredSize().height));
            app.chatMessages.add(row);
            app.chatMessages.add(Box.createVerticalStrut(8));
        }

        // Pending messages (right-aligned)
        for (PendingMessage pending : app.pendingMessages.values()) {
            if (pending.conversationId == app.activeConversation) {
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);

                JPanel bubbleWrapper = new JPanel();
                bubbleWrapper.setLayout(new BoxLayout(bubbleWrapper, BoxLayout.Y_AXIS));
                bubbleWrapper.setOpaque(false);

                Theme.RoundedPanel bubbleBox = (Theme.RoundedPanel) Theme.roundedPanel(Theme.SURFACE, 14);
                bubbleBox.setStroke(Theme.WARNING);
                bubbleBox.setLayout(new BoxLayout(bubbleBox, BoxLayout.Y_AXIS));
                bubbleBox.setBorder(new EmptyBorder(9, 13, 9, 13));

                JTextArea contentArea = readableMessage(pending.content);
                contentArea.setForeground(Theme.TEXT);
                contentArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubbleBox.add(contentArea);

                if (!"Đang gửi...".equals(pending.status) && !"Đang gửi…".equals(pending.status)) {
                    JButton retryBtn = primary("Thử lại cùng mã tin nhắn", new AppIcon(AppIcon.Type.REFRESH, Theme.ON_PRIMARY, 14), () -> retryMessage(pending));
                    retryBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                    bubbleBox.add(Box.createVerticalStrut(6));
                    bubbleBox.add(retryBtn);
                }

                bubbleBox.setAlignmentX(Component.RIGHT_ALIGNMENT);
                bubbleWrapper.add(bubbleBox);

                JLabel statusLabel = new JLabel("Bạn · " + pending.status);
                statusLabel.setFont(Theme.font(Font.PLAIN, 11));
                statusLabel.setForeground(Theme.WARNING);
                statusLabel.setBorder(new EmptyBorder(2, 4, 0, 4));
                statusLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
                bubbleWrapper.add(statusLabel);

                row.add(bubbleWrapper, BorderLayout.EAST);
                row.add(Box.createHorizontalStrut(32), BorderLayout.WEST);
                row.setMaximumSize(new Dimension(SocialWindow.CHAT_WIDTH, row.getPreferredSize().height));

                app.chatMessages.add(row);
                app.chatMessages.add(Box.createVerticalStrut(8));
            }
        }

        if (app.chatMessages.getComponentCount() == 0) {
            app.chatMessages.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.CHAT, Theme.MUTED, 36), "Chưa có tin nhắn nào", "Hãy gửi lời chào đầu tiên để bắt đầu cuộc trò chuyện!"));
        }
        app.chatMessages.revalidate();
        app.chatMessages.repaint();

        invoke(() -> {
            if (bottom || nearBottom) bar.setValue(bar.getMaximum());
            else bar.setValue(old + Math.max(0, bar.getMaximum() - height));
        });
    }

    String messageStatus(String status) {
        return switch (status) {
            case "READ" -> "Đã đọc";
            case "DELIVERED" -> "Đã nhận";
            case "STORED" -> "Đã lưu trên máy chủ";
            default -> status.isBlank() ? "Đã lưu trên máy chủ" : status;
        };
    }

    void sendMessage(long cid, String mediaId, String content) {
        if (content.length() > 4000) {
            warn("Tin nhắn tối đa 4.000 ký tự.");
            return;
        }
        if (content.isBlank() && mediaId == null) return;
        if (!app.authenticated) {
            warn("Vui lòng đăng nhập lại để gửi tin.");
            return;
        }
        if (app.pendingMessages.size() >= 50) {
            warn("Có 50 tin chưa xác định kết quả. Vui lòng tra cứu hoặc thử lại các tin đó trước khi gửi thêm.");
            return;
        }
        String stableId = UUID.randomUUID().toString();
        PendingMessage p = new PendingMessage(cid, stableId, UUID.randomUUID().toString(), content, mediaId);
        app.pendingMessages.put(stableId, p);
        if (app.activeConversation == cid) {
            app.chatInput.setText("");
            renderMessages(true);
        }
        submitMessage(p);
    }

    void submitMessage(PendingMessage p) {
        p.status = "Đang gửi…";
        SocialClient requestedClient = app.client;
        CompletableFuture.supplyAsync(() -> blocking(requestedClient, "MESSAGE_SEND",
                of("conversationId", p.conversationId, "clientMessageId", p.clientId, "content", p.content, "mediaId", p.mediaId), p.key), app.workers).whenComplete((ack, error) -> invoke(() -> {
            if (app.client != requestedClient) return;
            if (error != null) {
                p.status = (unwrap(error) instanceof RemoteException ? "Không gửi được · " : "Chưa xác định kết quả · ") + friendly(error);
                if (app.activeConversation == p.conversationId) renderMessages(false);
                return;
            }
            Map<String, Object> msg = map(ack.get("message"));
            if (msg.isEmpty()) msg = new LinkedHashMap<>(ack);
            msg.putIfAbsent("content", p.content);
            msg.putIfAbsent("senderId", id(app.currentUser, "user"));
            msg.putIfAbsent("conversationId", p.conversationId);
            msg.putIfAbsent("clientMessageId", p.clientId);
            if (p.mediaId != null) msg.putIfAbsent("mediaId", p.mediaId);
            msg.putIfAbsent("status", "STORED");
            app.pendingMessages.remove(p.clientId);
            if (app.activeConversation == p.conversationId) {
                upsertMessage(msg);
                renderMessages(false);
            }
        }));
    }

    void retryMessage(PendingMessage p) {
        SocialClient requestedClient = app.client;
        CompletableFuture.supplyAsync(() -> blocking(requestedClient, "MESSAGE_LOOKUP", of("clientMessageId", p.clientId), null), app.workers).whenComplete((found, error) -> invoke(() -> {
            if (app.client != requestedClient) return;
            if (error == null) {
                Map<String, Object> m = map(found.get("message"));
                if (m.isEmpty()) m = found;
                app.pendingMessages.remove(p.clientId);
                if (app.activeConversation == p.conversationId) {
                    upsertMessage(m);
                    renderMessages(false);
                }
            } else if (unwrap(error) instanceof RemoteException r && "NOT_FOUND".equals(r.getCode())) {
                submitMessage(p);
            } else {
                p.status = "Tra cứu thất bại · " + friendly(error);
                renderMessages(false);
            }
        }));
    }
}
