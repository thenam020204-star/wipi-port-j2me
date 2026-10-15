# Phase C — Âm thanh: SMAF (`SND/*_MMF`) → MIDI / WAV (AND2011)

Công cụ: `tools/smaf.py` (Python thuần, không dùng mã bên thứ ba). Kết quả kiểm: `docs/phaseC_audio_selftest.txt`.
Chuyển hàng loạt (đầu ra nằm trong `work/`, **không commit** vì là tài sản gốc đã biến đổi):
`python3 tools/smaf.py convert <res> <outdir>` → `BGM_xx.mid`, `EFF_xx.wav` (in sha256 từng file; chạy lại cho kết quả giống hệt).

## 0. Bối cảnh (CONFIRMED)
- Bản AND2011 chỉ phát **OGG** (`SM_CreateClip` → `audio/OGG`, Phase B) nhưng vẫn còn 42 file MMF cùng chỉ số: 20 nhạc nền (`BGM_00..19_MMF`) và 22 hiệu ứng (`EFF_00..21_MMF`).
- OGG không chạy được trên J2ME (MIDP 2.0 / MMAPI thường chỉ có MIDI, WAV, AMR, MP3), nên cần định dạng thay thế. **Đây là thay đổi hành vi có chủ đích** (xem §5).

## 1. Container SMAF (CONFIRMED — 42/42 file được tách hết đến byte cuối)
`'MMMD'` + `u32be size` (**tính cả CRC16 cuối**), rồi các chunk `{ id[4], u32be size, data }`:
`CNTI` (thông tin), `OPDA`, `MTR<n>` (track điểm nhạc), `ATR<n>` (track âm thanh). Không file nào có `MSTR` hay track đồ hoạ.

### 1.1 Track điểm nhạc `MTR` (20 file BGM)
`u8 formatType` (**luôn 2 = Mobile Standard, không nén**), `u8 sequenceType`, `u8 durationTimeBase`, `u8 gateTimeTimeBase`, 16 × `u8` trạng thái kênh
(trong dữ liệu đều có loại `NoCare`), rồi chunk con: `Mtsu` (setup: định nghĩa giọng FM của chip MA-3 dạng sysex Yamaha), `Mtsq` (chuỗi sự kiện).
Mã time base: `0:1 ms, 1:2 ms, 2:4 ms, 3:5 ms, 0x10:10 ms, 0x11:20 ms, 0x12:40 ms, 0x13:50 ms`; cả 20 file dùng `2` (**4 ms/tick**) cho duration và gate.

`Mtsq` = chuỗi `duration, event` (duration dạng độ dài biến thiên kiểu MIDI):
| trạng thái | ý nghĩa |
|---|---|
| `0x8n note, gate` | nốt dùng **vận tốc cuối cùng của kênh** (ban đầu 64) |
| `0x9n note, velocity, gate` | nốt + đặt vận tốc kênh |
| `0xBn control, value` | điều khiển (bank select `CC0=124` giọng FM / `CC0=125` bộ trống; `CC32`; 7, 10, 11…) |
| `0xCn program` | đổi giọng (số hiệu giọng MA-3) |
| `0xEn lsb, msb` | pitch bend |
| `0xF0 len bytes` | sysex; mỗi file có đúng 1 lệnh `43 79 06 7F 00 vv F7` = **âm lượng tổng** Yamaha |
| `0xFF 00` / `0xFF 2F 00` | nop / kết thúc |
Nốt tự mang **gate time** (không có note-off). Kênh 9 luôn là bộ trống (bank 125) ở mọi bài dùng nó; các kênh còn lại bank 124/1.
Tổng cộng 20 file chứa **20.010 nốt**.

### 1.2 Track âm thanh `ATR` (22 file EFF)
`u8 formatType, u8 sequenceType, u8 params, u8 reserved, u8 durationTimeBase, u8 gateTimeTimeBase`; trong đó `params = 0x11`: bit7 = 0 (mono), bit6..4 = 1 (định dạng **ADPCM Yamaha 4 bit**), bit3..0 = 1 (**8000 Hz**). Chunk con `AspI`, `Atsq` (đều 16 byte, chuỗi `st:`/`sp:`/`Atsq` — ý nghĩa UNKNOWN), `Awa<k>` (dữ liệu ADPCM, nibble thấp trước).
Giải mã (YM2608): `pred += step·diff[n]/8` (kẹp int16), `step = clamp(step·scale[n] >> 8, 127, 24576)`, khởi tạo `pred = 0, step = 127`; `diff = ±{1,3,…,15}`, `scale = {230×4, 307, 409, 512, 614}×2`.

## 2. Bộ chuyển đổi (cách làm)
- **WAV**: PCM 16 bit mono 8000 Hz từ `Awa`. **Khớp từng byte với bộ giải `adpcm_yamaha` của ffmpeg trên cả 22 file.**
- **MIDI** (SMF loại 0, 1000 ppq tại 60 bpm ⇒ **1 tick = 1 ms**): nốt-bật tại `tick`, nốt-tắt tại `tick + gate·gateTimeBase`; nốt có `gate = 0` bị bỏ.
  Control giữ lại: `1, 6, 7, 10, 11, 38, 64, 71, 74, 100, 101, 120, 121, 123, 126, 127`; bank select (CC0/CC32) **bỏ** (không thuộc GM); kênh có `CC0 = 125` là kênh trống → số chương trình ép về 0;
  sysex âm lượng tổng → GM master volume (INFERRED tương đương). Program change, pitch bend giữ nguyên.

## 3. Kiểm chứng
1. **Cấu trúc**: 42/42 file tách đúng đến byte cuối; mọi chuỗi sự kiện kết thúc bằng sự kiện `end`; MIDI sinh ra đạt kiểm tra nội bộ (mỗi note-on có note-off, kết thúc `End of Track`).
2. **Đối chiếu độc lập (công cụ bên thứ ba, chỉ dùng để kiểm; không phải một phần sản phẩm)**: `smaf-converter`/`vavi-sound` (Java) chạy trên cùng 20 file. So sánh *từng sự kiện theo mili-giây*:
   toàn bộ **20.010 nốt-bật và nốt-tắt (kênh, phím, vận tốc, thời điểm), program change, pitch bend và các control khác khớp tuyệt đối**. Khác biệt duy nhất: 646 sự kiện CC7 (âm lượng) ở thời điểm 0 mà công cụ tham chiếu gán sang kênh MIDI khác do logic hoán đổi kênh trống riêng; bản của ta giữ đúng kênh SMAF.
3. **Đối chiếu với bản OGG của chính game** (nguồn sự thật độc lập): độ dài bản MIDI so với OGG trùng gần tuyệt đối ở phần lớn bài (BGM_03 46,9/46,8 s; BGM_04 74,5/74,5; BGM_05 27,0/27,0; BGM_09 48,0/48,0; BGM_12 18,4/18,7; BGM_14 42,7/42,6; BGM_16 30,3/30,3; BGM_18 45,0/45,0; BGM_19 55,4/55,2), xác nhận time base 4 ms. Một số bài OGG dài hơn (BGM_02, 06, 10, 11: có phần lặp). Bản âm thanh hiệu ứng giải mã tương quan 0,93 với OGG tương ứng (EFF_00).

## 4. Chưa xác định
- `Mtsu` (định nghĩa giọng FM) chưa được diễn giải: âm sắc MIDI **sẽ khác** bản OGG/chip MA-3. Số `program 107` v.v. trong dữ liệu là số hiệu giọng FM tuỳ biến, không phải GM — dùng nguyên số là xấp xỉ.
- Ý nghĩa `AspI`/`Atsq` của track âm thanh; trường `CNTI`/`OPDA`.

## 5. Thay đổi hành vi & phương án (cần quyết định ở Phase F)
| phương án | ưu | nhược |
|---|---|---|
| (a) MIDI từ MMF (script ở đây) | rất nhẹ, mọi máy MIDP2 đều có | **âm sắc sai** (không có FM tuỳ biến) |
| (b) WAV từ OGG của game: `ffmpeg -i BGM_xx.ogg -ar 11025 -ac 1 -c:a pcm_u8 BGM_xx.wav` | **đúng âm thanh gốc** (đúng nội dung bản 2011) | JAR lớn (không giới hạn theo quyết định đã duyệt); WAV dài phát qua MMAPI tuỳ máy |
| (c) hiệu ứng: WAV 8 kHz từ MMF (script ở đây) | đúng dữ liệu gốc, nhỏ | — |
Khuyến nghị (INFERRED): hiệu ứng dùng (c); nhạc nền dùng (b) nếu máy đích phát WAV tốt, (a) làm dự phòng. Quyết định cuối cùng và việc "một kênh, hiệu ứng ngắt BGM" (Phase B) thuộc Phase F.
