# Nguyên tắc thiết kế NẾP NHÀ

Tham khảo bộ quy tắc mã nguồn mở **Taste Skill** (github.com/Leonxlnx/taste-skill, bản 2.5.1 và nhánh classics:
minimalist-ui, high-end-visual-design). Bộ này viết cho website; dưới đây là những nguyên tắc chuyển được sang
ứng dụng Android cho người trung niên, cao tuổi, và những gì cố ý KHÔNG áp dụng.

## Áp dụng

| Nguyên tắc Taste Skill | Cách làm trong Nếp Nhà |
|---|---|
| Không dùng font mặc định (Roboto/Inter) | Be Vietnam Pro cho nội dung (thiết kế riêng cho dấu tiếng Việt), Spectral SemiBold cho tiêu đề. Cắt bớt còn Latin + tiếng Việt (~410 KB). |
| Tiêu đề serif, khoảng chữ hơi khít, dòng 1.1–1.2 | Tiêu đề Spectral, letterSpacing âm nhẹ, dòng ~1.2; nội dung dòng 1.5. |
| Chữ không đen tuyệt đối, chữ phụ xám ấm | Mực #292522, chữ phụ #5C534C (đạt tương phản AA trên nền kem). |
| Màu là tài nguyên hiếm | Đỏ trầm chỉ cho nút chính, ngày được chọn, viền hôm nay, Chủ nhật/ngày lễ, cảnh báo. Tiêu đề màu mực; chip đã chọn màu nâu gỗ. |
| Viền mảnh, gần như không đổ bóng | Viền thẻ = nâu gỗ 12%, không bóng; nút không đổ bóng. |
| Một hệ thống: một kiểu nút chính + phụ, một kiểu thẻ, một thang bo góc | Nút 14dp, thẻ 14dp, thẻ nổi bật 20dp/14dp, chip 12dp. |
| Thẻ "hai lớp" (vỏ + lõi, bo góc đồng tâm) cho thứ quan trọng nhất | Chỉ thẻ "Hôm nay" ở Trang chủ, kèm khung góc truyền thống. |
| Nhãn nhỏ viết hoa trên tiêu đề (eyebrow) | "HÔM NAY · THỨ BẢY", "ÂM LỊCH" – cỡ 14sp, giãn chữ, màu đồng. |
| Nhãn trạng thái nền pastel nhạt | Nhãn loại sự kiện: nền màu loại 12%, chữ cùng màu đậm. |
| Chuyển động tiết chế: lối vào một lần, phản hồi nhấn | Thẻ Hôm nay hiện dần một lần; mọi nút/thẻ bấm được thu nhỏ 0.98 khi nhấn. Chỉ dùng transform/opacity. |
| Tôn trọng "giảm chuyển động" | Máy RAM thấp hoặc khi bật "Giảm hiệu ứng": tắt lối vào, tắt khung góc. |
| Không emoji, không chữ mẫu, nút là động từ + đối tượng | "Thêm sự kiện", "Gửi yêu cầu tham gia", "Tạo mã mời". |
| Vùng chạm ≥ 44px | Giữ chuẩn riêng cao hơn: nút 56dp, vùng chạm ≥ 48dp. |

## Cố ý không áp dụng

- Chữ nhỏ 10–12px, nhãn siêu nhỏ: trái với chuẩn cho người lớn tuổi (tối thiểu 14sp, nội dung 18–20sp).
- Hiện dần từng mục khi cuộn, kính mờ (blur), hạt nhiễu, hiệu ứng shader: tốn GPU trên máy yếu và gây rối mắt.
- "Một màn hình một phần", ảnh lớn, khung trình duyệt: quy tắc của trang web giới thiệu, không phải ứng dụng.
- Dải hoa văn ở mọi đầu trang: chỉ giữ ở Trang chủ và màn khởi động để không lạm dụng họa tiết (theo đặc tả).

## Giấy phép font

Be Vietnam Pro và Spectral dùng SIL Open Font License 1.1 – xem `docs/licenses/`.
