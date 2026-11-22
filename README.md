# NẾP NHÀ – Lịch gia đình & Gia tộc

*Gìn giữ ngày tháng – Kết nối các thế hệ*

Ứng dụng Android native (Kotlin + Jetpack Compose) cho người Việt: lịch âm dương chuẩn Việt Nam (UTC+7), ngày giỗ, sinh nhật, nhắc trước nhiều mốc, việc gia đình; và (đang phát triển) gia tộc trực tuyến với gia phả, vai vế, chi nhánh, phân quyền.

## Lấy file APK
1. Mỗi lần đẩy mã lên nhánh `main`, GitHub Actions tự kiểm thử và build.
2. Vào tab **Actions** → workflow **Build NEP NHA APK** → lần chạy mới nhất → mục **Artifacts** → tải **NepNha-apk** (file zip chứa APK).
3. Có thể bấm **Run workflow** để build lại bằng tay.

## Build trên máy
Cần Android Studio (JDK 17, Android SDK 35):
```
gradle :core:lunar:test :core:kinship:test :app:testDebugUnitTest
gradle :app:assembleRelease
```

## Tài liệu
- [Kế hoạch kỹ thuật & tình trạng chức năng](docs/KE_HOACH_KY_THUAT.md)
- [Hướng dẫn cấu hình máy chủ Supabase](docs/HUONG_DAN_BACKEND.md)
- Lược đồ CSDL: `supabase/migrations/`

## Kiểm thử trên điện thoại (bắt buộc trước khi phát hành)
- Thêm giỗ âm lịch, nhắc 7/1/0 ngày; đặt giờ nhắc vài phút tới để thử thật.
- Khởi động lại máy → nhắc vẫn đến.
- Xiaomi/HyperOS: làm đủ các bước trong *Thêm → Nhắc lịch không bị chặn*, rồi khóa app trong đa nhiệm.
- Phóng chữ 150% và kiểm tra trên màn hình nhỏ.
