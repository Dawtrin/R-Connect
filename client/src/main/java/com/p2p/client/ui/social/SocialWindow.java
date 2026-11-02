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

/**
 * R-Connect desktop client shell: authentication, navigation and every content screen.
 * Visual tokens and reusable controls live in {@code com.p2p.client.ui} (Theme, NavItem, Dialogs, ModernUI).
 * All TCP network IO, hashing, and file IO run strictly off the Swing EDT.
 */
public final class SocialWindow extends JFrame {
    final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "social-ui-worker");
        t.setDaemon(true);
        return t;
    });

    final JPanel root = new JPanel(new BorderLayout());
    final JPanel body = new JPanel(new BorderLayout());
    final ChatView chatView = new ChatView(this);
    final ReportView reportView = new ReportView(this);

    final BroadcastView broadcastView = new BroadcastView(this);

    final AuthView authView = new AuthView(this);
    final NotificationsView notificationsView = new NotificationsView(this);
    final AdminView adminView = new AdminView(this);
    final ProfileView profileView = new ProfileView(this);
    final PeopleView peopleView = new PeopleView(this);
    final FeedView feedView = new FeedView(this);
    final ShellView shellView = new ShellView(this);
    final MessagesView messagesView = new MessagesView(this);

    final JLabel connection = new JLabel("Chưa kết nối");
    final JLabel identity = new JLabel();
    JPanel ownAvatarHolder;
    static final int CHAT_WIDTH = 880;
    static final int LIST_WIDTH = 320;
    static final int CONTEXT_WIDTH = 280;
    static final int CONTEXT_MIN_CONTENT = 1120;
    final JPanel contextHolder = new JPanel(new BorderLayout());
    JComponent contextContent;
    boolean compactNext;
    Page convPage;
    JPanel convPanelCache;
    long selectedConvId;
    long ownPostMutationAt;
    JDialog invitesDialog;
    NavItem notificationNav;
    NavItem broadcastNav;
    JButton currentActionSource;
    final Map<String, NavItem> navButtons = new LinkedHashMap<>();

    volatile SocialClient client;
    volatile long reconnectToken;
    long pendingOwnerId;
    volatile CallWindow currentCall;
    long udpExpiresAt;
    final javax.swing.Timer udpRefresh = new javax.swing.Timer(60000, e -> refreshUdp());
    UdpClient udp;
    MediaTransfers media = new MediaTransfers(() -> client, this::showError);
    final ThumbnailCache thumbnails = new ThumbnailCache();

    Map<String, Object> currentUser = new LinkedHashMap<>();
    String host = System.getProperty("social.host", "localhost");
    int port = Integer.getInteger("social.port", 5000);
    String view = "";
    Runnable refreshCurrent = () -> {};
    boolean authenticated;
    long generation;
    String displayLanguage;
    final Preferences userPreferences = Preferences.userNodeForPackage(SocialWindow.class);
    final Map<String, Map<String, Object>> translations = new ConcurrentHashMap<>();
    final Set<String> translationPending = ConcurrentHashMap.newKeySet();
    final Set<String> originalMessages = ConcurrentHashMap.newKeySet();

    // Chat state
    JPanel chatMessages;
    JScrollPane chatScroll;
    long activeConversation;
    long earliestSeq, latestSeq;
    Map<String, Object> activeConversationData = new LinkedHashMap<>();
    final LinkedHashMap<Long, Map<String, Object>> messages = new LinkedHashMap<>();
    final LinkedHashMap<String, PendingMessage> pendingMessages = new LinkedHashMap<>();
    JLabel typingLabel;
    JTextArea chatInput;


    // Timers
    final javax.swing.Timer typingExpiry = new javax.swing.Timer(5000, e -> { if (typingLabel != null) typingLabel.setText(" "); });
    final javax.swing.Timer countRefresh = new javax.swing.Timer(20000, e -> { if (authenticated) refreshCount(); });
    final javax.swing.Timer eventRefresh = new javax.swing.Timer(400, e -> { if (authenticated) refreshCurrent.run(); });

    public SocialWindow() {
        super("R-Connect · Secure Social Desktop");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1160, 780);
        setMinimumSize(new Dimension(1000, 620));
        setLocationRelativeTo(null);
        String savedLanguage = userPreferences.get("displayLanguage", "vi");
        displayLanguage = savedLanguage.startsWith("en") ? "en" : "vi";

        root.setBackground(Theme.APP_BACKGROUND);
        root.setBorder(new EmptyBorder(0, 0, 0, 0));
        body.setBackground(Theme.APP_BACKGROUND);
        setContentPane(root);

        typingExpiry.setRepeats(false);
        eventRefresh.setRepeats(false);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                shutdown();
                dispose();
            }
        });

        showAuth(false);

        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent e) {
                if (authenticated && activeConversation > 0) ackMessages();
            }
        });
    }

    void shutdown() {
        reconnectToken++;
        authenticated = false;
        SocialClient closing = client;
        client = null;
        countRefresh.stop();
        udpRefresh.stop();
        eventRefresh.stop();
        typingExpiry.stop();
        if (udp != null) udp.close();
        thumbnails.close();
        media.close();
        if (closing != null) closing.close();
        workers.shutdownNow();
    }

    /** Secondary (outlined) button: the default for non-primary actions. */
    JButton button(String text, Runnable action) {
        JButton b = Theme.secondaryButton(text);
        b.addActionListener(e -> runAction(b, action));
        return b;
    }

    JButton button(String text, Icon icon, Runnable action) {
        JButton b = button(text, action);
        if (icon != null) {
            b.setIcon(icon);
            b.setIconTextGap(8);
        }
        return b;
    }

    /** Quiet inline action (card toolbars, links): no fill until hovered. */
    JButton ghost(String text, Icon icon, Runnable action) {
        JButton b = Theme.ghostButton(text);
        b.setFont(Theme.font(Font.PLAIN, 13));
        if (icon != null) {
            b.setIcon(icon);
            b.setIconTextGap(7);
        }
        b.addActionListener(e -> runAction(b, action));
        return b;
    }

    JButton ghost(String text, Runnable action) {
        return ghost(text, null, action);
    }

    JButton danger(String text, Runnable action) {
        JButton b = Theme.dangerOutlineButton(text);
        b.addActionListener(e -> runAction(b, action));
        return b;
    }

    JButton primary(String text, Runnable action) {
        JButton b = Theme.primaryButton(text);
        b.addActionListener(e -> runAction(b, action));
        return b;
    }

    JButton primary(String text, Icon icon, Runnable action) {
        JButton b = primary(text, action);
        if (icon != null) {
            b.setIcon(icon);
            b.setIconTextGap(8);
        }
        return b;
    }

    void runAction(JButton source, Runnable action) {
        JButton previous = currentActionSource;
        currentActionSource = source;
        try {
            action.run();
        } finally {
            currentActionSource = previous;
        }
    }

    JPanel horizontal(Component... components) {
        JPanel p = new JPanel(new WrapLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(0, -8, 0, 0));   // cancel the leading gap so rows align with fields
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component c : components) p.add(c);
        return p;
    }

    JPanel vertical() {
        return new StackPanel();
    }

    JPanel card() {
        JPanel p = new CardPanel();
        p.setBackground(Theme.CARD_BACKGROUND);
        p.setBorder(new EmptyBorder(16, 18, 14, 18));
        p.putClientProperty("CardPanel.fillWidth", true);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    JLabel label(String text, boolean heading) {
        JLabel l = new JLabel(text);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 0, 6));
        l.putClientProperty("html.disable", true);
        l.setFont(Theme.font(heading ? Font.BOLD : Font.PLAIN, heading ? 18 : 13));
        l.setForeground(heading ? Theme.TEXT : Theme.TEXT_SECONDARY);
        return l;
    }

    JTextArea text(String value, int rows) {
        return text(value, rows, false);
    }

    /**
     * Read-only areas must freeze their caret BEFORE the text is set: otherwise the queued caret repaint scrolls the
     * enclosing scroll pane to the last card (the feed used to open at the bottom).
     */
    JTextArea text(String value, int rows, boolean readOnly) {
        JTextArea a = new JTextArea(rows, 35);
        if (readOnly) {
            a.setEditable(false);
            ((javax.swing.text.DefaultCaret) a.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        }
        a.setText(value);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(Theme.messageFont(Font.PLAIN, 13));
        a.setBackground(Theme.SURFACE);
        a.setForeground(Theme.TEXT);
        a.setCaretColor(Theme.TEXT);
        a.setSelectionColor(Theme.hex(0x2B3760));
        a.setMargin(new Insets(8, 10, 8, 10));
        return a;
    }

    /** Wraps a text area in a scroll pane with the R-Connect input frame (hairline border, accent when focused). */
    JScrollPane framed(JTextArea area) {
        JScrollPane s = new JScrollPane();
        s.setViewport(new JViewport() {
            @Override public void paint(Graphics g) {
                super.paint(g);
                Object hint = area.getClientProperty("JTextField.placeholderText");
                if (hint != null && area.getDocument().getLength() == 0 && area.isEnabled()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(area.getFont());
                    g2.setColor(Theme.MUTED);
                    Insets in = area.getInsets();
                    g2.drawString(hint.toString(), in.left, in.top + g2.getFontMetrics().getAscent());
                    g2.dispose();
                }
            }
        });
        s.setViewportView(area);
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { s.getViewport().repaint(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { s.getViewport().repaint(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { s.getViewport().repaint(); }
        });
        s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        s.getVerticalScrollBar().setUnitIncrement(16);
        s.getViewport().setBackground(area.getBackground());
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        Runnable paintBorder = () -> s.setBorder(new RoundedLineBorder(
                !area.isEnabled() ? Theme.BORDER : area.hasFocus() ? Theme.PRIMARY : Theme.BORDER_STRONG, Theme.RADIUS_MD));
        paintBorder.run();
        area.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { paintBorder.run(); }
            @Override public void focusLost(FocusEvent e) { paintBorder.run(); }
        });
        area.addPropertyChangeListener("enabled", e -> paintBorder.run());
        return s;
    }

    JTextArea readable(String value) {
        JTextArea a = text(value, Math.min(8, Math.max(1, value.length() / 70 + 1)), true);
        a.setEditable(false);
        a.setOpaque(false);
        a.setBorder(new EmptyBorder(2, 2, 2, 2));
        a.setForeground(Theme.TEXT);
        return a;
    }

    JTextArea readableMessage(String value) {
        String safe = value != null ? value : "";
        int len = safe.length();
        int cols = Math.max(4, Math.min(30, len + 1));
        int rows = Math.min(10, Math.max(1, len / 36 + 1));
        JTextArea a = new JTextArea(rows, cols);
        a.setEditable(false);
        ((javax.swing.text.DefaultCaret) a.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        a.setText(safe);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setFont(Theme.font(Font.PLAIN, 13));
        a.setBorder(new EmptyBorder(2, 2, 2, 2));
        a.setForeground(Theme.TEXT);
        return a;
    }

    /** Scrollable form column: content is centered with a readable maximum width. */
    JScrollPane columnScroll(JPanel content, int maxWidth) {
        content.setMaximumSize(new Dimension(maxWidth, Integer.MAX_VALUE));
        StackPanel holder = new StackPanel(true);
        holder.setBorder(new EmptyBorder(0, 24, 0, 24));
        holder.add(content);
        return scroll(holder);
    }

    JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.getVerticalScrollBar().setUnitIncrement(20);
        return s;
    }

    static JPanel centered(JComponent child, int maxWidth) {
        JPanel p = new JPanel(new CenteredLayout(maxWidth));
        p.setOpaque(false);
        p.add(child);
        return p;
    }

    JComponent avatar(Map<String, Object> user, int size) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(size, size));
        panel.setMinimumSize(new Dimension(size, size));
        panel.setMaximumSize(new Dimension(size, size));
        panel.setAlignmentY(Component.CENTER_ALIGNMENT);
        Theme.Avatar face = new Theme.Avatar(name(user), size, bool(user, "online"));
        panel.add(face);
        String mid = str(user, "avatarMediaId");
        if (!mid.isBlank()) {
            thumbnails.load(mid, size * 2, icon -> {
                face.setImage(icon.getImage());
                panel.revalidate();
                panel.repaint();
            });
        }
        return panel;
    }

    JButton photo(String mediaId, String fileName) {
        JButton b = Theme.secondaryButton("Xem ảnh");
        b.setFont(Theme.font(Font.PLAIN, 12));
        b.setForeground(Theme.TEXT_SECONDARY);
        b.setIcon(new AppIcon(AppIcon.Type.FILE, Theme.MUTED, 20));
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setIconTextGap(8);
        b.setPreferredSize(new Dimension(196, 150));
        b.setToolTipText(fileName.isBlank() ? "Xem ảnh" : "Xem ảnh · " + fileName);
        b.addActionListener(e -> media.preview(this, mediaId));
        thumbnails.load(mediaId, 180, icon -> {
            b.setIcon(icon);
            b.setText("");
            b.revalidate();
            b.repaint();
        });
        return b;
    }

    /**
     * Page header: title on the left, page actions on the right. Callers may add a subtitle row to CENTER
     * and a meta row to SOUTH.
     */
    JPanel titled(String title, Component... actions) {
        JPanel p = new JPanel(new BorderLayout(12, 6));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(22, 24, 14, 24));
        JLabel heading = label(title, true);
        heading.setFont(Theme.title());
        heading.setForeground(Theme.TEXT);
        heading.setBorder(null);
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(heading, BorderLayout.CENTER);
        if (actions.length > 0) {
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            for (Component c : actions) right.add(c);
            row.add(right, BorderLayout.EAST);
        }
        p.add(row, BorderLayout.NORTH);
        return p;
    }

    void styleTabs(JTabbedPane tabs) {
        tabs.setFont(Theme.font(Font.BOLD, 13));
        tabs.setOpaque(false);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.putClientProperty("JTabbedPane.tabType", "underlined");
        tabs.putClientProperty("JTabbedPane.showTabSeparators", false);
        tabs.putClientProperty("JTabbedPane.hasFullBorder", false);
        tabs.putClientProperty("JTabbedPane.tabAreaAlignment", "leading");
        tabs.putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 24, 0, 24));
        tabs.putClientProperty("JTabbedPane.minimumTabWidth", 72);
        tabs.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int column = Math.min(Theme.COLUMN_WIDTH + 48, tabs.getWidth());
                int inset = Math.max(24, (tabs.getWidth() - column) / 2 + 24);
                Object old = tabs.getClientProperty("JTabbedPane.tabAreaInsets");
                if (!(old instanceof Insets in) || in.left != inset) {
                    tabs.putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, inset, 0, 24));
                }
            }
        });
    }

    /** Sets (or clears with null) the right-hand context panel. It is hidden automatically on narrow windows. */
    void setContext(JComponent component) {
        contextContent = component;
        contextHolder.removeAll();
        if (component != null) contextHolder.add(component, BorderLayout.CENTER);
        applyContextVisibility();
    }

    void applyContextVisibility() {
        Container content = contextHolder.getParent();
        boolean wide = content != null && content.getWidth() >= CONTEXT_MIN_CONTENT;
        boolean show = contextContent != null && wide;
        if (contextHolder.isVisible() != show) contextHolder.setVisible(show);
        contextHolder.revalidate();
        contextHolder.repaint();
    }

    void setView(String name, JPanel panel, Runnable refresh) {
        setContext(null);
        if (authenticated && connection.getText() != null && connection.getText().contains("Bảng tin có cập nhật mới")) {
            connection.setText("Đã kết nối TCP · " + host + ":" + port);
        }
        generation++;
        view = name;
        activeConversation = 0;
        messages.clear();
        translations.clear();
        translationPending.clear();
        originalMessages.clear();
        body.removeAll();
        body.add(panel);
        refreshCurrent = refresh;
        updateNavSelection(name);
        body.revalidate();
        body.repaint();
    }

    void updateNavSelection(String currentView) {
        String selected = switch (currentView) {
            case "post" -> "feed";
            case "profile" -> "people";
            case "chat" -> "conversations";
            default -> currentView;
        };
        navButtons.forEach((key, nav) -> nav.setActive(key.equals(selected)));
    }

    void invoke(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    Map<String, Object> blocking(String type, Map<String, Object> payload, String key) {
        SocialClient c = client;
        if (c == null) throw new IllegalStateException("Chưa kết nối server");
        return (key == null ? c.request(type, payload) : c.request(type, payload, key)).join();
    }

    Map<String, Object> blocking(SocialClient c, String type, Map<String, Object> payload, String key) {
        if (c == null) throw new IllegalStateException("Chưa kết nối server");
        return (key == null ? c.request(type, payload) : c.request(type, payload, key)).join();
    }

    void query(String type, Map<String, Object> payload, Consumer<Map<String, Object>> success) {
        command(type, payload, null, null, success, false);
    }

    void mutate(String type, Map<String, Object> payload, JButton source, Consumer<Map<String, Object>> success) {
        command(type, payload, UUID.randomUUID().toString(), source == null ? currentActionSource : source, success, true);
    }

    void command(String type, Map<String, Object> payload, String key, JButton source, Consumer<Map<String, Object>> success, boolean retry) {
        if (source != null) source.setEnabled(false);
        SocialClient expected = client;
        CompletableFuture.supplyAsync(() -> blocking(expected, type, payload, key), workers).whenComplete((result, error) -> invoke(() -> {
            if (source != null) source.setEnabled(authenticated || type.startsWith("AUTH_"));
            if (client != expected) return;
            if (error != null) {
                Throwable cause = unwrap(error);
                if (retry && (cause instanceof java.io.IOException || cause instanceof TimeoutException)) {
                    int answer = Dialogs.choose(this, "Kết nối gián đoạn",
                            "Chưa xác định kết quả. Thử lại dùng cùng mã để tránh tạo bản sao.\n" + friendly(cause),
                            Dialogs.Kind.WARNING, "Thử lại cùng thao tác", "Đóng");
                    if (answer == 0) command(type, payload, key, source, success, true);
                    else showError(cause);
                } else {
                    showError(cause);
                }
                return;
            }
            success.accept(result);
        }));
    }

    Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    String friendly(Throwable error) {
        Throwable e = unwrap(error);
        if (e instanceof RemoteException remote) {
            String code = remote.getCode();
            return switch (code) {
                case "INVALID_CREDENTIALS" -> "Tên đăng nhập hoặc mật khẩu không chính xác.";
                case "ALREADY_SIGNED_IN" -> "Tài khoản đang đăng nhập trên thiết bị khác.";
                case "VERSION_CONFLICT" -> "Dữ liệu đã thay đổi trên máy chủ. Tải lại trang trước khi lưu.";
                case "NOT_FOUND" -> "Nội dung không còn khả dụng hoặc bạn không có quyền truy cập.";
                case "FRIENDSHIP_REQUIRED" -> "Cần kết bạn trước khi thực hiện hành động này.";
                case "UNAUTHENTICATED" -> "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.";
                case "RATE_LIMITED" -> "Bạn thao tác quá nhanh. Vui lòng đợi trong giây lát rồi thử lại.";
                case "USERNAME_TAKEN" -> "Tên đăng nhập này đã được sử dụng bởi người khác.";
                case "ACCOUNT_SUSPENDED" -> "Tài khoản hiện đã bị tạm khóa bởi quản trị viên.";
                case "ACTION_FORBIDDEN" -> "Bạn không có quyền thực hiện thao tác này.";
                case "INVALID_STATE" -> "Trạng thái đã thay đổi. Vui lòng làm mới danh sách.";
                case "INVALID_REQUEST" -> "Dữ liệu gửi lên chưa hợp lệ. Vui lòng kiểm tra lại.";
                case "INCOMING_REQUEST_EXISTS" -> "Người này đã gửi lời mời cho bạn. Hãy chấp nhận từ tab Đã nhận.";
                case "ALREADY_FRIENDS" -> "Hai bạn đã là bạn bè của nhau.";
                case "QUOTA_EXCEEDED" -> "Đã đạt giới hạn dung lượng hoặc số thành viên.";
                case "FILE_TOO_LARGE" -> "Tệp vượt quá giới hạn: tin nhắn 25 MiB; ảnh/avatar 5 MiB.";
                case "HASH_MISMATCH" -> "Tệp không khớp mã băm SHA-256. Hãy chọn lại tệp và tải tiếp.";
                case "OFFSET_MISMATCH" -> "Vị trí tải tệp chưa khớp với máy chủ. Tiếp tục theo xác nhận.";
                case "TRANSFER_EXPIRED" -> "Lượt tải tệp đã hết hạn. Hãy bấm tiếp tục để lấy lượt tải mới.";
                case "SERVER_BUSY" -> "Máy chủ đang xử lý nhiều tác vụ. Vui lòng thử lại sau giây lát.";
                case "IDEMPOTENCY_CONFLICT" -> "Mã thao tác đã gắn với nội dung khác. Vui lòng kiểm tra lại.";
                case "VERSION_UNSUPPORTED" -> "Phiên bản client không tương thích với máy chủ.";
                case "UNSUPPORTED_OPERATION" -> "Máy chủ chưa hỗ trợ thao tác này.";
                case "INTERNAL_ERROR" -> "Máy chủ gặp sự cố nội bộ. Vui lòng thử lại sau.";
                default -> code + " · " + Objects.toString(e.getMessage(), "Không thể thực hiện");
            };
        }
        return Objects.toString(e.getMessage(), "Không thể kết nối máy chủ TCP. Hãy thử lại.");
    }

    void showError(Throwable e) {
        if (!SwingUtilities.isEventDispatchThread()) {
            invoke(() -> showError(e));
            return;
        }
        Dialogs.notice(this, "Không thể hoàn tất", friendly(e), Dialogs.Kind.WARNING);
    }

    /** Short, non-blocking confirmation. Used for outcomes the user does not need to acknowledge. */
    void info(String s) {
        if (isShowing()) ModernUI.showToast(this, s, ModernUI.ToastType.SUCCESS);
    }

    /** Non-blocking validation hint (amber), for input the user must correct. */
    void warn(String s) {
        if (isShowing()) ModernUI.showToast(this, s, ModernUI.ToastType.WARNING);
    }

    boolean confirm(String s) {
        return Dialogs.confirm(this, "Xác nhận thao tác", s, "Xác nhận", true);
    }

    void showAuth(boolean register) { authView.showAuth(register); }


    static boolean isConnectionProblem(Throwable e) { return AuthView.isConnectionProblem(e); }


    JLabel smallLabel(String text) { return authView.smallLabel(text); }


    void addField(JPanel p, String name, Component input) { authView.addField(p, name, input); }


    static void styleAuthInput(JTextField input) { AuthView.styleAuthInput(input); }


    void setupUdp(Map<String, Object> login) {
        if (udp != null) udp.close();
        Map<String, Object> config = map(login.get("udp"));
        if (config.isEmpty()) config = new LinkedHashMap<>(login);
        if (!config.containsKey("sessionId")) config.put("sessionId", login.get("sessionId"));
        udpExpiresAt = num(config, "keyExpiresAt");
        SocialClient expected = client;
        Map<String, Object> finalConfig = config;
        workers.execute(() -> {
            try {
                UdpClient created = new UdpClient(host, (int) (num(finalConfig, "serverUdpPort") == 0 ? 5002 : num(finalConfig, "serverUdpPort")));
                created.onTyping(p -> invoke(() -> {
                    if (!authenticated || client != expected || udp != created) return;
                    if (activeConversation == num(p, "conversationId") && typingLabel != null) {
                        long remaining = num(p, "expiresAt") - expected.serverTime();
                        if (bool(p, "typing") && remaining > 0) {
                            typingLabel.setText("Người trò chuyện đang nhập tin nhắn…");
                            typingExpiry.setInitialDelay((int) Math.min(5000, remaining));
                            typingExpiry.restart();
                        } else {
                            typingLabel.setText(" ");
                            typingExpiry.stop();
                        }
                    }
                }));
                created.configure(finalConfig, expected.serverTime());
                invoke(() -> {
                    if (client == expected && authenticated) {
                        udp = created;
                        udpRefresh.start();
                    } else created.close();
                });
            } catch (Exception ignored) {
                /* UDP is disabled in the default TCP-only profile. */
            }
        });
    }

    void refreshUdp() {
        if (!authenticated || udp == null || client == null || client.serverTime() < udpExpiresAt - 120000) return;
        query("UDP_SESSION_REFRESH", of(), this::setupUdp);
    }

    void showMain() { shellView.showMain(); }


    void installShortcuts() { shellView.installShortcuts(); }


    JLabel navSection(String text) { return shellView.navSection(text); }


    NavItem navButton(String key, String text, AppIcon.Type iconType, Runnable action) { return shellView.navButton(key, text, iconType, action); }


    void logout() {
        query("AUTH_LOGOUT", of(), r -> {
            clearSession();
            showAuth(false);
        });
    }

    void clearSession() {
        convRefresh.stop();
        convPage = null;
        convPanelCache = null;
        selectedConvId = 0;
        reconnectToken++;
        authenticated = false;
        thumbnails.bind(null);
        udpRefresh.stop();
        for (Window window : getOwnedWindows()) window.dispose();
        if (currentCall != null) {
            currentCall.hangup();
            currentCall = null;
        }
        if (udp != null) { udp.close(); udp = null; }
        if (client != null) { client.close(); client = null; }
        media.close();
        media = new MediaTransfers(() -> client, this::showError);
        messages.clear();
        pendingMessages.clear();
        resetBroadcast();
        pendingOwnerId = 0;
        currentUser.clear();
        body.removeAll();
    }

    void disconnected(Throwable error) {
        authenticated = false;
        connection.setForeground(Theme.WARNING);
        connection.setIcon(new AppIcon(AppIcon.Type.DOT, Theme.WARNING, 10));
        connection.setText("Mất kết nối · Dữ liệu có thể cũ · Đang thử kết nối lại TCP...");
        if (udp != null) udp.close();
        udpRefresh.stop();
        media.disconnected();
        if (chatInput != null) chatInput.setEnabled(false);
        if (typingLabel != null) typingLabel.setText(" ");
        countRefresh.stop();
        disableButtons(body);
        long token = ++reconnectToken;
        if (error == null) return;
        workers.execute(() -> {
            for (int attempt = 0; attempt < 6 && reconnectToken == token; attempt++) {
                try {
                    Thread.sleep(Math.min(30000, 1000L << attempt) + ThreadLocalRandom.current().nextInt(250));
                    if (reconnectToken != token) return;
                    SocialClient candidate = new SocialClient(host, port);
                    try {
                        candidate.connect().join();
                        if (reconnectToken != token) {
                            candidate.close();
                            return;
                        }
                        client = candidate;
                        invoke(() -> {
                            if (reconnectToken == token) {
                                showAuth(false);
                                info("Đã kết nối lại TCP thành công. Vui lòng đăng nhập lại; tin chưa gửi vẫn được lưu.");
                            }
                        });
                        return;
                    } catch (Throwable e) {
                        candidate.close();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            invoke(() -> {
                if (reconnectToken == token) {
                    connection.setForeground(Theme.DANGER);
                    connection.setIcon(new AppIcon(AppIcon.Type.DOT, Theme.DANGER, 10));
                    connection.setText("Chưa kết nối · Bấm Đăng nhập lại để kết nối tiếp");
                }
            });
        });
    }

    void disableButtons(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JButton b) b.setEnabled(false);
            if (child instanceof Container nested) disableButtons(nested);
        }
    }

    void refreshCount() {
        if (!authenticated) return;
        query("NOTIFICATION_COUNT", of(), r -> {
            long unread = num(r, "unreadCount", "count");
            if (notificationNav != null) notificationNav.setBadge((int) Math.min(unread, 999));
        });
    }

    void handleCallSignalEvent(Map<String, Object> p) {
        String action = str(p, "action");
        long senderId = num(p, "senderId");
        String callerName = str(p, "callerName", "displayName");
        long cid = num(p, "conversationId");
        String callId = str(p, "callId");
        String senderHost = str(p, "senderHost");
        int udpPort = (int) num(p, "udpPort");
        int videoPort = (int) num(p, "videoPort");
        boolean isVideo = bool(p, "isVideo");

        switch (action) {
            case "INVITE" -> {
                if (currentCall != null && currentCall.isDisplayable()) {
                    query("CALL_SIGNAL", Map.of("action", "BUSY", "targetUserId", senderId, "conversationId", cid, "callId", callId), r -> {});
                    return;
                }
                currentCall = CallWindow.showIncoming(this, senderId, callerName, cid, udpPort, videoPort, senderHost, callId, isVideo);
            }
            case "ACCEPT" -> {
                if (currentCall != null && currentCall.isDisplayable()) {
                    currentCall.onAcceptedByPeer(senderHost, udpPort, videoPort);
                }
            }
            case "REJECT" -> {
                if (currentCall != null && currentCall.isDisplayable()) {
                    currentCall.onCallRejected("Người dùng từ chối cuộc gọi");
                }
            }
            case "BUSY" -> {
                if (currentCall != null && currentCall.isDisplayable()) {
                    currentCall.onCallRejected("Người dùng đang bận");
                }
            }
            case "HANGUP" -> {
                if (currentCall != null && currentCall.isDisplayable()) {
                    currentCall.onCallEnded("Đối phương đã kết thúc cuộc gọi");
                }
            }
        }
    }

    public void startCallWithCurrentPeer() {
        startCallWithCurrentPeer(false);
    }

    public void startCallWithCurrentPeer(boolean isVideo) {
        if (activeConversationData.isEmpty()) return;
        boolean isGroup = "GROUP".equals(str(activeConversationData, "kind", "type"));
        if (isGroup) {
            info("Cuộc gọi nhóm P2P sẽ được hỗ trợ trong bản cập nhật tiếp theo");
            return;
        }
        long otherId = num(activeConversationData, "otherUserId");
        if (otherId == 0) {
            showError(new IllegalStateException("Không tìm thấy thông tin người nhận cuộc gọi"));
            return;
        }
        String otherName = str(activeConversationData, "title", "displayName", "otherUsername");
        if (otherName.isBlank()) otherName = "Người dùng #" + otherId;

        if (currentCall != null && currentCall.isDisplayable()) {
            currentCall.toFront();
            return;
        }
        currentCall = CallWindow.startOutgoing(this, otherId, otherName, activeConversation, isVideo);
    }

    void onEvent(WireMessage event) {
        if (!authenticated) return;
        String type = event.getType();
        Map<String, Object> p = event.getPayload();

        if (Set.of("PROFILE_INVALIDATED", "RELATION_CHANGED", "POST_INVALIDATED", "CONVERSATION_INVALIDATED").contains(type)) {
            thumbnails.clear();
        }
        if ("PROFILE_INVALIDATED".equals(type) && num(p, "userId") == id(currentUser, "user") && authenticated) {
            query("PROFILE_GET", of("targetUserId", id(currentUser, "user")), this::updateSelf);
        }
        if ("SESSION_REVOKED".equals(type) || "SERVER_STOPPING".equals(type)) {
            disconnected(null);
            return;
        }
        if ("NOTIFICATION_CHANGED".equals(type)) refreshCount();
        if ("CALL_SIGNAL".equals(type)) {
            handleCallSignalEvent(p);
            return;
        }
        if ("BROADCAST_MESSAGE".equals(type)) {
            receiveBroadcast(p);
            return;
        }
        if ("PRESENCE_CHANGED".equals(type) && "people".equals(view)) {
            updatePresence(body, num(p, "userId"), bool(p, "online"), num(p, "asOf"));
            return;
        }
        if ("feed".equals(view) && Set.of("POST_INVALIDATED", "COMMENT_INVALIDATED", "LIKE_INVALIDATED").contains(type)) {
            if ("POST_INVALIDATED".equals(type) && System.currentTimeMillis() - ownPostMutationAt < 6000) {
                // The change was made by this user a moment ago: reload instead of leaving a placeholder card.
                eventRefresh.restart();
                return;
            }
            if ("POST_INVALIDATED".equals(type)) invalidateVisiblePost(body, num(p, "postId"));
            connection.setIcon(new AppIcon(AppIcon.Type.DOT, Theme.SUCCESS, 10));
            connection.setText("Đã kết nối TCP · Bảng tin có cập nhật mới · Bấm Đồng bộ / Làm mới để xem");
            return;
        }
        if ("MESSAGE_CREATED".equals(type)) {
            Map<String, Object> m = map(p.get("message"));
            if (m.isEmpty()) m = p;
            if (activeConversation == num(m, "conversationId")) {
                upsertMessage(m);
                renderMessages(false);
                ackMessages();
            }
            return;
        }
        if ("RECEIPT_UPDATED".equals(type) && activeConversation == num(p, "conversationId")) {
            List<Map<String, Object>> receipts = rows(p.get("receipts"));
            if (receipts.isEmpty()) receipts = List.of(p);
            for (Map<String, Object> m : messages.values()) {
                if (num(m, "senderId") != id(currentUser, "user")) continue;
                long seq = num(m, "conversationSeq", "seq"), read = 0, delivered = 0;
                for (Map<String, Object> receipt : receipts) {
                    if (num(receipt, "actorId") == id(currentUser, "user")) continue;
                    if (seq <= num(receipt, "readThroughSeq", "readSeq")) read++;
                    if (seq <= num(receipt, "deliveredThroughSeq", "deliveredSeq")) delivered++;
                }
                m.put("readByCount", read);
                m.put("deliveredByCount", delivered);
                if (read > 0) m.put("status", "READ");
                else if (delivered > 0) m.put("status", "DELIVERED");
            }
            renderMessages(false);
            return;
        }
        if (Set.of("RELATION_CHANGED", "POST_INVALIDATED", "COMMENT_INVALIDATED", "LIKE_INVALIDATED", "PROFILE_INVALIDATED", "CONVERSATION_INVALIDATED", "GROUP_INVITE_CHANGED", "PRESENCE_CHANGED", "NOTIFICATION_CHANGED").contains(type)) {
            if ("chat".equals(view) && ("RELATION_CHANGED".equals(type) || "CONVERSATION_INVALIDATED".equals(type))) {
                long cid = activeConversation;
                messages.clear();
                chatMessages.removeAll();
                chatMessages.add(label("Đang kiểm tra quyền truy cập cuộc trò chuyện…", false));
                chatMessages.revalidate();
                chatMessages.repaint();
                chatInput.setEnabled(false);
                query("CONVERSATION_LIST", of("limit", 50), r -> {
                    Map<String, Object> found = rows(r.get("items")).stream().filter(c -> id(c, "conversation") == cid).findFirst().orElse(null);
                    if (found == null) showConversations();
                    else openChat(found);
                });
                return;
            }
            if ("POST_INVALIDATED".equals(type) || "RELATION_CHANGED".equals(type) || "CONVERSATION_INVALIDATED".equals(type)) {
                if ("feed".equals(view) || "post".equals(view) || "profile".equals(view)) {
                    body.removeAll();
                    body.add(label("Đang kiểm tra quyền và cập nhật dữ liệu…", false));
                    body.revalidate();
                    body.repaint();
                }
            }
            eventRefresh.restart();
        }
    }

    void updateSelf(Map<String, Object> profile) {
        currentUser.putAll(profile);
        identity.setText(name(currentUser));
        if (ownAvatarHolder != null) {
            ownAvatarHolder.removeAll();
            ownAvatarHolder.add(avatar(currentUser, 36));
            ownAvatarHolder.revalidate();
            ownAvatarHolder.repaint();
        }
    }

    void invalidateVisiblePost(Container container, long postId) {
        for (Component component : container.getComponents()) {
            if (component instanceof JPanel panel && Objects.equals(panel.getClientProperty("postId"), postId)) {
                int oldHeight = panel.getHeight();
                panel.removeAll();
                panel.add(readable("Bài viết đã thay đổi. Làm mới bảng tin để kiểm tra quyền và xem nội dung mới."));
                panel.setMinimumSize(new Dimension(0, Math.min(oldHeight, 100)));
                panel.revalidate();
                panel.repaint();
            } else if (component instanceof Container child) {
                invalidateVisiblePost(child, postId);
            }
        }
    }

    /** Paged view with cursor tracking. */
    final class Page {
        final boolean compact = compactNext;
        final JPanel panel = new JPanel(new BorderLayout(0, 0));
        final JPanel list = new StackPanel(true);
        final JLabel status = label("Đang tải dữ liệu…", false);
        final JButton more;
        final String type;
        final Map<String, Object> base;
        final Function<Map<String, Object>, JComponent> render;
        String cursor;
        String emptyTitle = "Chưa có nội dung nào";
        String emptyHint = "Danh sách hiện tại đang trống. Hãy quay lại sau!";
        AppIcon.Type emptyIcon = AppIcon.Type.FILE;
        boolean loading;
        int epoch;

        Page(String title, String type, Map<String, Object> base, Function<Map<String, Object>, JComponent> render, Component... actions) {
            this.type = type;
            this.base = base;
            this.render = render;
            panel.setOpaque(false);
            if (title != null && !title.isBlank()) {
                panel.add(centered(titled(title, actions), Theme.COLUMN_WIDTH + 48), BorderLayout.NORTH);
            }
            list.setBorder(compact ? new EmptyBorder(2, 8, 8, 8)
                    : new EmptyBorder(title == null || title.isBlank() ? 16 : 4, 24, 20, 24));
            panel.add(scroll(list), BorderLayout.CENTER);
            status.setFont(Theme.font(Font.PLAIN, 12));
            status.setForeground(Theme.MUTED);
            status.setBorder(null);
            more = ghost("Tải thêm", () -> load(false));
            if (compact) {
                more.setFont(Theme.font(Font.PLAIN, 12));
                JPanel compactFooter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                compactFooter.setOpaque(false);
                compactFooter.add(more);
                panel.add(compactFooter, BorderLayout.SOUTH);
            } else {
            JPanel footer = new JPanel(new BorderLayout(8, 0));
            footer.setOpaque(false);
            footer.setBorder(new EmptyBorder(8, 24, 8, 24));
            JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            footerActions.setOpaque(false);
            footerActions.add(ghost("Làm mới", new AppIcon(AppIcon.Type.REFRESH, Theme.TEXT_SECONDARY, 14), () -> load(true)));
            footerActions.add(more);
            footer.add(status, BorderLayout.CENTER);
            footer.add(footerActions, BorderLayout.EAST);
            JPanel footerBar = new JPanel(new BorderLayout());
            footerBar.setBackground(Theme.APP_BACKGROUND);
            footerBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
            footerBar.add(centered(footer, Theme.COLUMN_WIDTH + 48));
            panel.add(footerBar, BorderLayout.SOUTH);
            }
            compactNext = false;
        }

        void load(boolean reset) {
            if (loading) return;
            if ("USER_SEARCH".equals(type) && str(base, "query").trim().length() < 2) {
                status.setText("Nhập ít nhất 2 ký tự để tìm kiếm người dùng.");
                return;
            }
            loading = true;
            more.setEnabled(false);
            int token = ++epoch;
            long session = generation;
            if (reset) {
                cursor = null;
                list.removeAll();
                status.setText("Đang tải…");
            }
            Map<String, Object> request = new LinkedHashMap<>(base);
            request.put("limit", 20);
            if (cursor != null && !cursor.isBlank()) request.put("cursor", cursor);
            SocialClient requestedClient = client;
            CompletableFuture.supplyAsync(() -> blocking(requestedClient, type, request, null), workers).whenComplete((result, error) -> invoke(() -> {
                if (token != epoch || session != generation) return;
                loading = false;
                if (error != null) {
                    status.setText("Không tải được dữ liệu · Bấm Làm mới để thử lại");
                    more.setEnabled(true);
                    showError(error);
                    return;
                }
                if (list.getComponentCount() >= 120) list.removeAll();
                List<Map<String, Object>> items = rows(result.get("items"));
                for (Map<String, Object> row : items) {
                    JComponent rendered = render.apply(row);
                    rendered.setMaximumSize(new Dimension(compact ? Integer.MAX_VALUE : Theme.COLUMN_WIDTH, rendered.getPreferredSize().height));
                    list.add(rendered);
                    list.add(Box.createVerticalStrut(compact ? 2 : 12));
                }
                if ("RELATION_LIST".equals(type) && "FRIENDS".equals(base.get("kind")) && !items.isEmpty()) {
                    List<Long> ids = items.stream().map(r -> num(relationUser(r), "userId", "id", "otherUserId", "targetUserId")).filter(id -> id > 0).toList();
                    query("PRESENCE_GET", of("userIds", ids), presence -> {
                        if (session != generation) return;
                        for (Map<String, Object> state : rows(presence.get("items"))) {
                            updatePresence(list, num(state, "userId"), bool(state, "online"), num(state, "asOf"));
                        }
                    });
                }
                cursor = str(result, "nextCursor");
                more.setEnabled(bool(result, "hasMore") || !cursor.isBlank());
                if (compact) more.setVisible(more.isEnabled());
                if (items.isEmpty() && list.getComponentCount() == 0) {
                    status.setText("Chưa có nội dung.");
                    if ("COMMENT_LIST".equals(type)) {
                        list.add(ModernUI.emptyState(new AppIcon(AppIcon.Type.CHAT, Theme.MUTED, 36), "Chưa có bình luận", "Hãy là người đầu tiên chia sẻ ý kiến về bài viết này."));
                    } else {
                        list.add(ModernUI.emptyState(new AppIcon(emptyIcon, Theme.MUTED, 30), emptyTitle, emptyHint));
                    }
                } else {
                    status.setText("Đã tải " + items.size() + " mục · Giờ theo thiết bị của bạn");
                }
                list.revalidate();
                list.repaint();
            }));
        }
    }

    void showFeed() { feedView.showFeed(); }


    JComponent feedContext() { return feedView.feedContext(); }


    JButton fullWidthButton(String text, Runnable action) { return feedView.fullWidthButton(text, action); }


    JComponent friendRow(Map<String, Object> user, long uid, Map<Long, Theme.Avatar> faces, Map<Long, JLabel> states) { return feedView.friendRow(user, uid, faces, states); }


    JComponent postCard(Map<String, Object> post) { return feedView.postCard(post); }


    JButton authorLink(String text, Runnable action) { return feedView.authorLink(text, action); }


    JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.PLAIN, 12));
        l.setForeground(Theme.MUTED);
        l.putClientProperty("html.disable", Boolean.TRUE);
        return l;
    }

    String visibility(String code) { return feedView.visibility(code); }


    void composePost(Map<String, Object> existing) { feedView.composePost(existing); }


    void showPost(long postId) { feedView.showPost(postId); }


    /** Dialog footer: optional left-hand tools, then secondary actions and the primary action on the right. */
    JPanel actionRow(JComponent left, JButton secondary, JButton primaryAction) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (left != null) row.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        if (secondary != null) right.add(secondary);
        right.add(primaryAction);
        row.add(right, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return row;
    }

    JDialog dialog(String title, int width, int height) {
        JDialog d = new JDialog(this, title, Dialog.ModalityType.MODELESS);
        d.setSize(width, height);
        d.setLocationRelativeTo(this);
        ((JComponent) d.getContentPane()).setBackground(Theme.SURFACE_ELEVATED);
        ((JComponent) d.getContentPane()).setBorder(new EmptyBorder(20, 22, 18, 22));
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return d;
    }

    void showPeople() { peopleView.showPeople(); }


    Map<String, Object> relationUser(Map<String, Object> r) { return peopleView.relationUser(r); }




    void updatePresence(Container parent, long userId, boolean online, long asOf) { peopleView.updatePresence(parent, userId, online, asOf); }




    void block(long uid, boolean blocked) { peopleView.block(uid, blocked); }


    void showProfile(long uid) { profileView.showProfile(uid); }


    void showSettings() { profileView.showSettings(); }


    void openDirect(long target) { chatView.openDirect(target); }


    void receiveBroadcast(Map<String, Object> p) { broadcastView.receiveBroadcast(p); }


    void resetBroadcast() { broadcastView.resetBroadcast(); }


    void showBroadcast() { broadcastView.showBroadcast(); }


    void sendBroadcast(JTextArea input, JButton send, JLabel status) { broadcastView.sendBroadcast(input, send, status); }


    void renderBroadcasts() { broadcastView.renderBroadcasts(); }


    void showConversations() { messagesView.showConversations(); }


    JPanel messagesShell(JComponent center) { return messagesView.messagesShell(center); }


    void buildConversationList() { messagesView.buildConversationList(); }


    JComponent conversationRow(Map<String, Object> conv) { return messagesView.conversationRow(conv); }


    String shortStamp(long epochMillis) { return messagesView.shortStamp(epochMillis); }


    void showGroupInvites() { messagesView.showGroupInvites(); }


    JComponent chatContext(Map<String, Object> conv) { return messagesView.chatContext(conv); }


    JLabel contextHeading(String text) { return messagesView.contextHeading(text); }


    JComponent fullWidth(JComponent c) { return messagesView.fullWidth(c); }


    void createGroup() { messagesView.createGroup(); }


    void openChat(Map<String, Object> conv) { chatView.openChat(conv); }


    void loadHistory(boolean older) { chatView.loadHistory(older); }


    void syncChat(long cid) { chatView.syncChat(cid); }


    final javax.swing.Timer convRefresh = new javax.swing.Timer(700, e -> {
        if (authenticated && convPage != null && ("chat".equals(view) || "conversations".equals(view))) convPage.load(true);
    });

    /** Keeps the conversation list (order, unread chips, activity time) in step with incoming and sent messages. */
    void scheduleConversationRefresh() {
        if (convPage == null) return;
        convRefresh.setRepeats(false);
        convRefresh.restart();
    }

    void upsertMessage(Map<String, Object> m) { chatView.upsertMessage(m); }


    int statusRank(String s) { return chatView.statusRank(s); }


    void ackMessages() { chatView.ackMessages(); }


    String translationKey(Map<String, Object> message) { return chatView.translationKey(message); }


    void requestTranslation(Map<String, Object> message) { chatView.requestTranslation(message); }


    JComponent translatedMessage(Map<String, Object> message) { return chatView.translatedMessage(message); }


    void renderMessages(boolean bottom) { chatView.renderMessages(bottom); }


    String messageStatus(String status) { return chatView.messageStatus(status); }


    void sendMessage(long cid, String mediaId, String content) { chatView.sendMessage(cid, mediaId, content); }


    void submitMessage(PendingMessage p) { chatView.submitMessage(p); }


    void retryMessage(PendingMessage p) { chatView.retryMessage(p); }


    void groupInfo(Map<String, Object> original) { messagesView.groupInfo(original); }


    void selectFriend(LongConsumer selection) { messagesView.selectFriend(selection); }


    void showNotifications() { notificationsView.showNotifications(); }


    String notificationTitle(String type) { return notificationsView.notificationTitle(type); }


    String roleText(String role) {
        return switch (role) { case "OWNER" -> "Chủ nhóm"; case "MEMBER" -> "Thành viên"; case "ADMIN" -> "Quản trị viên"; default -> role; };
    }

    String targetText(String type) {
        return switch (type) { case "POST" -> "Bài viết"; case "COMMENT" -> "Bình luận"; case "USER" -> "Người dùng"; case "MESSAGE" -> "Tin nhắn"; default -> type; };
    }

    String statusText(String status) {
        return switch (status) {
            case "OPEN", "PENDING" -> "Đang chờ";
            case "CLAIMED", "IN_REVIEW" -> "Đang xử lý";
            case "RESOLVED" -> "Đã giải quyết";
            case "DISMISSED" -> "Đã bỏ qua";
            case "ACCEPTED" -> "Đã chấp nhận";
            case "REJECTED" -> "Đã từ chối";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    void report(String type, long target) { reportView.report(type, target); }


    void showAdmin() { adminView.showAdmin(); }


    void reviewReport(long reportId) { adminView.reviewReport(reportId); }


    void showRecord(String title, Map<String, Object> record) { adminView.showRecord(title, record); }


    String recordText(Map<String, Object> data) {
        StringBuilder b = new StringBuilder();
        data.forEach((k, v) -> {
            if (!Set.of("udpKey", "password", "passwordHash").contains(k)) {
                b.append(k).append(": ").append(v).append('\n');
            }
        });
        return b.toString();
    }
}
