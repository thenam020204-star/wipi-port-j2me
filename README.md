# wipi-port-j2me — 크로이센 (Chroisen) LGT WIPI → J2ME + Việt hoá

Game: **크로이센 / Chroisen** (Softmaru, LGT WIPI-C, AID `0002CCCB`, PID `PD137477`, v01.00.03), gốc `kro/KRO.zip`.
Bản port chạy **nguyên mã ARM/Thumb gốc** (`binary.mod`) trong trình thông dịch Java CLDC 1.1, kèm AOT Thumb → Java.
Nền tảng WIPI-C được giả lập bằng HLE. Logic game không bị viết lại.

## Bản chuẩn hiện tại

| File | Nội dung |
|---|---|
| `release/CHROISEN_LGT_vi2.zip` | `CHROISEN_LGT_vi2.jar` + `.jad` (MIDP 2.0 / CLDC 1.1, 240x320, `HL4-FPS: 27`) — **Việt hoá toàn bộ** |
| `release/CHROISEN_LGT_vi1.zip` | bản trước (chỉ menu/UI/đồ), giữ để so sánh |

Nội dung bản vi2:
- **Việt hoá v1:** menu, UI, thông báo, đồ (IT01–32), kỹ năng, danh hiệu, mẹo, trợ giúp (Describ), tên quái/NPC (49x), MD0.
- **Việt hoá v2:** toàn bộ kịch bản sự kiện (`EV00–21`, 3493 câu), kịch bản quest (`QUEST1`, 2162), thoại NPC + bảng cảnh báo hầm ngục (`DIAL`, 401), tên + mô tả quest (`QUEST0/00`, 559). Không còn câu tiếng Hàn trong dữ liệu chữ.
- **Ảnh có chữ:** `title.dat`, `uiinner.dat`. Phụ đề "크로 이센" và nhãn độ tuổi trên ảnh tiêu đề được giữ nguyên có chủ đích.
- **Logo:** logo NAMP thay logo Softmaru.
- **Cash shop miễn phí offline:** mua xong game tự lưu.
- **Tính năng cần máy chủ** (xếp hạng, hộp quà, ký gửi, sự kiện): báo lỗi như mất mạng.

Phím:
- OK/FIRE = phím OZ (xác nhận).
- Phím mềm trái = CLR (menu/quay lại); đổi được bằng JAD `HL5-LSK: LSK`.
- `#` = bỏ qua thoại, `*` = minimap, SEND = lưu nhanh.

## Build

```bash
VI=1 ./make_kro.sh _vi2     # bản Việt hoá -> dist/CHROISEN_LGT_vi2.jar/.jad (+ quét J2ME)
./make_kro.sh _ko           # bản gốc tiếng Hàn (để so sánh)
NOAOT=1 VI=1 ./make_kro.sh _vi2_noaot   # không AOT (chậm ~9 lần, jar nhỏ hơn ~1 MB)
```

Cần JDK (javac, jar) và Python 3 + Pillow; `capstone` chỉ cần cho `tdis.py`.

Các bước của `make_kro.sh`:
1. Bung `kro/KRO.zip` vào `kro/jar`.
2. Chuyển âm thanh MMF → mid/wav (`SndConvert5`).
3. Việt hoá: `kro/vi/extract.py` → `build_vi.py` → overlay, rồi `kro/img/mk_images.py`.
4. `kroj/build.sh`: ecj `-source 1.3 -target 1.2` → ProGuard `-microedition` (preverify) → jar + jad.
5. `tools/j2me_scan.py`: quét tương thích J2ME, gặp vi phạm thì dừng.

So sánh jar trước/sau: `python3 tools/jardiff.py A.jar B.jar`.

Sinh lại AOT (khi đổi `kro/re/patches.txt` hoặc profile): `python3 kro/vi/build_vi.py --out kro/vi/build/overlay && bash kroj/tools/regen_aot.sh`, rồi chạy lại kiểm tra bit-exact (BAO-CAO §13).

Sửa bản dịch: mỗi nhóm có thư mục `kro/vi/dp/w/<EV|Q1|DIAL|QUEST>` (công cụ `dp`: `../../dp fix < file`, `../../dp check`), sau đó `bash dp/tr.sh <nhóm>` (chạy trong `kro/vi`) để gộp vào `tr/<nhóm>.tsv`. `build_vi.py` báo "Mã từ điển: N / 6298".

## Thư mục

| Đường dẫn | Nội dung |
|---|---|
| `kroj/` | engine (từ `xenj`/Zenonia): `src/core` (Cpu, Memory, Runtime, Heap, Gfx16, Smaf, TextEngine…), `src/core/hl4/lgt` (LgtRuntime = bảng WIPI-C LGT; KroGame/KroText = hook riêng của game), `src/midp` (MIDlet, GameCanvas, Sound), `aot/` (LgtAot sinh tự động), `profiles/`, `test/src/RunHeadless.java`, `tools/` |
| `kro/re/` | `formats.md` (định dạng + kiến trúc, có độ tin cậy), `map.tsv` (địa chỉ → tên, H/M/L + bằng chứng) |
| `kro/vi/` | `extract.py`, `build_vi.py`, `tr/*.tsv` (ID, Hàn, Việt), `dp/` (nguồn dịch tay + công cụ `dp`), `thuat-ngu.tsv`, `vai.md`, `font/f12.bin`, `vrun.sh` |
| `kro/img/` | `datpack.py` (bung/đóng gói `image/*.dat`), `mk_images.py` (vẽ lại ảnh có chữ + logo), `pixeltext/`, `fonts/` (NAM.P f12/f13c/f15c) |
| `kro/test/` | save mẫu (phòng mở đầu, đã có quyền điều khiển nhân vật ~105 s sau khi chạy kịch bản OK) |
| `tools/` | ecj, ProGuard, stub CLDC/MIDP, MicroEmulator, `j2me_scan.py`, `jardiff.py` |

## Chạy thử headless

```bash
(cd kroj && ./tbuild.sh build/t && AOTDIR=aot ./tbuild.sh build/ta)
K=...   # kịch bản phím "ms:KEY,..." (OK, CLR, UP, DOWN, LEFT, RIGHT, ...)
HL4_AOT=1 CP=kroj/build/ta kro/vi/vrun.sh ten 160000 4000 "$K" kro/test 26 14   # -> /tmp/krorun/ten.png
```

- Biến môi trường: `HL4_AOT`, `HL4_SVCLOG`, `HL4_TEXTLOG`, `HL4_PROFILE[_END]`, `HL4_CALLS`, `HL4_HEAPLOG`, `HL4_FPS`.
- Thử bằng class lấy từ jar: bung jar ra một thư mục, rồi đặt `RES=<dir> HL4_VI=<dir>/vi CP=<dir>:kroj/build/ta`.

## Việc tồn đọng / phiên sau

1. **Chưa kiểm được ở đây:**
   - J2ME Loader, KEmulator, máy thật (nhờ anh thử).
   - FreeJ2ME/MicroEmulator: anh đã yêu cầu dừng phần tự động hoá cửa sổ giả lập, nên chưa chạy.
2. **Bộ nhớ:** cần máy có heap Java lớn (ước tính ≥ 16 MB, xem BAO-CAO §Bộ nhớ). Máy S40 heap 2–4 MB sẽ không chạy được.
3. Âm thanh trên máy thật chưa nghe thử (chuyển đổi tự động, giống các bản port HL4/HL5).
4. Từ điển mã từ còn 1080 chỗ (5218/6298). Nếu cần thêm: gộp ~800 mã chỉ khác chữ hoa đầu từ.
5. Bản dịch v2 chưa được người đọc soát trong game từ đầu đến cuối (headless chỉ chụp được các đoạn đầu): nếu thấy câu tràn/xưng hô lệch, sửa bằng `dp fix` như trên.

6. Thẩm mỹ: khoảng trắng sau chữ số ASCII trong câu tiếng Việt hơi hẹp ("3 loại", "12 tuổi"), vì game tự vẽ chữ số trong ô 6 px. Có từ v1, vẫn đọc được. Muốn sửa thì vẽ chữ số bằng VnFont (thêm vào `VNP` ở cả `build_vi.py` và `KroText`), rồi phải soát lại toàn bộ ngắt dòng.

Chi tiết kỹ thuật, lỗi đã sửa và cách test: `BAO-CAO-KY-THUAT.md`.
