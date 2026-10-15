# Layout nhị phân — 영웅서기4 (KTF / LGT / Android)

Tái lập: `python3 -I tools/binlayout.py <ktf client.bin> <lgt binary.mod> <android libbinary.so>` → `docs/binlayout_output.txt`.
Nhãn: **CONFIRMED** (có offset/trace), **INFERRED** (nêu căn cứ), **UNKNOWN**.

Quy ước địa chỉ cho bản KTF trong toàn dự án: **`img+X`** = offset tính từ đầu image (file offset `0x1104 + X`).
Project cũ dùng địa chỉ nạp 0x100000 cho cả file ⇒ `addr_cũ = 0x101104 + X`.

## 1. KTF `client.bin387872` (mục tiêu chính) — KHÔNG phải ELF

| Thành phần | Giá trị | Nhãn | Bằng chứng |
|---|---|---|---|
| Định dạng | raw image Thumb, tự relocate | CONFIRMED | magic `04 e0 c0 46` = `b.n 0xc ; mov r8,r8`; loader file 0x0c–0x62 |
| Header 8 byte @file 0x04 | `24 02 04 20 01 00 02 00` | UNKNOWN | không được loader đọc |
| Bảng relocation | @file 0x70: u32 count = 1060, rồi 1060 × u32 offset (image-relative) | CONFIRMED | file 0x0e–0x18 tính địa chỉ; vòng 0x22–0x2e: `[r0+off] += r0` |
| Image start (r0) | file 0x1104, dài 0x89384 (= hết file) | CONFIRMED | file 0x20 `adds r0,r1,r3` |
| Image header @img+0 | `{ptrtab_start=0x88bb4, ptrtab_end=0x89384, bss_start=0x89384, bss_end=0xe7ea4, desc=0x881a8}` | CONFIRMED | loader đọc [r0],[r0+4],[r0+8],[r0+0xc],[r0+0x10] |
| Bảng con trỏ (ptrtab / "GOT") | img+0x88bb4..0x89384, 500 entry; mỗi entry trỏ tới 1 word được cộng r0 | CONFIRMED | vòng file 0x30–0x3e |
| GOT base cho mã PIC | img+0x88bb4 | CONFIRMED | literal `str − 0x88bb4` tham chiếu đúng chuỗi trong 252 vị trí (xem `docs/xmatch_strings_ktf_so.tsv`); khớp "sl=0x189cb8" của project cũ |
| BSS | img+0x89384..0xe7ea4, **387872 B = hậu tố tên file** | CONFIRMED | file 0x40–0x4a: `r3=[r0+0xc]-[r0+8]; cmp r1(arg),r3; beq` — sai kích thước ⇒ trả 0 |
| BSS được zero | có | CONFIRMED | vòng file 0x52–0x5c |
| Giá trị trả về loader | `desc` = img+0x881a8 (struct, không phải code) | CONFIRMED | file 0x5e `ldr r0,[r0,#0x10]` |
| Descriptor img+0x881a8 | `{0x88188 (typeinfo "ExeInterface"), 0x861fc "WIPI_exe", 1, 0, fn 0x769b9, fn 0x76db1, 0,0, fn 0x76f09, 0, 0x769bd×…}` | CONFIRMED (dữ liệu) / INFERRED (ý nghĩa: bảng hàm interface KTF kiểu `wie`) | dump img+0x881a8 |
| Hằng IEEE754 | 0x7f800000, 0xff800000, 0x7fc00000, 0x7ff00000_00000000… ngay sau descriptor | CONFIRMED (có mặt) | img+0x881f0.. — runtime float/double hỗ trợ (Java/soft-float) |
| Vùng chuỗi C/dữ liệu | khoảng img+0x806b4..0x86c60 (chuỗi đường dẫn tài nguyên, format) | CONFIRMED | `tools/xmatch_strings.py` |
| Metadata lớp Java (KTF WIPI-Java) | `Clet`, `Clet$CletCard`, `org/kwis/msp/lcdui/*`, `java/lang/*Exception`, chữ ký `(IIII)V+paintClet`… img+0x86208..0x871f0 | CONFIRMED (chuỗi) / INFERRED (lớp Java biên dịch AOT ⇒ Clet là lớp vỏ Java gọi mã C) | strings |
| Interface nền tảng | `WIPI_JBInterface`, `WIPIC_knlInterface`, `WIPICX_incMemInterface` | CONFIRMED (chuỗi) | img+0x86288.. |
| Ranh giới code/data chính xác, danh sách hàm | — | UNKNOWN | Phase B |

## 2. LGT `binary.mod` (đối chiếu) — ELF32 ARM EXEC
CONFIRMED (`binlayout_output.txt`): entry 0x77504, flags 0x206, không có program header, không symbol.
`.text` @0x1000 size 0x7c142 (file 0x34), `.rel.text` 12030×R_ARM_ABS32 + 1×type15, `.data` @0x1400000 size 0x10ac,
`.rel.data` 159×ABS32 + 84×type1, `.bss` @0x1500000 size 0x67778 (424824 B), `.raptor` (0x4c byte, chưa rõ — UNKNOWN).

## 3. Android `libbinary.so` (đối chiếu, **có symbol**) — ELF32 ARM DYN
CONFIRMED: GCC 4.2.1, EABI4, `.symtab` 3341 symbol / **2388 hàm có tên**, toàn bộ ở chế độ ARM (không Thumb).
Phụ thuộc `liblgt_system.so` (lớp WIPI-C của LG U+ trên Android) ⇒ port từ nhánh LGT (INFERRED; 1774/1892 tài nguyên trùng hash với LGT, 1674 với KTF).
`res/raw/jarfile.mp3` thực chất là ZIP (magic `PK`) chứa `binary.mod` (ELF, hash khác LGT) + `res/` — cùng tập chuỗi với `.so` (1019/1019) ⇒ cùng phiên bản mã (INFERRED).

Import WIPI-C (CONFIRMED, `tools/so_floatscan.py`): `MC_fs*`, `MC_grp*`, `MC_knl*`, `MC_mda*`, `MC_net*`, `MC_phnCallPlace`, `MC_util*`, `dlib_*`, `wipic_get_frame_*`.

### Float / fixed-point
Soft-float helper chỉ được gọi bởi 6 hàm (CONFIRMED trên `.so`):
`make_Lenz_array`, `make_Warp_array` (tạo bảng hiệu ứng), `_sin`, `_sqrt`, `MGHEO_sqrt`, `DM_ConvertHSBtoRGB`.
Còn lại là số nguyên (`__aeabi_idiv` 60 hàm, `idivmod` 29, `ldivmod` 4); có `Cos100` (cos ×100, bảng/integer).
⇒ INFERRED cho KTF: logic game là số nguyên/fixed-point; float chỉ ở khởi tạo bảng → có thể tính trước và so bit-exact. Cần xác minh trên KTF ở Phase B.

## 4. Ghép tên hàm KTF ↔ .so
`tools/xmatch_strings.py`: 1292 chuỗi rodata `.so`, 315 có trong KTF, 252 vị trí literal KTF ↔ **155 tên hàm `.so`** khác nhau.
Ví dụ (CONFIRMED chuỗi + literal, INFERRED tên): img+0x22a04 ∈ `SCENE_Change` (`/MAP/SC/e%04d_scn`), img+0x76874 ∈ `nFPS_Render`,
img+0x6b028 ∈ `loadTitleImage`, img+0x26870 ∈ `FR_Load`, img+0x1ec94 ∈ `EnemyAIParsing_LoadData`.
Thứ tự hàm trong KTF có vẻ theo thứ tự module nguồn (Boss_* → DM_* → Event_* → FR_* …) ⇒ lan truyền qua call graph sẽ gán được nhiều hơn (Phase B).
