# So sánh tốc độ: bản viết lại vs bản thông dịch ARM cũ (đo 2026-10-17)

Chưa có máy Nokia thật ⇒ chỉ đo trên PC (VM cloud 4 nhân). `-Xint` (JVM chỉ thông dịch) dùng làm **đại diện thô** cho máy yếu; không phải số đo của máy thật.

| Bản | Cảnh | JVM thường (JIT) | `-Xint` |
|---|---|---|---|
| Viết lại (dự án này), `Engine.tick` | trace M tick 5000–9000 (đánh quái, rơi đồ, level-up); `-Xint` đo 5000–7000 | **0,33 ms/khung** (max 3,2) | **6,0 ms/khung** (max 33) |
| Cũ `HL4_project_2026-09-27j` KTF, có AOT | `keys_walk` (đi lại/hội thoại, 1,5–2,8 triệu lệnh ARM/khung) | khoảng 4–7 ms/khung | khoảng 40–90 ms/khung |
| Cũ, không AOT | như trên | khoảng 16–49 ms/khung | khoảng 390–470 ms/khung |

Ngân sách 27 fps = 37 ms/khung. Bản cũ khi chơi cần 1,5–2,8 triệu lệnh ARM/khung (đỉnh 5,6 triệu) ⇒ 40–75 MIPS khách ở 27 fps.

Cách đo:
- Viết lại: `JT_OPTS="-Dbench=5000 [-Xint]" tools/jt.sh hl4.test.GameTraceTest work/golden/trace_M.txt 9000 500 $(cat docs/golden_scripts/kM.txt)` (chỉ tính thời gian `Engine.tick`, không tính phần so hash của test).
- Cũ: `HL4_FPS=1 [HL4_AOT=1] HL4_FPSCAP=27 java [-Xint] -cp hl4j/build/test2 RunHeadless KTF.zip <ms> <ms> <out> "$(cat hl4j/profiles/keys_walk.txt)"`; ms/khung = `realms / frames` của mỗi dòng `[fps]`.

Giới hạn: hai bản đo ở hai cảnh khác nhau (đánh quái vs đi lại), nên chỉ so được bậc độ lớn. Tỉ lệ đo được: viết lại nhanh hơn bản cũ có AOT khoảng **10–15 lần**, hơn bản cũ không AOT khoảng **50–70 lần**. Máy thật chưa xác minh.
