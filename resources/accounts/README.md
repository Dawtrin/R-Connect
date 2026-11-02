# THƯ MỤC RESOURCE QUẢN LÝ TÀI KHOẢN VÀ LỊCH SỬ HOẠT ĐỘNG (R-CONNECT)

Thư mục này lưu trữ thông tin tài khoản, mật khẩu, thời gian đăng ký và toàn bộ lịch sử hoạt động (tin nhắn đã gửi, bạn bè) của từng người dùng trong hệ thống R-Connect.

## 1. Danh sách tài khoản

| User ID | Tên đăng nhập | Tên hiển thị | Mật khẩu | Vai trò | Thời gian đăng ký |
| :---: | :---: | :---: | :---: | :---: | :---: |
| `1` | **`test1`** | đạt | `1234567890` | USER | 2026-10-26 01:04:04 (GMT+7) |
| `2` | **`Dawtrin`** | Dawtrin | `1234567890` | USER | 2026-11-01 11:48:19 (GMT+7) |
| `3` | **`RinnSan`** | RinnSan | `1234567890` | USER | 2026-11-01 15:48:19 (GMT+7) |

## 2. Cấu trúc thư mục

Mỗi tài khoản có một thư mục riêng biệt mang tên người dùng:
- `account_info.json`: Thông tin chi tiết tài khoản dạng JSON.
- `account_info.txt`: Thông tin tài khoản dạng văn bản dễ đọc.
- `activity_history.json`: Toàn bộ dữ liệu hoạt động (tin nhắn đã gửi, danh sách bạn bè).
- `message_history.txt`: Nhật ký các tin nhắn đã gửi theo dòng thời gian.

## 3. Cách cập nhật đồng bộ
Khi có thêm người dùng mới hoặc có thêm tin nhắn gửi trong ứng dụng, có thể chạy lại script đồng bộ:
```bash
python tools/export_user_resources.py
```
