# R-Connect — Nền Tảng Mạng Xã Hội Desktop & Gọi Video P2P

Ứng dụng mạng xã hội và liên lạc thời gian thực cho máy tính (Desktop Application), được xây dựng trên nền tảng **Java 17**, giao diện đồ họa **Swing / FlatLaf (Dark Obsidian Theme)**, kết hợp kiến trúc mạng lai **Client–Server qua TCP/IP** và **LAN Discovery / Realtime Streaming qua UDP**.

---

## 🌟 Tính Năng Nổi Bật

### 1. 🎥 Gọi Video & Gọi Thoại P2P (UDP Real-time Streaming)
- **Hỗ trợ phần cứng camera vật lý:** Kết nối trực tiếp webcam máy tính (Sarxos Webcam API), khởi tạo an toàn trên luồng nền và tắt đèn phần cứng ngay khi người dùng tắt camera.
- **Nâng cấp cuộc gọi linh hoạt & Chia sẻ màn hình (Screen Share):** Bắt đầu gọi thoại riêng tư (không tự mở camera) và có thể bật Camera hoặc Chia sẻ màn hình bất kỳ lúc nào trong cuộc gọi.
- **Gọi thoại P2P chất lượng cao:** Truyền tải âm thanh PCM 16-bit / 16 kHz với độ trễ thấp, tự động ngắt chuông sau 45 giây nếu không có phản hồi.
- **Tự động Fallback:** Chế độ thẻ đại diện đồ họa thông minh khi camera đang bận hoặc chạy nhiều client trên cùng một thiết bị.

### 2. 💬 Trò Chuyện Đa Chế Độ & Truyền Tệp Tin Cậy (TCP + UDP)
- **3 chế độ truyền tin:** Tin nhắn riêng 1–1 (*Unicast*), Nhóm chat công khai & mời bạn bè (*Group Multicast tầng ứng dụng*), và Kênh thông báo hệ thống một chiều từ Quản trị viên (*Admin Broadcast*).
- **Truyền tệp & Đa phương tiện:** Gửi ảnh (có xem trước trực tiếp trong khung chat), tin nhắn thoại (*Voice clip*), tài liệu lớn chia chunk 48 KiB hỗ trợ **tạm dừng / tải tiếp (Resume)** và xác thực toàn vẹn **`SHA-256`**.
- **Dò tìm máy chủ LAN & Chỉ báo đang nhập tin:** Quét tự động máy chủ trong mạng LAN (`UDP 5001`) và phản hồi trạng thái đang gõ phím có ký xác thực HMAC (`UDP 5002`).

### 3. 🌐 Mạng Xã Hội, Quản Trị & Đa Ngôn Ngữ (Tiếng Việt / English)
- **Giao diện song ngữ tức thời (i18n Việt – Anh) & Dịch tin nhắn Hybrid:** Chuyển đổi ngôn ngữ toàn bộ giao diện ngay lập tức (lưu riêng theo từng tài khoản); tự động dịch tin nhắn hai chiều kết hợp API trực tuyến và từ điển cụm từ Offline mở rộng cho mạng LAN nội bộ.
- **Bảng tin & Tương tác:** Đăng bài kèm tối đa 4 ảnh, phân quyền xem (*Công khai / Bạn bè / Chỉ mình tôi*), thả tim, bình luận, kết bạn, chặn hai chiều và thông báo thời gian thực.
- **Kiểm duyệt & Quản trị hệ thống (`ADMIN`):** Người dùng gửi báo cáo vi phạm; Quản trị viên tiếp nhận (*Claim*), ẩn bài viết/bình luận, khóa/mở khóa tài khoản và tra cứu **Nhật ký kiểm toán (*Audit Logs*)**.

### 4. ⚡ Tự Động Đồng Bộ Tài Nguyên Tài Khoản
- Tự động xuất và cập nhật thông tin tài khoản cùng nhật ký hoạt động ra định dạng `JSON` và `TXT` trong thư mục `resources/accounts/` (tách biệt hoàn toàn với môi trường kiểm thử JUnit).

---

## 🏗️ Kiến Trúc Giao Thức Mạng

| Giao thức | Cổng mặc định | Mục đích & Chức năng |
| :--- | :---: | :--- |
| **TCP** | `5000` | Kết nối Client–Server tin cậy (`WireCodec v2`): Xác thực, Bảng tin, Chat Unicast/Group/Broadcast, Truyền tệp Resume/SHA-256, Quản trị hệ thống. |
| **UDP (Control)** | `5001`, `5002` | Quét tìm máy chủ tự động trong mạng LAN (`DISCOVER`/`OFFER` cổng `5001`) & Chỉ báo đang nhập tin xác thực HMAC (`TYPING` cổng `5002`). |
| **UDP (P2P Media)** | `Dynamic` | Truyền phát luồng âm thanh Voice Call PCM, hình ảnh Video Call JPEG chunking và Screen Share trực tiếp giữa hai điểm cuối. |

---

## 🚀 Hướng Dẫn Khởi Động Nhanh

### Yêu Cầu Môi Trường
- **Hệ điều hành:** Windows 10/11 (khuyến nghị), macOS, hoặc Linux.
- **Java Development Kit (JDK):** Phiên bản **17** trở lên.
- **Apache Maven:** Phiên bản **3.9+** (khi cần biên dịch lại mã nguồn).

### Cách 1: Chạy trực tiếp trên Windows (Đã đóng gói sẵn JAR)
1. **Khởi động Máy chủ & Bảng điều khiển (Server Dashboard):**
   - Nhấp đúp vào **`run-server.bat`**
2. **Khởi động Ứng dụng Người dùng (Desktop Client):**
   - Nhấp đúp vào **`run-client.bat`** (có thể mở đồng thời 2–3 cửa sổ Client trên cùng máy để demo chat, gọi điện và đổi ngôn ngữ độc lập).

### Cách 2: Biên dịch và kiểm thử bằng Maven
```powershell
mvn clean package
```

---

## 👥 Tài Khoản Trải Nghiệm Mặc Định

Bạn có thể đăng nhập ngay bằng các tài khoản có sẵn bên dưới hoặc bấm **"Đăng ký ngay"** trên giao diện:

| Tên đăng nhập (Username) | Mật khẩu (Password) | Vai trò | Ghi chú |
| :---: | :---: | :---: | :--- |
| **`admin`** | `1234567890` | **Quản trị viên (`ADMIN`)** | Phát thông báo toàn hệ thống (*Public Channel*) & Quản trị kiểm duyệt (*Report / Audit Log*) |
| **`Dawtrin`** | `1234567890` | Người dùng (`USER`) | Tài khoản người dùng mẫu |
| **`RinnSan`** | `1234567890` | Người dùng (`USER`) | Tài khoản người dùng mẫu |
| **`test1`** | `1234567890` | Người dùng (`USER`) | Tài khoản người dùng mẫu |

---

## 📁 Cấu Trúc Dự Án

```text
Chat-CS-P2P/
├── common/                  # Giao thức chung, Codec đóng gói khung tin (WireCodec TCP & UdpCodec HMAC)
├── server/                  # Máy chủ trung tâm, SQLite WAL, Media ACL, Bộ dịch Hybrid & Server Dashboard
├── client/                  # Giao diện FlatLaf Swing song ngữ Việt/Anh, P2P Voice/Video/Screen Streamer
├── data/                    # Cơ sở dữ liệu SQLite (social-network.db) và kho lưu trữ tệp (data/media/)
├── resources/               # Dữ liệu đồng bộ tài khoản & lịch sử hoạt động (resources/accounts/)
├── docs/                    # Tài liệu đặc tả kiến trúc, mô hình dữ liệu, giao thức và nghiệp vụ
├── run-server.bat           # Khởi chạy nhanh Máy chủ kèm Server Dashboard
├── run-client.bat           # Khởi chạy nhanh Ứng dụng Client
└── pom.xml                  # Cấu hình Maven đa mô-đun (Multi-module)
```
