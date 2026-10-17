# Máy nghe MMF (công cụ phụ, không thuộc bản J2ME)

Trang web chọn file SMAF (.mmf), chuyển sang MIDI và phát bằng soundfont Nokia. Bản đã đăng: https://claude.ai/artifact/F62mTyTp6Ejhsm9iTBYjKo (riêng tư).

- `smaf.js`: bản JavaScript của `hl4.core.Smaf` (project cũ `HL4_project_2026-09-27j.zip`, `hl4j/src/core/hl4/core/Smaf.java`).
  Đã so **từng byte** với bản Java: 125 file MMF thật (AND2011 `work/res`, KTF `ktfjar`, LGT `lgtjar`) và 400 file bị cắt/sửa ngẫu nhiên → kết quả (MIDI/WAV/null) và byte giống hệt.
  Cách so: `javac -d cls Smaf.java Dump.java` (Smaf.java lấy từ zip cũ, package `hl4.core`), `java -cp cls Dump outj <file...>`, `node dumpn.js` (đọc `files.txt`, ghi `outn/`), rồi `diff -r outj outn`.
- `sf2synth.js`: bộ phát SF2 đơn giản (render offline): vùng preset/instrument, loop, tuning, pitch bend, envelope âm lượng, attenuation, pan, CC7/10/11, bank select. Không có modulator riêng, filter, LFO, reverb/chorus. Cao độ đã kiểm (A4 = 440 Hz).
- `page.src.html`: giao diện; build = thay `/*SMAF*/`, `/*SYNTH*/` bằng nội dung hai file JS. Soundfont đi kèm trang dưới dạng base64 (`Nokia.sf2.b64.txt`, không commit).
