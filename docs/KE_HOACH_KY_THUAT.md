# NẾP NHÀ – Kế hoạch kỹ thuật & tình trạng

Phiên bản tài liệu: 0.1 (22/11/2026)

## 1. Quyết định kỹ thuật chính (giải thích ngắn)

| Quyết định | Lý do |
|---|---|
| Kotlin + Jetpack Compose, 1 Activity | Theo đặc tả; giao diện native, không WebView. |
| Chia module Gradle: `:core:lunar`, `:core:kinship`, `:app` | Lịch âm và vai vế là nghiệp vụ thuần, không phụ thuộc Android → kiểm thử nhanh, tái dùng cho máy chủ sau này. |
| MVVM + Clean (data / domain / ui / reminder) | Tách giao diện, nghiệp vụ, dữ liệu, thông báo. Đồng bộ sẽ là gói `sync` riêng. |
| DI thủ công (`AppContainer`, khởi tạo lười) thay vì Hilt | Khởi động nhanh hơn, APK nhỏ hơn trên máy yếu; số phụ thuộc còn ít. Có thể chuyển sang Hilt khi module gia tộc lớn lên. |
| Room + WAL, không bao giờ dùng `fallbackToDestructiveMigration` | Không mất dữ liệu khi app bị đóng đột ngột hay khi nâng cấp. |
| AlarmManager (báo thức chính xác) + WorkManager (12 giờ/lần) + BootReceiver | Nhắc đúng giờ; tự khôi phục sau khởi động lại, đổi giờ, cập nhật app; hiện bù lần nhắc lỡ trong 12 giờ. |
| Supabase: RLS + hàm `SECURITY DEFINER` | Mọi quyền kiểm tra ở máy chủ trên từng bản ghi. App chỉ chứa anon key. |
| Mỗi bản ghi đồng bộ có `version` | Phát hiện xung đột, không ghi đè âm thầm (`update_person_checked` báo `CONFLICT`). |

## 2. Tối ưu cho máy yếu → mạnh

- Phát hiện máy RAM thấp (≤ 3 GB hoặc `isLowRamDevice`) → tự tắt hiệu ứng chuyển cảnh và họa tiết góc; người dùng cũng bật tay được.
- Họa tiết hồi văn, hoa mai, khung góc vẽ bằng `drawWithCache` (Path tính một lần) – không dùng ảnh bitmap.
- Lịch tháng: bảng âm lịch 42 ngày tính một lần trên luồng nền, giữ cache 6 tháng gần nhất; mọi sự kiện dùng chung bảng (`LunarSpan`).
- Danh sách dùng `LazyColumn` có `key` + `contentType`; Flow từ Room được `remember` để không thu thập lại khi vẽ lại.
- Không có thao tác mạng/CSDL trên UI thread (Room suspend/Flow, `flowOn(Dispatchers.Default)`).
- Theme nền tảng nhẹ (không AppCompat), màn khởi động hiện ngay nền kem + hoa sen, R8 rút gọn mã và tài nguyên.
- Cỡ chữ người dùng 100/115/130/150% nhân với cỡ chữ hệ thống; bố cục co giãn từ 320dp.

## 3. Cấu trúc mã

```
core/lunar     VietLunarCalendar (UTC+7), CanChi, tiết khí, Holidays, Recurrence (lặp âm/dương/tùy chỉnh), LunarSpan
core/kinship   FamilyGraph (cha mẹ–con, hôn nhân, xác nhận thứ bậc), KinshipResolver (suy vai vế)
app/data       Room (events, tasks, checklist, notification_log, scheduled_alarms, outbox), repositories, DataStore
app/domain     EventType, FamilyEvent, Occurrence
app/reminder   ReminderScheduler, ReminderReceiver, BootReceiver, RescheduleWorker, NotificationHelper, DeviceHealth (HyperOS)
app/ui         theme, components, nav (5 tab), home, calendar, event, tasks, clan, notifications, more
supabase/      migrations SQL (bảng, RLS, RPC, audit, storage)
```

## 4. Cơ sở dữ liệu

### Cục bộ (Room)
- `events`: sự kiện gia đình; lưu ngày gốc (âm hoặc dương) + quy tắc lặp + danh sách mốc nhắc. Ngày diễn ra được **tính**, không lưu cứng.
- `tasks`, `checklist_items`: việc gia đình, phân công, hạn, tiến độ, mẫu tái sử dụng.
- `notification_log`: nhật ký thông báo nội bộ, khóa `dedupeKey` (sự kiện|ngày|mốc) chống hiện trùng.
- `scheduled_alarms`: báo thức đang đặt, để hủy chính xác khi sửa/xóa sự kiện.
- `outbox`: hàng đợi thay đổi offline chờ đồng bộ (dùng ở giai đoạn gia tộc).

### Máy chủ (Supabase) – `supabase/migrations/20261122000001_nep_nha_core.sql`
- `profiles` (tài khoản) **tách** khỏi `persons` (hồ sơ trong gia phả; người đã mất vẫn tồn tại, có ngày giỗ âm).
- Quan hệ gốc: `parent_links` (ruột/nuôi, tối đa 2 cha mẹ ruột, trigger chống vòng lặp), `marriages` (hiện tại/ly hôn/góa, thứ tự vợ).
- `seniority_confirmations`: quản lý xác nhận ai lớn hơn khi thiếu dữ liệu → không suy đoán.
- `kinship_overrides`: cách xưng hô riêng theo phong tục gia đình.
- `clans` (mã định danh `NN-XXXXXX`), `clan_branches` (`order_index` = thứ tự chi, **không** phải đời), `clan_members` (trạng thái + vai trò + phạm vi chi), `invite_codes` (chỉ lưu băm SHA-256, hết hạn, giới hạn lượt, thu hồi).
- `clan_events`, `clan_tasks`, `notifications`, `devices` (token FCM), `audit_log`.
- 8 cấp quyền độc lập vai vế: Chủ gia tộc › Trưởng tộc › Phó trưởng tộc › Quản trị viên › Trưởng chi › Quản lý gia phả › Thành viên › Khách.
- RPC: `create_clan`, `create_invite`, `revoke_invite`, `request_join` (chống dò mã 10 lần/giờ), `decide_member`, `set_member_role`, `transfer_ownership`, `update_person_checked`, `leave_clan`.

## 5. Luồng chính

1. **Thêm ngày giỗ**: Trang chủ → Thêm sự kiện → loại Ngày giỗ (tự chọn Âm lịch, Hằng năm, nhắc 7–1–0 ngày) → nhập ngày âm → xem ngay ngày dương tương ứng và “Lần tới” → Lưu → xin quyền thông báo (Android 13+) → lịch nhắc được đặt.
2. **Nhắc**: báo thức đến → kiểm tra sự kiện còn tồn tại → ghi nhật ký (chống trùng) → hiện thông báo → đặt tiếp các mốc sau.
3. **Gia tộc (giai đoạn 2)**: đăng nhập → tạo gia tộc (thành Chủ) / nhập mã mời → khai báo, chọn chi, người thân liên quan → chờ duyệt → khi được duyệt mới đọc được dữ liệu.

## 6. Quy tắc phong tục đã cài (ghi rõ để gia đình kiểm tra)

- Giỗ lặp theo âm lịch: tháng thiếu không có ngày 30 → nhắc ngày 29; ngày gốc ở tháng nhuận → nhắc vào tháng thường cùng tên.
- Lặp hằng tháng theo âm lịch (mùng 1, rằm) tính cả tháng nhuận.
- Vai vế: anh/chị/em họ xét theo thứ bậc nhánh (con chú con bác), không theo tuổi. Miền Bắc: anh/chị của bố mẹ gọi Bác; miền Nam: bên ngoại gọi Cậu/Dì.

## 7. Tình trạng (trung thực)

### Đã hoàn thành trong mã (có kiểm thử đơn vị, CI chạy tự động)
- Lịch âm Việt Nam UTC+7: đổi âm↔dương, tháng nhuận, tháng thiếu/đủ, Can Chi, tiết khí, ngày lễ. Kiểm thử: 14 mốc đối chiếu thủ công + 2.486 đầu tháng âm 1900–2100 + đổi khứ hồi mọi ngày 1900–2100 (phát hiện và sửa một lỗi biên khi sóc rơi sát nửa đêm, ví dụ 07/05/2054).
- Lặp sự kiện âm/dương/hằng tháng/mỗi N ngày, nhiều năm liên tục.
- Bộ suy vai vế: ông bà nội/ngoại, cụ, cha mẹ (nuôi), anh chị em (cùng cha khác mẹ / cùng mẹ khác cha), cô dì chú bác cậu, anh chị em họ, con cháu chắt, vợ chồng, bố mẹ chồng/vợ, con dâu/rể, chị dâu/anh rể, thím/mợ, mẹ kế/bố dượng, thông gia; báo “cần xác nhận” khi thiếu dữ liệu; xưng hô tùy chỉnh.
- Ứng dụng phần Gia đình: Trang chủ, Lịch (ngày/tháng/năm, lọc), thêm/sửa/xóa sự kiện, nhắc nhiều mốc, việc gia đình + checklist + mẫu, thông báo nội bộ, cài đặt cỡ chữ, hướng dẫn HyperOS, thông báo thử.
- Lược đồ Supabase đầy đủ với RLS, RPC, audit, storage.

### Chưa hoàn thành (giai đoạn tiếp theo)
- Ứng dụng chưa có đăng nhập, tạo/tham gia gia tộc, màn gia phả (cây/thế hệ/sơ đồ của tôi), chi nhánh, phân quyền trên giao diện.
- Đồng bộ online/offline + giải quyết xung đột phía app (bảng `outbox` đã có, chưa có bộ đồng bộ).
- FCM (cần file `google-services.json` của bạn), Realtime.
- Sổ gia đình: hồ sơ người thân, ảnh, tài liệu, văn khấn, kiến thức phong tục.
- Sao lưu/khôi phục chủ động (hiện dựa vào Android Auto Backup), xóa tài khoản.
- SQL chưa được chạy thử trên một dự án Supabase thật; APK chưa được kiểm thử trên điện thoại thật (đặc biệt Xiaomi HyperOS).

## 8. Lộ trình

1. **Giai đoạn 1 (bản này)**: nền tảng, lịch âm, phần Gia đình, nhắc lịch, lược đồ máy chủ.
2. **Giai đoạn 2**: đăng nhập Supabase, tạo/tham gia gia tộc, xét duyệt, danh sách thành viên, phân quyền.
3. **Giai đoạn 3**: gia phả (3 chế độ xem, phân trang cho hàng nghìn hồ sơ), chi nhánh, vai vế trực tuyến.
4. **Giai đoạn 4**: đồng bộ offline-first + xung đột, Realtime, FCM, sự kiện/việc gia tộc.
5. **Giai đoạn 5**: Sổ gia đình, sao lưu/khôi phục, xóa tài khoản, kiểm thử trên nhiều máy (Xiaomi HyperOS, Samsung, máy 2–3 GB RAM).
