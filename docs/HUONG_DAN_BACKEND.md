# Hướng dẫn cấu hình máy chủ (Supabase)

## 1. Tạo dự án
1. Vào https://supabase.com → New project. Chọn vùng gần Việt Nam (Singapore).
2. Lưu lại: **Project URL** và **anon public key** (Settings → API).
   - **Không bao giờ** đưa `service_role` key vào ứng dụng hay vào GitHub.

## 2. Tạo cơ sở dữ liệu
1. Mở SQL Editor → dán toàn bộ nội dung `supabase/migrations/20261122000001_nep_nha_core.sql` → Run.
2. Hoặc dùng Supabase CLI: `supabase link --project-ref <ref>` rồi `supabase db push`.
3. Kiểm tra: Table Editor phải có các bảng `clans`, `clan_members`, `persons`, `parent_links`…, và mỗi bảng hiện “RLS enabled”.

## 3. Đăng nhập
- Authentication → Providers: bật Email (và Phone nếu muốn đăng nhập bằng số điện thoại – cần nhà cung cấp SMS).
- Authentication → Sessions: có thể thu hồi phiên đăng nhập của người dùng tại đây.

## 4. Gắn vào ứng dụng
Cách 1 – trên máy: tạo file `secrets.properties` ở thư mục gốc (đã được bỏ qua khỏi git):
```
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_ANON_KEY=eyJ...
```
Cách 2 – GitHub Actions: repo → Settings → Secrets and variables → Actions → thêm `SUPABASE_URL`, `SUPABASE_ANON_KEY`.

## 5. Sao lưu
- Gói trả phí của Supabase có sao lưu hằng ngày + khôi phục theo thời điểm (PITR).
- Gói miễn phí: định kỳ chạy `supabase db dump -f backup.sql` và lưu nơi an toàn.

## 6. Ký APK chính thức (khi phát hành)
Thêm Secrets: `NEPNHA_KEYSTORE_PATH`, `NEPNHA_KEYSTORE_PASSWORD`, `NEPNHA_KEY_ALIAS`, `NEPNHA_KEY_PASSWORD`. Khi chưa có, APK được ký bằng khóa thử nghiệm (cài được nhưng không dùng để lên Google Play).
