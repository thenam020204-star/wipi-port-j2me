# PROGRESS — Chroisen (크로이센) LGT WIPI → J2ME + Việt hoá

Đọc file này đầu mỗi phiên (không đọc lại cả codebase). Chi tiết: `README.md`, `BAO-CAO-KY-THUAT.md`.

## Trạng thái (2026-10-14)

| Mốc | Trạng thái |
|---|---|
| Port (interpreter + HLE + AOT), offline, cash shop miễn phí | xong (v1) |
| Việt hoá v1: menu/UI/đồ/kỹ năng/trợ giúp/ảnh/logo | xong (v1) |
| Việt hoá v2: EV 3493/3493, QUEST1 2162/2162, DIAL 401/401, QUEST0/00 559/559 | xong |
| Soát chữ Hàn còn sót + sửa (IT11 CP949, Describ0 mục 0/11) | xong |
| Soát hình: khung tên quest (≤99 px), ngắt dòng Describ theo khung thật (lỗi v1), ô kiểm duyệt | xong |
| Build `dist/CHROISEN_LGT_vi2.jar`, j2me_scan 0 vi phạm, AOT sinh lại = giống hệt | xong |
| Kiểm chứng headless từ jar (bit-exact, cash, monkey) | `release/kiem-chung-vi2.txt` |
| Âm thanh/lưu/phím/FPS trên máy thật | chưa kiểm được (cần người dùng) |

## Quyết định đã chốt
- Phong cách: fantasy dễ đọc, Hán Việt nhẹ; tên người Latin (`kro/vi/names.tsv`); lớp nghề/quái/hầm ngục giữ tên tiếng Anh.
- Font NAM.P f12 (VnFont) trong ô 10x12 của game; dấu câu tách khỏi mã từ (trừ chuỗi binary B:); `` ` `` = "...".
- Mã từ 2 byte: lead 0xC9–0xFE + 0xA2,0xA3,0xA5–0xAF → tối đa 6298 mã, đang dùng 5218.
- Giới hạn hiển thị do build_vi kiểm: tên quest ≤ 99 px (`QUEST_TITLE_W`); Describ ≤ W−16 px và ≤ (W−16)/5 byte (`DESCRIB_BOX`, W theo nơi gọi 0x29e3c).
- Giữ nguyên có chủ đích: nhãn bản ghi DIAL (không hiển thị), câu `<도저히 못 참겠군!!<n>` trong EV04 (thẻ lạ, game gốc ẩn), phụ đề "크로 이센" trên ảnh tiêu đề.

## Sửa bản dịch
`cd kro/vi/dp/w/<EV|Q1|DIAL|QUEST>` → `../../dp fix < file` (dòng `số\tcâu`) → `../../dp check` → `cd kro/vi && bash dp/tr.sh <nhóm>` → `VI=1 ./make_kro.sh _vi2`.
Xưng hô: `kro/vi/dp/w/*/dp_work/vai.md`; thuật ngữ: `thuat-ngu.tsv`.

## Việc tiếp theo (nếu có phiên sau)
1. Nhận phản hồi từ máy thật/J2ME Loader của người dùng (ảnh lỗi nếu có).
2. Soát đọc toàn bộ thoại trong game (headless chỉ chụp đoạn đầu).
