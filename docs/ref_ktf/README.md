# Ảnh tham chiếu giao diện bản KTF (người dùng gửi 2026-10-17)

Dùng cho T0.7 (`docs/PLAN_NEXT.md`): giao diện bàn phím lấy từ KTF, logic + dữ liệu giữ AND2011 (`NOTES.md`).
Ảnh chụp màn hình điện thoại Android: vùng game ở trên, bên dưới là bàn phím ảo của trình chạy J2ME (không thuộc game).
Ảnh 01–09 có chữ tiếng Hàn (bản gốc); ảnh 00 là bản đã Việt hoá ("Tân binh", "Phím phải SHOP").

**Chỉ để nhận diện màn hình và thành phần.** Tọa độ, màu, thứ tự vẽ phải lấy từ code gốc (KTF / LGT 2010), không đo từ ảnh: ảnh đã bị phóng to và nén JPEG.

| File | Màn hình | Thành phần thấy được |
|---|---|---|
| `00_hud_vi.png` | HUD lúc đánh quái (bản Việt hoá) | thanh trên: cấp + tên quái; nhãn phím mềm phải "SHOP"; thanh dưới: ô tắt 1/3/5/7/9/0, icon skill ở ô 5, thanh HP/SP |
| `01_status.jpg` | Menu: Status | tab ◀1 / 3▶; ảnh nhân vật, tên, chủng tộc, class; HP/SP, EXP/NEXT; Level, ATH, P.DEF, M.DEF, RES, ACC, BLK, DOD, CRI; Stat Point + 4 chỉ số (근력, 민첩, 체력, 정신) |
| `02_inventory.jpg` | Menu: Inventory | tiền (소지금); tab loại ◀7 / 9▶ (전체); lưới 4×4 icon, dấu E = đang trang bị; số túi (가방수 1/3); khung chi tiết vật phẩm, SOCKET |
| `03_equipment.jpg` | Menu: Equipment | ô trang bị quanh hình nhân vật (mũ, áo, găng, giày, vũ khí, 2 nhẫn); lưới 3×3 vật phẩm; khung chi tiết + chỉ số |
| `04_skill_attack.jpg` | Menu: Skill, nhánh Attack | tab ◀7 / 9▶; cây skill có khoá, LV; dòng S.LEVEL 6/12/18/24; SkillPoint; thẻ tên skill + "#INFO" |
| `05_skill_defend.jpg` | Menu: Skill, nhánh Defend | như trên |
| `06_quest.jpg` | Menu: Quest | danh sách 7 dòng, nhãn loại (메인 = chính) |
| `07_system.jpg` | Menu: System | 세이브 (lưu), OPTION, HELP, MAIN MENU |
| `08_option.jpg` | Option | 배경음, 효과음, 게임속도, 진동, 대화속도, 시선이동, 크리티컬, 투구표시 (thanh cuộn: còn mục bên dưới) |
| `09_hud_shop.jpg` | HUD lúc đi lại trong trại | nhãn phím mềm phải "우상키 / SHOP" góc trên phải; thanh dưới như ảnh 00 |
