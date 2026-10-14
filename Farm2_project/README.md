# The Farm 2 (딸팜2) — port SKT WIPI-Java → J2ME MIDP 2.0 / CLDC 1.1

Port thuần Java của bản SKT (WIPI-Java, `org.kwis.msp.*` + `com.skt.m.*`) sang J2ME,
thiết kế đa màn hình (đang kiểm thử thêm các độ phân giải ngoài 240x320). Bytecode game giữ nguyên, chỉ đổi các điểm gọi
nền tảng (danh sách đầy đủ trong `build/reports/remap_*.txt`).

Bản phát hành: `release/Farm2_port.jar` + `.jad`.

## Build

```
./build.sh Farm2_port
```

Pipeline (mỗi bước dừng build nếu lỗi):

| Bước | Việc | Báo cáo |
|---|---|---|
| 1b | Tách ảnh đóng gói → PNG sprite sheet (`tools/src/Unpack.java`) | `unpack_*.txt` |
| 2 | Biên dịch shim + host (Java 1.3 source, CLDC 1.1/MIDP 2.0 stub) | |
| 3 | Remap điểm gọi nền tảng + literal → `Str.lit` (`tools/src/Remap.java`) | `remap_*.txt` |
| 3c | Tách text dữ liệu → `.txt` UTF-8 + `index.bin` (`tools/textx.py`) | `textx_*.txt` |
| 3d | TextCheck: chạy `Enc.txt` của port trên 5200 chuỗi, phải khớp 100% | |
| 4 | Âm thanh SMAF → MIDI/WAV | |
| 5 | Preverify (ProGuard `-microedition`, StackMap CLDC) | |
| 7 | Scanner tương thích CLDC/MIDP (reject khi có lỗi) | `scan_*.txt` |
| 8 | Binary diff so với jar gốc | `bindiff_*.txt` |

## Sửa ảnh trực tiếp trong jar (không cần build lại)

Mọi ảnh từng bị đóng gói (LBMP trong `.dat`, PNG gói trong `.dat`/`.pet`/NPC) giờ là
**PNG thường**: mỗi file gói `Dir/Name.dat` → `Dir/Name.png` (sprite sheet chứa mọi khung,
theo thứ tự trái→phải, trên→dưới) + `Dir/Name.txt` (toạ độ khung).

```
# Image/Ch/ChOpen.png: frames of Image/Ch/ChOpen.dat
# frame x y w h
0 0 0 20 27
1 21 0 20 27
```

- Sửa PNG bằng trình vẽ bất kỳ (giữ nền trong suốt nếu khung có trong suốt).
- Muốn đổi kích thước/vị trí khung: sửa dòng tương ứng trong `.txt`.
- Mở jar bằng 7-Zip/WinRAR, thay file, lưu lại. Nhớ cập nhật `MIDlet-Jar-Size` trong `.jad`
  nếu cài bằng jad.
- Trong `.dat` mỗi ảnh được thay bằng tham chiếu cùng độ dài (`@PNG` + đường dẫn + số khung)
  nên mọi offset/kích thước game tính vẫn y nguyên; các byte không phải ảnh (tên, bảng size)
  giữ nguyên.

Tổng: 135 sheet, 2933 khung (2147 LBMP + 786 PNG). Mỗi khung được kiểm tra pixel-khớp
100% với bộ giải mã LBMP của shim lúc build. Jar chỉ tăng ~90 KB nhờ gom theo sheet
(tách 2933 file lẻ sẽ tăng ~860 KB — quá nặng cho máy thật).

## Sửa text trực tiếp trong jar (không cần build lại)

Toàn bộ text nằm trong thư mục `text/` của jar, UTF-8, mỗi dòng `khóa=nội dung`:

| File | Nội dung |
|---|---|
| `ui.txt` | chuỗi trong code game + lớp UI mới (menu, thông báo, nút…) |
| `talk_T0.txt`, `talk_B_PP.txt` | thoại NPC (theo file `Data/Talk/B.dat` phần `PP`) |
| `npc_name.txt`, `pet_name.txt`, `map_name.txt`, `crop.txt` | tên |
| `explan.txt` | trang hướng dẫn (`@` = xuống dòng) |
| `quest.txt`, `qexplan.txt`, `item.txt` | nhiệm vụ, gợi ý, vật phẩm nhiệm vụ |
| `skill.txt` (`N.0` tên, `N.1` mô tả), `petskill.txt` | kỹ năng |
| `popup.txt`, `popup1.txt` (`N.0`, `N.1`… các lựa chọn) | hộp chọn |
| `anim.txt` | chữ trong cut-scene ("1 năm sau…") |

Quy tắc:
- `\n` = xuống dòng, `\\` = dấu `\`. Dòng bắt đầu `#` là chú thích.
- Giữ nguyên mã điều khiển: `/C0`…`/C4` (màu), `/Nxx` (chèn tên), `/Pxx` ở cuối thoại (mở popup).
- Xoá một dòng → game dùng lại chữ gốc.
- Số đếm độ dài trong chuỗi ghép (popup, skill, cut-scene) được tính lại tự động.
  Giới hạn của game gốc: lựa chọn popup và tên skill tối đa 9 ký tự, mô tả skill/chữ
  cut-scene tối đa 99 (quá thì bị cắt và ghi log).
- `text/index.bin` là bảng tra (hash nội dung gốc → dòng), **không sửa**.

Cách hoạt động: build thay `new String(byte[])` ở 16 điểm đọc text dữ liệu bằng `Enc.txt`
(hash FNV-1a của byte gốc → `index.bin` → dòng trong `.txt`), và mỗi literal hiển thị
`ldc "…"` bằng `ldc "…"; sipush id; invokestatic Str.lit` (cùng nội dung → cùng id, nên
phép so sánh literal như `DownMent.equals("금액확인")` vẫn đúng sau khi sửa). Bộ nhớ:
`ui.txt` nạp một lần; file dữ liệu cache 2 file một lúc.

## Giao diện mới đa màn hình (lớp `Ui*`)

Mọi khung cũ cố định 176x202/240x320 (menu chính, hộp thoại NPC, hộp chọn, cài đặt,
hướng dẫn, chọn ô lưu, xác nhận WAP/mua/xếp hạng, menu trong game 5 thẻ, cửa hàng hạt
giống/mồi/nông sản, học kỹ năng, cửa hàng thú cưng, chọn đồ dự thi/thức ăn) được vẽ lại
trực tiếp theo kích thước màn hình (`src/ui/*.java`, không tạo ảnh đệm lớn):

- Build thay **thân** các hàm vẽ trong `tools/ui_body.txt` (vd. `GameCanvas.DrawGameMenu` →
  `UiGM.DrawGameMenu`): descriptor, access flag và mọi nơi gọi giữ nguyên; logic trạng thái
  /phím trong các hàm này được chép nguyên (CFR, đối chiếu FernFlower + bytecode).
- `tools/ui_hooks.txt`: các buffer 176x202 cũ chỉ còn 1x1 (tiết kiệm ~140 KB heap).
- Màn hẹp (chiều ngang logic < ~210, gồm 176x220 và 360x640 vốn chạy logic 207x368):
  thẻ chỉ hiện icon, tên thẻ hiện ở chân khung; lưới/ô kỹ năng tự co.
- Không cấp phát mỗi khung hình: số/tiền vẽ từ bộ đệm ký tự, ngắt dòng dùng mảng tĩnh.

## Save gốc trong gói

`FSave0.dat`, `FSave1.dat`, `Option.dat` của máy gốc được đóng vào `save/` trong jar.
Lần chạy đầu (chưa có store đánh dấu `Sseed`) mỗi file chưa có trong RMS được nhập vào,
sau đó là file RMS bình thường (xoá save sẽ không tự hiện lại). Tắt bằng
`Farm-Save-Seed: 0` trong JAD/manifest.

## Kiểm thử

- `test/emu.py` (FreeJ2ME headless + Xvfb): logo → title → menu → game mới → cut-scene →
  gameplay; ảnh từ PNG sheet, text từ `.txt`; đã thử sửa `.txt` trong jar (zip) và thấy
  thay đổi ngay (thoại, tên NPC, chuỗi UI).
- Móc kiểm thử `Farm-Debug-Open` (chỉ khi thêm vào manifest, bản phát hành không có):
  vd. `s0,s4,k0,p,g2` — mỗi lần về màn chơi, game mở lần lượt cửa hàng/menu bằng chính
  hàm của game (`LoadShopMenu`, `LoadSkillShop`, `LoadPetShop`, `LoadGameMenu`) để chụp
  mọi cửa hàng mà không phải đi tới NPC. (`s5` = chọn đồ dự thi cần sự kiện hội chợ dựng
  `c.point` trước, mở trực tiếp sẽ NPE — đó là giới hạn của móc test, không phải lỗi game.)
- Lỗi `Sequencer ... is null` trong log là do container không có MIDI synth (môi trường),
  không phải lỗi game.
