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
import com.p2p.client.ui.social.SocialWindow.Page;

/** AuthView screen. Extracted from SocialWindow; behaviour is unchanged. */
final class AuthView extends ViewSupport {
    AuthView(SocialWindow app) { super(app); }

    // ================= AUTH SCREEN =================
    void showAuth(boolean register) {
        app.reconnectToken++;
        app.authenticated = false;
        app.generation++;
        app.activeConversation = 0;
        app.messages.clear();
        app.translations.clear();
        app.translationPending.clear();
        app.originalMessages.clear();
        app.currentUser.clear();
        app.thumbnails.bind(null);
        app.countRefresh.stop();
        app.udpRefresh.stop();
        for (Window window : getOwnedWindows()) window.dispose();
        app.root.removeAll();

        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(Theme.APP_BACKGROUND);
        center.setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel panel = card();
        panel.putClientProperty("CardPanel.fillWidth", false);
        panel.setBorder(new EmptyBorder(28, 32, 24, 32));
        panel.setMinimumSize(new Dimension(380, 300));

        JPanel fields = vertical();

        JLabel titleLbl = new JLabel(register ? "Tạo tài khoản" : "Chào mừng trở lại");
        titleLbl.setFont(Theme.font(Font.BOLD, 24));
        titleLbl.setForeground(Theme.TEXT);
        fields.add(titleLbl);
        fields.add(Box.createVerticalStrut(4));
        JLabel subtitleLbl = label(register ? "Tham gia mạng giao tiếp an toàn R-Connect." : "Đăng nhập để tiếp tục cuộc trò chuyện.", false);
        subtitleLbl.setForeground(Theme.TEXT_SECONDARY);
        fields.add(subtitleLbl);
        fields.add(Box.createVerticalStrut(20));

        // Inline validation / app.connection feedback (replaces blocking pop-ups on app screen)
        JLabel errorLabel = new JLabel();
        errorLabel.setFont(Theme.font(Font.PLAIN, 13));
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setIcon(new AppIcon(AppIcon.Type.SHIELD, Theme.DANGER, 15));
        errorLabel.setIconTextGap(8);
        JPanel errorBox = new Theme.RoundedPanel(Theme.DANGER_SOFT, Theme.RADIUS_MD);
        ((Theme.RoundedPanel) errorBox).setStroke(Theme.hex(0x4A2A2D));
        errorBox.setLayout(new BorderLayout());
        errorBox.setBorder(new EmptyBorder(9, 12, 9, 12));
        errorBox.add(errorLabel);
        errorBox.setVisible(false);
        errorBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel[] authParts = new JPanel[2];
        java.util.function.Consumer<String> fail = text -> {
            boolean show = text != null && !text.isBlank();
            errorLabel.setText(show ? "<html><body style='width:250px'>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>" : "");
            errorBox.setVisible(show);
            if (authParts[0] != null) {
                authParts[1].invalidate();
                authParts[1].setSize(authParts[0].getWidth() > 0 ? authParts[0].getWidth() - 64 : 380, authParts[1].getHeight());
                authParts[1].doLayout();
                authParts[0].setPreferredSize(new Dimension(444, authParts[1].getPreferredSize().height
                        + ((JPanel) authParts[0].getClientProperty("fields")).getPreferredSize().height + 70));
                authParts[0].revalidate();
                authParts[0].getParent().revalidate();
            }
        };

        // Server endpoint (real app.connection settings; kept at the bottom of the card)
        JTextField hostField = new JTextField(app.host);
        JTextField portField = new JTextField(String.valueOf(app.port));
        hostField.setName("host");
        portField.setName("port");
        styleAuthInput(hostField);
        styleAuthInput(portField);
        portField.setPreferredSize(new Dimension(84, 38));
        JPanel serverRow = new JPanel(new BorderLayout(10, 0));
        serverRow.setOpaque(false);
        JPanel hostCol = new JPanel(new BorderLayout(0, 4));
        hostCol.setOpaque(false);
        hostCol.add(smallLabel("Máy chủ"), BorderLayout.NORTH);
        hostCol.add(hostField);
        JPanel portCol = new JPanel(new BorderLayout(0, 4));
        portCol.setOpaque(false);
        portCol.add(smallLabel("Cổng TCP"), BorderLayout.NORTH);
        portCol.add(portField);
        serverRow.add(hostCol, BorderLayout.CENTER);
        serverRow.add(portCol, BorderLayout.EAST);

        // Account Inputs
        JTextField user = new JTextField();
        JTextField display = new JTextField();
        user.setName("user");
        display.setName("display");
        JPasswordField password = new JPasswordField();
        JPasswordField confirmPassword = new JPasswordField();
        styleAuthInput(user);
        styleAuthInput(display);
        styleAuthInput(password);
        styleAuthInput(confirmPassword);

        if (register) {
            JPanel names = new JPanel(new GridLayout(1, 2, 12, 0));
            names.setOpaque(false);
            JPanel usernameCol = vertical(), displayCol = vertical();
            addField(usernameCol, "Tên đăng nhập", user);
            addField(displayCol, "Tên hiển thị", display);
            names.add(usernameCol);
            names.add(displayCol);
            names.setMaximumSize(new Dimension(Integer.MAX_VALUE, names.getPreferredSize().height));
            fields.add(names);
        } else {
            addField(fields, "Tên đăng nhập", user);
        }

        addField(fields, "Mật khẩu (10–128 ký tự)", password);
        if (register) {
            addField(fields, "Xác nhận lại mật khẩu", confirmPassword);

            // Password requirements checklist
            JPanel checklist = new JPanel(new GridLayout(3, 1, 0, 4));
            checklist.setOpaque(false);
            checklist.setBorder(new EmptyBorder(6, 4, 8, 4));
            JLabel chkLen = ModernUI.checklistItem("Độ dài mật khẩu từ 10 đến 128 ký tự");
            JLabel chkMatch = ModernUI.checklistItem("Mật khẩu xác nhận trùng khớp");
            JLabel chkName = ModernUI.checklistItem("Tên hiển thị không được để trống");
            checklist.add(chkLen);
            checklist.add(chkMatch);
            checklist.add(chkName);
            fields.add(checklist);

            DocumentListener docListener = new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { check(); }
                public void removeUpdate(DocumentEvent e) { check(); }
                public void changedUpdate(DocumentEvent e) { check(); }
                private void check() {
                    char[] p1 = password.getPassword(), p2 = confirmPassword.getPassword();
                    ModernUI.updateChecklist(chkLen, p1.length >= 10 && p1.length <= 128, "Độ dài mật khẩu từ 10 đến 128 ký tự");
                    ModernUI.updateChecklist(chkMatch, p1.length >= 10 && Arrays.equals(p1, p2), "Mật khẩu xác nhận trùng khớp");
                    ModernUI.updateChecklist(chkName, !display.getText().trim().isEmpty(), "Tên hiển thị không được để trống");
                    Arrays.fill(p1, '\0');
                    Arrays.fill(p2, '\0');
                }
            };
            password.getDocument().addDocumentListener(docListener);
            confirmPassword.getDocument().addDocumentListener(docListener);
            display.getDocument().addDocumentListener(docListener);
        }

        fields.add(Box.createVerticalStrut(4));
        JLabel state = label(" ", false);
        state.setFont(Theme.font(Font.PLAIN, 12));
        state.setForeground(Theme.MUTED);
        state.setBorder(null);
        state.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton submit = Theme.primaryButton(register ? "Tạo tài khoản" : "Đăng nhập");
        submit.setFont(Theme.font(Font.BOLD, 14));
        submit.putClientProperty("FlatLaf.style", submit.getClientProperty("FlatLaf.style") + "; margin:10,18,10,18");
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        submit.addActionListener(e -> {
            char[] secret = password.getPassword(), repeat = confirmPassword.getPassword();
            if (user.getText().trim().isEmpty() || secret.length < 10 || secret.length > 128) {
                Arrays.fill(secret, '\0');
                Arrays.fill(repeat, '\0');
                fail.accept("Nhập tên đăng nhập và mật khẩu từ 10–128 ký tự.");
                return;
            }
            if (register && (!Arrays.equals(secret, repeat) || display.getText().isBlank())) {
                Arrays.fill(secret, '\0');
                Arrays.fill(repeat, '\0');
                fail.accept("Điền tên hiển thị và xác nhận mật khẩu trùng khớp.");
                return;
            }
            Arrays.fill(repeat, '\0');
            String username = user.getText().trim(), name = display.getText().trim(), serverHost = hostField.getText().trim();
            int serverPort;
            try {
                serverPort = Integer.parseInt(portField.getText());
                if (serverPort < 1 || serverPort > 65535) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                Arrays.fill(secret, '\0');
                fail.accept("Cổng TCP cần nằm trong khoảng 1–65535.");
                return;
            }

            fail.accept(null);
            submit.setEnabled(false);
            submit.setText(register ? "Đang tạo tài khoản…" : "Đang đăng nhập…");
            state.setText("Đang kết nối " + serverHost + ":" + serverPort + " và xác thực…");
            app.reconnectToken++;

            CompletableFuture.supplyAsync(() -> {
                SocialClient c = null;
                try {
                    if (app.client != null) app.client.close();
                    c = new SocialClient(serverHost, serverPort);
                    Map<String, Object> hello = c.connect().join();
                    Map<String, Object> login = c.request(register ? "AUTH_REGISTER" : "AUTH_LOGIN",
                            of("username", username, "password", new String(secret), "displayName", register ? name : null)).join();
                    app.client = c;
                    return login;
                } catch (Throwable ex) {
                    if (c != null) c.close();
                    throw new CompletionException(ex);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }, app.workers).whenComplete((login, error) -> invoke(() -> {
                password.setText("");
                confirmPassword.setText("");
                submit.setEnabled(true);
                submit.setText(register ? "Tạo tài khoản" : "Đăng nhập");
                app.host = serverHost;
                app.port = serverPort;
                if (error != null) {
                    state.setText(" ");
                    fail.accept(friendly(error) + (isConnectionProblem(error) ? " Kiểm tra máy chủ và cổng ở cuối thẻ rồi thử lại." : ""));
                    return;
                }
                if (register) {
                    info("Tạo tài khoản thành công! Hãy đăng nhập để tiếp tục.");
                    app.client.close();
                    app.client = null;
                    showAuth(false);
                    return;
                }
                app.currentUser = map(login.get("currentUser"));
                if (app.currentUser.isEmpty()) app.currentUser = map(login.get("user"));
                if (app.pendingOwnerId != 0 && app.pendingOwnerId != id(app.currentUser, "user")) {
                    app.pendingMessages.clear();
                    app.media.close();
                    app.media = new MediaTransfers(() -> app.client, this::showError);
                }
                app.pendingOwnerId = id(app.currentUser, "user");
                app.media.bind(app.client);
                app.thumbnails.bind(app.client);
                app.authenticated = true;
                SocialClient logged = app.client;
                logged.onEvent(event -> {
                    app.media.event(event);
                    invoke(() -> {
                        if (app.client == logged) onEvent(event);
                    });
                });
                logged.onDisconnect(errorCause -> invoke(() -> {
                    if (app.client == logged && app.authenticated) disconnected(errorCause);
                }));
                showMain();
                query("STATE_SYNC", of("limit", 20), r -> refreshCount());
                showFeed();
            }));
        });

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBorder(new EmptyBorder(8, 0, 0, 0));
        south.add(errorBox);
        south.add(Box.createVerticalStrut(6));
        south.add(state);
        south.add(Box.createVerticalStrut(8));
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(submit);
        south.add(Box.createVerticalStrut(10));
        JButton switchMode = ghost(register ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký ngay", () -> showAuth(!register));
        switchMode.setForeground(Theme.PRIMARY_LIGHT);
        switchMode.setAlignmentX(Component.LEFT_ALIGNMENT);
        switchMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        south.add(switchMode);
        south.add(Box.createVerticalStrut(14));
        JSeparator rule = new JSeparator();
        rule.setForeground(Theme.BORDER);
        south.add(rule);
        south.add(Box.createVerticalStrut(12));
        JLabel serverTitle = new JLabel("Kết nối máy chủ");
        serverTitle.setFont(Theme.font(Font.BOLD, 12));
        serverTitle.setForeground(Theme.MUTED);
        serverTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(serverTitle);
        south.add(Box.createVerticalStrut(8));
        serverRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, serverRow.getPreferredSize().height));
        south.add(serverRow);
        panel.add(scroll(fields), BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        panel.putClientProperty("fields", fields);
        authParts[0] = panel;
        authParts[1] = south;
        panel.setPreferredSize(new Dimension(444, fields.getPreferredSize().height + south.getPreferredSize().height + 70));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.weightx = 1;
        constraints.weighty = 1;
        center.add(panel, constraints);
        panel.putClientProperty("CardPanel.flat", Boolean.TRUE);
        JPanel split = new JPanel(new BorderLayout());
        split.setOpaque(false);
        split.add(new HeroPanel("Trò chuyện và kết nối trên máy chủ của riêng bạn.",
                "Nhắn tin, chia sẻ bài viết và giữ liên lạc với bạn bè qua kết nối TCP trực tiếp."), BorderLayout.WEST);
        split.add(center, BorderLayout.CENTER);
        ((JComponent) split.getComponent(0)).setPreferredSize(new Dimension(440, 100));
        app.root.add(split);
        app.root.revalidate();
        app.root.repaint();
        getRootPane().setDefaultButton(submit);
    }

    static boolean isConnectionProblem(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.io.IOException || t instanceof TimeoutException) return true;
        }
        return false;
    }

    JLabel smallLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 12));
        l.setForeground(Theme.TEXT_SECONDARY);
        return l;
    }

    void addField(JPanel p, String name, Component input) {
        if (input instanceof JTextField textField) {
            styleAuthInput(textField);
        } else if (input instanceof JComboBox<?> combo) {
            input.setPreferredSize(new Dimension(input.getPreferredSize().width, 38));
        }
        JPanel row = new JPanel(new BorderLayout(4, 4));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(3, 0, 5, 0));
        JLabel lbl = label(name, false);
        lbl.setFont(Theme.font(Font.BOLD, 12));
        lbl.setForeground(Theme.TEXT_SECONDARY);
        row.add(lbl, BorderLayout.NORTH);
        row.add(input, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        p.add(row);
    }

    /** Applies the same forgiving geometry and spacing to all auth inputs. */
    static void styleAuthInput(JTextField input) {
        Theme.styleInput(input);
        input.putClientProperty("JTextField.placeholderText", "");
    }
}
