# R-Connect — Nền Tảng Mạng Xã Hội Desktop & Gọi Video P2P

Ứng dụng mạng xã hội và liên lạc thời gian thực cho máy tính (Desktop Application), được xây dựng trên nền tảng **Java 17**, giao diện đồ họa **Swing / FlatLaf (Dark Obsidian Theme)**, kết hợp kiến trúc mạng lai **Client–Server qua TCP/IP** và **P2P Streaming qua UDP**.

---

## 🌟 Tính Năng Nổi Bật

### 1. 🎥 Gọi Video & Gọi Thoại P2P (UDP Real-time Streaming)
- **Hỗ trợ phần cứng camera vật lý:** Tự động kết nối tới webcam máy tính (DirectShow Native / Sarxos Webcam API), phát hình ảnh mượt mà ở độ phân giải tối ưu 18–20 FPS.
- **Local PiP Preview tức thì:** Cửa sổ nổi phong cách Messenger hiển thị khung hình xem trước của chính bạn ngay khi bắt đầu cuộc gọi.
- **Chia sẻ màn hình trực tiếp (Screen Share):** Chuyển đổi linh hoạt giữa Camera và luồng truyền phát màn hình desktop qua UDP.
- **Gọi thoại P2P chất lượng cao:** Truyền tải âm thanh PCM 16-bit / 8000Hz với độ trễ cực thấp.
- **Tự động Fallback:** Chế độ thẻ đại diện đồ họa thông minh khi camera đang bận hoặc chạy nhiều client trên cùng một thiết bị.

### 2. 💬 Trò Chuyện & Nhắn Tin Tức Thời (TCP Socket)
- **Tin nhắn 1-1 riêng tư & Nhóm chat:** Quản lý hội thoại thời gian thực, hiển thị trạng thái đã gửi / đã nhận / đã đọc.
- **Kênh phát thanh (Broadcast):** Gửi thông điệp toàn hệ thống đến tất cả người dùng đang trực tuyến.
- **Truyền tệp & Đa phương tiện:** Gửi ảnh, tin nhắn thoại (Voice note), tài liệu có mã băm SHA-256 xác thực toàn vẹn dữ liệu.
- **Chỉ báo đang nhập tin (Typing Indicator):** Phản hồi thời gian thực qua UDP port chuyên dụng.

### 3. 🌐 Mạng Xã Hội Đa Tính Năng
- **Bảng tin & Bài viết:** Đăng tải bài viết, thiết lập quyền riêng tư (Công khai / Bạn bè / Riêng tư).
- **Tương tác:** Thả cảm xúc, bình luận bài viết, thông báo tương tác theo thời gian thực.
- **Quan hệ người dùng:** Tìm kiếm, kết bạn, chấp nhận/từ chối lời mời, chặn người dùng.

### 4. ⚡ Tự Động Đồng Bộ Dữ Liệu Thời Gian Thực (Zero-Touch Sync)
- Tự động xuất và cập nhật nhật ký tin nhắn và thông tin tài khoản ra các tệp định dạng chuẩn `JSON` và `TXT` trong thư mục `resources/accounts/` ngay khi gửi tin mà không cần thao tác thủ công.

---

## 🏗️ Kiến Trúc Hệ Thống

Hệ thống kết hợp tối ưu giữa hai giao thức mạng:

| Giao thức | Tỷ trọng | Mục đích & Chức năng |
| :--- | :---: | :--- |
| **TCP (Port 5000)** | **~90%** | Kết nối Client–Server tin cậy: Xác thực đăng nhập, Chat văn bản, Đăng bài, Truyền nhận tệp, Quản trị hệ thống. |
| **UDP (Port 5001, 5002, Dynamic)** | **~10%** | Dò tìm máy chủ trong mạng LAN (Discovery), Đàm thoại âm thanh & Video Call P2P, Chia sẻ màn hình, Typing indicator. |

---

## 🚀 Hướng Dẫn Cài Đặt & Chạy Ứng Dụng

### Yêu Cầu Môi Trường
- **Hệ điều hành:** Windows 10/11 (khuyến nghị), macOS, hoặc Linux.
- **Java Development Kit (JDK):** Phiên bản **17** trở lên.
- **Apache Maven:** Phiên bản **3.9+** (để đóng gói và biên dịch).

### Cách 1: Khởi động nhanh (Khuyên dùng trên Windows)

1. **Khởi động Máy chủ (Server):**
   - Nhấp đúp vào tệp: **`run-server.bat`**
   - Server sẽ mở cổng TCP `5000` và cổng UDP lắng nghe với Dashboard quản trị trực quan.

2. **Khởi động Ứng dụng người dùng (Client):**
   - Nhấp đúp vào tệp: **`run-client.bat`**
   - Có thể mở nhiều cửa sổ client cùng lúc trên một máy để trải nghiệm chat và gọi điện qua lại giữa các tài khoản.

---

### Cách 2: Biên dịch và chạy từ dòng lệnh (Maven)

1. **Biên dịch và đóng gói toàn bộ dự án:**
   ```powershell
   mvn clean package -DskipTests
   ```

2. **Khởi động Server:**
   ```powershell
   java -jar server/target/server-1.0.0-SNAPSHOT-shaded.jar
   ```

3. **Khởi động Client:**
   ```powershell
   java -jar client/target/client-1.0.0-SNAPSHOT-shaded.jar
   ```

---

## 👥 Tài Khoản Trải Nghiệm Mặc Định

Bạn có thể đăng nhập ngay bằng các tài khoản mẫu sẵn có hoặc bấm **"Đăng ký"** trực tiếp trên giao diện:

| Tên đăng nhập (Username) | Mật khẩu (Password) | Vai trò |
| :---: | :---: | :---: |
| **`Dawtrin`** | `1234567890` | Người dùng (USER) |
| **`RinnSan`** | `1234567890` | Người dùng (USER) |
| **`test1`** | `1234567890` | Người dùng (USER) |

---

## 📁 Cấu Trúc Dự Án

```text
R-Connect/
├── common/                  # Giao thức chung, Codec giải mã gói tin (JSON, TCP, UDP Wire)
├── server/                  # Máy chủ trung tâm, quản lý cơ sở dữ liệu SQLite, điều phối kết nối
├── client/                  # Giao diện FlatLaf Swing, bộ truyền phát Video/Voice Call UDP
├── resources/               # Bộ phông chữ chuẩn BeVietnamPro và dữ liệu đồng bộ tài khoản
├── run-server.bat           # Phím tắt khởi chạy nhanh Máy chủ
├── run-client.bat           # Phím tắt khởi chạy nhanh Ứng dụng Client
├── sync-resources.bat       # Phím tắt kiểm tra đồng bộ dữ liệu
└── pom.xml                  # Cấu hình xây dựng Maven đa mô-đun (Multi-module)
```
