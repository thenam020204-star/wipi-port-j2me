# Máy nghe MMF (công cụ phụ, không thuộc bản J2ME)

Trang web chọn file SMAF (.mmf), chuyển sang MIDI và phát bằng soundfont Nokia. Bản đã đăng: https://claude.ai/artifact/F62mTyTp6Ejhsm9iTBYjKo (riêng tư).

- `smaf.js`: bản JavaScript của `hl4.core.Smaf` (project cũ `HL4_project_2026-09-27j.zip`, `hl4j/src/core/hl4/core/Smaf.java`).
  Đã so **từng byte** với bản Java: 125 file MMF thật (AND2011 `work/res`, KTF `ktfjar`, LGT `lgtjar`) và 400 file bị cắt/sửa ngẫu nhiên → kết quả (MIDI/WAV/null) và byte giống hệt.
  Cách so: `javac -d cls Smaf.java Dump.java` (Smaf.java lấy từ zip cũ, package `hl4.core`), `java -cp cls Dump outj <file...>`, `node dumpn.js` (đọc `files.txt`, ghi `outn/`), rồi `diff -r outj outn`.
- `sf2synth.js`: bộ phát SF2 đơn giản (render offline): vùng preset/instrument, loop, tuning, pitch bend, envelope âm lượng, attenuation, pan, CC7/10/11, bank select. Không có modulator riêng, filter, LFO, reverb/chorus. Cao độ đã kiểm (A4 = 440 Hz).
- `page.src.html`: giao diện; build = thay `/*SMAF*/`, `/*SYNTH*/` bằng nội dung hai file JS. Soundfont đi kèm trang dưới dạng base64 (`Nokia.sf2.b64.txt`, không commit).

## Bản Android (APK)

Bản 1.1: 4 tab (Đang phát, Hàng chờ, Thư viện, Thư mục). Thư mục nguồn chọn qua Storage Access Framework, được nhớ và quét lại (cả thư mục con) mỗi lần mở; file `.mid/.midi/.kar/.mmf` theo đuôi, file không đuôi theo 4 byte đầu (`MMMD` / `MThd`). Xuất WAV (16-bit stereo 44,1 kHz cho MIDI; WAV gốc cho MMF chỉ có PCM) vào thư mục xuất chọn một lần. Lặp (tắt / cả hàng chờ / một bài), trộn bài, hàng chờ lưu lại giữa các lần mở.
Đã chạy thử trong Chromium với cầu nối `window.Android` giả lập (quét, phát MMF/MIDI, chuyển bài, lọc, xuất WAV: header và độ dài đúng). Chưa chạy trên máy Android thật.

`android/`: vỏ WebView (một Activity, chỉ có phần chọn file là code native) chạy đúng trang trên, soundfont đóng trong APK (`assets/sf2data.js`), không cần mạng (không xin quyền INTERNET; chữ dùng font hệ thống).
Build: `tools/mmfplayer/android/build_apk.sh <Nokia.sf2>` → `work/apk/MayNgheMMF.apk` (ký bằng khoá tự tạo `work/apk/mmf.keystore`, không commit; minSdk 21 = Android 5.0).
Trang trong APK đã chạy thử qua `file://` trên Chromium (chọn file, phát MIDI/WAV, nghe thử): không lỗi. Chưa chạy trên máy Android thật.
