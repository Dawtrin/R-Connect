package com.p2p.server.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Real-time automatic synchronization of account data, activity history, and message logs
 * to JSON and human-readable TXT files in resources/accounts/ whenever a message is sent
 * or an account event occurs.
 */
public final class UserResourceExporter {
    private static final Logger LOG = Logger.getLogger(UserResourceExporter.class.getName());
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '(GMT+7)'");
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final Map<String, String> KNOWN_PASSWORDS = new ConcurrentHashMap<>();
    static {
        KNOWN_PASSWORDS.put("dawtrin", "1234567890");
        KNOWN_PASSWORDS.put("rinnsan", "1234567890");
        KNOWN_PASSWORDS.put("test1", "1234567890");
    }

    private static final List<Path> TARGET_DIRS = List.of(
            Path.of("resources", "accounts"),
            Path.of("C:", "Users", "Lenovo", "IdeaProjects", "Chat-CS-P2P", "resources", "accounts"),
            Path.of("F:", "Chat-CS-P2P", "resources", "accounts")
    );

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RConnect-UserResourceExporter");
        t.setDaemon(true);
        return t;
    });

    private UserResourceExporter() {}

    public static void registerPassword(String username, String password) {
        if (username != null && password != null) {
            KNOWN_PASSWORDS.put(username.toLowerCase(Locale.ROOT), password);
        }
    }

    /**
     * Trigger asynchronous background export without blocking chat or request thread.
     */
    public static void syncAsync(SocialDatabase db) {
        if (db == null) return;
        EXECUTOR.submit(() -> {
            try {
                syncDirect(db);
            } catch (Throwable t) {
                LOG.log(Level.FINE, "Auto-sync error (non-fatal)", t);
            }
        });
    }

    /**
     * Synchronous export: Reads DB and exports JSON & TXT files to all valid target directories.
     */
    public static void syncDirect(SocialDatabase db) {
        if (db == null) return;
        try {
            db.read(conn -> {
                exportFromConnection(conn);
                return null;
            });
        } catch (Throwable t) {
            LOG.log(Level.FINE, "Failed to export user resources", t);
        }
    }

    private static void exportFromConnection(Connection conn) {
        try {
            // Find which target directories are accessible
            List<Path> activeTargets = new ArrayList<>();
            for (Path p : TARGET_DIRS) {
                Path abs = p.toAbsolutePath().normalize();
                if (Files.exists(abs) || (abs.getParent() != null && Files.exists(abs.getParent()))) {
                    activeTargets.add(abs);
                }
            }
            if (activeTargets.isEmpty()) return;

            // Skip export if running in a unit test temporary database
            try {
                String dbUrl = conn.getMetaData().getURL();
                if (dbUrl != null) {
                    String lower = dbUrl.toLowerCase(Locale.ROOT);
                    if (lower.contains("junit") || lower.contains("temp") || lower.contains("tmp")) {
                        return;
                    }
                }
            } catch (Exception ignored) {}

            // 1. Fetch all users
            List<Map<String, Object>> users = new ArrayList<>();
            String userSql = "SELECT u.id, u.username, u.role, u.status, u.created_at, u.last_login_at, p.display_name, p.bio " +
                    "FROM users u LEFT JOIN profiles p ON p.user_id = u.id ORDER BY u.id ASC";
            try (PreparedStatement ps = conn.prepareStatement(userSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> u = new HashMap<>();
                    u.put("id", rs.getLong("id"));
                    u.put("username", rs.getString("username"));
                    u.put("role", rs.getString("role"));
                    u.put("status", rs.getString("status"));
                    u.put("created_at", rs.getLong("created_at"));
                    u.put("last_login_at", rs.getObject("last_login_at") == null ? null : rs.getLong("last_login_at"));
                    u.put("display_name", rs.getString("display_name"));
                    u.put("bio", rs.getString("bio"));
                    users.add(u);
                }
            }

            List<Map<String, Object>> summaryList = new ArrayList<>();
            Map<String, UserBundle> bundleMap = new HashMap<>();

            for (Map<String, Object> u : users) {
                long uid = (Long) u.get("id");
                String uname = (String) u.get("username");
                String dname = u.get("display_name") != null ? (String) u.get("display_name") : uname;
                String role = (String) u.get("role");
                String status = (String) u.get("status");
                long cAt = (Long) u.get("created_at");

                ZonedDateTime zdt = Instant.ofEpochMilli(cAt).atZone(VN_ZONE);
                String dtStr = zdt.format(DISPLAY_FORMATTER);
                String isoStr = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                String pw = KNOWN_PASSWORDS.getOrDefault(uname.toLowerCase(Locale.ROOT), "1234567890");
                String bio = u.get("bio") != null ? (String) u.get("bio") : "";

                // Fetch messages sent by this user
                List<Map<String, Object>> msgList = new ArrayList<>();
                String msgSql = "SELECT m.id, m.conversation_id, m.conversation_seq, m.kind, m.content, m.created_at, " +
                        "c.kind as conv_kind, c.title as conv_title " +
                        "FROM messages m JOIN conversations c ON c.id = m.conversation_id " +
                        "WHERE m.sender_id = ? ORDER BY m.created_at ASC";
                try (PreparedStatement ps = conn.prepareStatement(msgSql)) {
                    ps.setLong(1, uid);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long mCAt = rs.getLong("created_at");
                            ZonedDateTime mZdt = Instant.ofEpochMilli(mCAt).atZone(VN_ZONE);
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("messageId", rs.getLong("id"));
                            m.put("conversationId", rs.getLong("conversation_id"));
                            m.put("conversationType", rs.getString("conv_kind"));
                            m.put("conversationTitle", rs.getString("conv_title"));
                            m.put("sequence", rs.getLong("conversation_seq"));
                            m.put("kind", rs.getString("kind"));
                            m.put("content", rs.getString("content"));
                            m.put("sentAt", mZdt.format(DISPLAY_FORMATTER));
                            m.put("timestamp", mCAt);
                            msgList.add(m);
                        }
                    }
                }

                // Fetch friends
                List<Map<String, Object>> friendList = new ArrayList<>();
                String frSql = "SELECT CASE WHEN low_user_id = ? THEN high_user_id ELSE low_user_id END AS friend_id " +
                        "FROM relationships WHERE (low_user_id = ? OR high_user_id = ?) AND status = 'ACCEPTED'";
                try (PreparedStatement ps = conn.prepareStatement(frSql)) {
                    ps.setLong(1, uid);
                    ps.setLong(2, uid);
                    ps.setLong(3, uid);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long fid = rs.getLong("friend_id");
                            try (PreparedStatement psU = conn.prepareStatement("SELECT username FROM users WHERE id = ?")) {
                                psU.setLong(1, fid);
                                try (ResultSet rsU = psU.executeQuery()) {
                                    if (rsU.next()) {
                                        Map<String, Object> frMap = new LinkedHashMap<>();
                                        frMap.put("friendUserId", fid);
                                        frMap.put("friendUsername", rsU.getString("username"));
                                        friendList.add(frMap);
                                    }
                                }
                            }
                        }
                    }
                }

                Map<String, Object> accountInfo = new LinkedHashMap<>();
                accountInfo.put("userId", uid);
                accountInfo.put("username", uname);
                accountInfo.put("displayName", dname);
                accountInfo.put("password", pw);
                accountInfo.put("role", role);
                accountInfo.put("status", status);
                accountInfo.put("registrationDate", dtStr);
                accountInfo.put("registeredTimestamp", cAt);
                accountInfo.put("isoTimestamp", isoStr);
                accountInfo.put("bio", bio);

                Map<String, Object> activityData = new LinkedHashMap<>();
                activityData.put("userId", uid);
                activityData.put("username", uname);
                activityData.put("totalMessagesSent", msgList.size());
                activityData.put("friendsCount", friendList.size());
                activityData.put("friends", friendList);
                activityData.put("messagesSent", msgList);

                StringBuilder txtInfo = new StringBuilder();
                txtInfo.append("==================================================\n")
                        .append("           THÔNG TIN TÀI KHOẢN R-CONNECT          \n")
                        .append("==================================================\n")
                        .append("Mã người dùng (User ID)    : ").append(uid).append("\n")
                        .append("Tên đăng nhập (Username)   : ").append(uname).append("\n")
                        .append("Tên hiển thị (Display Name): ").append(dname).append("\n")
                        .append("Mật khẩu (Password)        : ").append(pw).append("\n")
                        .append("Vai trò (Role)             : ").append(role).append("\n")
                        .append("Trạng thái (Status)        : ").append(status).append("\n")
                        .append("Thời gian đăng ký          : ").append(dtStr).append("\n")
                        .append("Tiểu sử (Bio)              : ").append(bio.isBlank() ? "(Trống)" : bio).append("\n")
                        .append("==================================================\n");

                StringBuilder txtMsg = new StringBuilder();
                txtMsg.append("==================================================\n")
                        .append("   LỊCH SỬ TIN NHẮN ĐÃ GỬI - TÀI KHOẢN: ").append(uname).append("\n")
                        .append("==================================================\n")
                        .append("Tổng số tin nhắn đã gửi: ").append(msgList.size()).append("\n\n");

                int idx = 1;
                for (Map<String, Object> m : msgList) {
                    txtMsg.append("[").append(idx++).append("] Thời gian: ").append(m.get("sentAt")).append("\n")
                            .append("    Cuộc hội thoại: #").append(m.get("conversationId"))
                            .append(" (").append(m.get("conversationType")).append(")\n")
                            .append("    Loại: ").append(m.get("kind")).append("\n")
                            .append("    Nội dung: ").append(m.get("content")).append("\n")
                            .append("--------------------------------------------------\n");
                }

                bundleMap.put(uname, new UserBundle(accountInfo, activityData, txtInfo.toString(), txtMsg.toString()));
                summaryList.add(accountInfo);
            }

            // Generate README.md
            StringBuilder readme = new StringBuilder();
            readme.append("# THƯ MỤC RESOURCE QUẢN LÝ TÀI KHOẢN VÀ LỊCH SỬ HOẠT ĐỘNG (R-CONNECT)\n\n")
                    .append("Thư mục này lưu trữ thông tin tài khoản, mật khẩu, thời gian đăng ký và toàn bộ lịch sử hoạt động (tin nhắn đã gửi, bạn bè) của từng người dùng trong hệ thống R-Connect.\n\n")
                    .append("## 1. Danh sách tài khoản\n\n")
                    .append("| User ID | Tên đăng nhập | Tên hiển thị | Mật khẩu | Vai trò | Thời gian đăng ký |\n")
                    .append("| :---: | :---: | :---: | :---: | :---: | :---: |\n");
            for (Map<String, Object> a : summaryList) {
                readme.append("| `").append(a.get("userId")).append("` | **`").append(a.get("username")).append("`** | ")
                        .append(a.get("displayName")).append(" | `").append(a.get("password")).append("` | ")
                        .append(a.get("role")).append(" | ").append(a.get("registrationDate")).append(" |\n");
            }
            readme.append("\n## 2. Cấu trúc thư mục\n\n")
                    .append("Mỗi tài khoản có một thư mục riêng biệt mang tên người dùng:\n")
                    .append("- `account_info.json`: Thông tin chi tiết tài khoản dạng JSON.\n")
                    .append("- `account_info.txt`: Thông tin tài khoản dạng văn bản dễ đọc.\n")
                    .append("- `activity_history.json`: Toàn bộ dữ liệu hoạt động (tin nhắn đã gửi, danh sách bạn bè).\n")
                    .append("- `message_history.txt`: Nhật ký các tin nhắn đã gửi theo dòng thời gian.\n\n")
                    .append("## 3. Cơ chế tự động đồng bộ\n")
                    .append("Hệ thống tự động cập nhật các file trên ngay khi có bất kỳ tin nhắn mới hoặc tài khoản mới nào được tạo trong ứng dụng mà không cần thao tác thủ công.\n");

            String summaryJson = MAPPER.writeValueAsString(summaryList);
            String readmeStr = readme.toString();

            // Write to all active target directories
            for (Path baseDir : activeTargets) {
                try {
                    Files.createDirectories(baseDir);
                    for (Map.Entry<String, UserBundle> entry : bundleMap.entrySet()) {
                        String uname = entry.getKey();
                        UserBundle bundle = entry.getValue();
                        Path userDir = baseDir.resolve(uname);
                        Files.createDirectories(userDir);

                        Files.writeString(userDir.resolve("account_info.json"), MAPPER.writeValueAsString(bundle.accountInfo()), StandardCharsets.UTF_8);
                        Files.writeString(userDir.resolve("account_info.txt"), bundle.txtInfo(), StandardCharsets.UTF_8);
                        Files.writeString(userDir.resolve("activity_history.json"), MAPPER.writeValueAsString(bundle.activityData()), StandardCharsets.UTF_8);
                        Files.writeString(userDir.resolve("message_history.txt"), bundle.txtMsg(), StandardCharsets.UTF_8);
                    }

                    Files.writeString(baseDir.resolve("accounts_summary.json"), summaryJson, StandardCharsets.UTF_8);
                    Files.writeString(baseDir.resolve("README.md"), readmeStr, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    LOG.log(Level.FINE, "Failed writing resources to " + baseDir, ex);
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Export query failed", ex);
        }
    }

    private record UserBundle(Map<String, Object> accountInfo, Map<String, Object> activityData, String txtInfo, String txtMsg) {}
}
