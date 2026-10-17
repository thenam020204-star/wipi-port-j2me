# Bàn giao giai đoạn E6 (quái, chiến đấu, rơi/nhặt đồ, level-up) + bản chạy thử

Ngày: 2026-10-17. Nhánh: `ccr-f53d26cc-ct4qk8`. **Kế hoạch cho các phiên tới: `docs/PLAN_NEXT.md`.** Đọc kèm: `docs/HANDOFF.md` (dựng môi trường, công cụ, mô hình code), `docs/phaseE.md` (§3b: bảng module E6 và các quirk của bản gốc; §4: lệch có chủ đích + lối tắt của bản thử nghiệm), `NOTES.md`.

## 1. Trạng thái

| Hạng mục | Trạng thái |
|---|---|
| Đã phân tích | E6: HERO_attackMonster, MONSTER_addHp/die/drop/regen, particle, level-up, game over, popup, nhặt đồ; lệnh script 0x1c; phần thưởng quest (`Event_Get_QuestStatusList`, `Item_RepaySet`) |
| Đã triển khai | tất cả mục trên (nhánh chưa port ném `IllegalStateException "...: later"`) |
| Đã build | có: `python3 tools/j2me_build.py build`, scanner 0 reject, preverify ProGuard |
| Chạy emulator | MicroEmulator 2.0.4: qua cutscene mở đầu, chơi ở trại (đánh quái, nhặt đồ, level-up) |
| Đối chiếu bản gốc | trace F/G/H/J/K khớp; L khớp tới 5496; M khớp trọn 501..9000 (hash khung + RNG + back buffer) |
| Chạy thiết bị thật | **chưa xác minh** |

Tiến độ (`python3 tools/port_cover.py`): Java dẫn chiếu **649 / 2113** hàm trong phạm vi (≈ 30,7%), 114 chỗ `later` trong mã, khoảng 25k dòng Java.

## 2. Commit cuối giai đoạn — phần CHƯA kiểm chứng

Commit bàn giao gồm code đã viết từ disassembly và **đã biên dịch qua build J2ME**, nhưng **chưa chạy tới trên trace nào**:

| Code | Nguồn gốc | Ghi chú |
|---|---|---|
| `Evt.onFunction` lệnh 0x1c: đặt hero ở ô (b0, b1) → x = b0·16+8, y = b1·16+8, hướng +0x168 = b2, rồi `Npcs.checkColHero` | `EVT_OnFunction` case 0x1c | INFERRED từ disassembly, chưa đối chiếu trace |
| `Npcs.checkColHero(hero)`: NPC có event type 1..10 chồng lên hero thì đẩy NPC sang ô kề đầu tiên đi được (hướng 0..3) bằng `evnetMove(n, x, y, 3, dir)` | `NPC_checkColHero` | INFERRED |
| `Items.removeItem(type, idx, n)`, `Items.repaySet(path, idx, many, apply)` | `Remove_item`, `Item_RepaySet` @0x96184 | chép trọn hàm; nhánh ArmsBag_setItem, SUMMON_addExp, ô guardian/arms, `GMenu_repayErrorPopSet` = `later` |
| `Hero.addCurMoney(h, n)` (+0xa28, trần 2 000 000 000) | `HERO_add_curMoney` | |
| `Quest.getStatusList` nhánh trả thưởng + `Quest.delObject(k, count)` | `Event_Get_QuestStatusList` | giữ quirk gốc: sau `DelObject` vòng lặp bỏ qua bản ghi vừa dồn vào chỗ k |

Việc đầu tiên của phiên sau là kiểm chứng phần này (§4 bước 1–2). Nếu lệch: disassembly thắng, sửa theo nó.

## 3. Bản chạy thử cho người dùng

- File: `work/testbuild/hl4.jar` (≈ 2,5 MB, có tài nguyên gốc, không có SND) + `hl4.jad`. `work/` bị gitignore ⇒ **không có trên git**; dựng lại:
  ```
  python3 tools/j2me_stage_res.py                 # -> work/resjar_full (bỏ SND, kickbert, TOUCH/CIMG/S)
  python3 tools/j2me_build.py build --res work/resjar_full
  mkdir -p work/testbuild && cp work/build/hl4.jar work/build/hl4.jad work/testbuild/
  ```
- Lối tắt tạm thời (ghi ở `docs/phaseE.md` §4): phím 1/2 ở màn đồng ý; mục 0 menu chính vào thẳng game mới slot 0 class 0; gặp phần chưa port thì dừng và in lỗi lên màn hình (`GameCanvas.drawError`).
- Chạy thử không màn hình: `JT_OPTS="-Devery=500 -Dshots=4000 -Dshotdir=DIR" tools/jt.sh hl4.test.PlayRun <ticks> <period> [tick:key|tick:pKEY|tick:rKEY]` — bấm OK mỗi `period` tick (phím 1 ở TitleFrame khi chưa trả lời đồng ý), dừng ở `later` đầu tiên và in tick.
  Kịch bản đi theo cốt truyện: `tools/jt.sh hl4.test.PlayRun 20000 10 4100:p-2 4300:r-2` (giữ phím xuống 4100..4300). Blocker trước commit này: tick 4738 = phần thưởng quest (đã port, chưa chạy lại).
- MicroEmulator thật: `tools/me_run.sh` + `tools/KeyRobot.java` (`delay:KEY[:holdMs]`; ENTER đầu tiên bị launcher nuốt; màn đồng ý cần phím `1`).

## 4. Việc tiếp theo (theo thứ tự)

1. Chạy lại PlayRun kịch bản ở §3 để thấy blocker kế tiếp sau tick 4738.
2. **Trace N** (đường cốt truyện tự nhiên: OK mỗi 10 tick, giữ phím xuống 4100..4300, 12 000 tick). Kịch bản lưu ở `docs/golden_scripts/scN.txt` (bản gốc) và `kN.txt` (Java, từ tick 500). Lúc bàn giao `work/golden/trace_N.txt` **chưa có** (lần sinh nền bị dừng khi worker khởi động lại) — phải sinh lại (chạy nền, vài chục phút):
   ```
   B="work/orig/android/영웅서기4.apk!/res/raw/jarfile.mp3!/binary.mod"
   LC_ALL=C.UTF-8 SNAP=500 nohup python3 tools/e_trace.py "$B" work/res work/golden/trace_N.txt 12000 $(cat docs/golden_scripts/scN.txt) &
   tools/jt.sh hl4.test.GameTraceTest work/golden/trace_N.txt 12000 500 $(cat docs/golden_scripts/kN.txt)
   ```
   Trace N là nghiệm thu cho lệnh 0x1c, `checkColHero`, phần thưởng quest. Lệch thì dùng `e_ret.py` / `e_raw.py` / `e_state.py` với **kịch bản đầy đủ** `scN.txt`.
3. Trước khi port cụm kế tiếp: `tools/e_cover.py` trên khoảng tick sắp tới (khoảng cuối loại trừ) để lấy danh sách hàm, port cả cụm một lượt.
4. Hồi quy sau mỗi thay đổi (chạy **tuần tự**, chạy song song `jt.sh` tranh nhau biên dịch):
   `for t in F G H J K L M; do tools/jt.sh hl4.test.GameTraceTest work/golden/trace_$t.txt 9000 500 $(cat docs/golden_scripts/k$t.txt); done`
   Kỳ vọng: F/G/H/J/K khớp hết trace, L dừng ở 5497 (`GameMenuFrame_returnMainMenu: later`, thuộc E4), M khớp 501..9000.
   Đã chạy với commit bàn giao: **L dừng đúng ở 5497, M khớp 501..9000** (F/G/H/J/K chưa chạy lại sau phần code chưa kiểm chứng ở §2).
5. Còn lại của E6: hồi sinh bằng bùa/NpcUi ở game over, đường hero class ≠ 0, phép/enchant/arms, boss, summon, skipTimeParticle, lớp particle 2.
6. Sau đó: E4 (story mode; trace L là trace nghiệm thu), E7 (VM script/event còn lại, NPC, quest), E8 (menu, túi đồ, shop, save RMS, âm thanh MMF, đóng gói; giảm kích thước jar).

## 5. Kịch bản golden trace (đã đưa vào repo)

`docs/golden_scripts/scX.txt` = kịch bản đầy đủ cho `tools/e_trace.py` / `e_ret.py` / `e_raw.py` (gồm token < 500); `kX.txt` = phần từ tick 500 cho `GameTraceTest`.
Token: `t:KEY`, `t:pKEY`, `t:rKEY`, `t:wSCENE/X/Y/W` (warp, móc test), `t:H` (fixture test: HP hero := HP max trước tick t — **không phải hành vi gốc**).
Trace sinh bằng `SNAP=500 python3 tools/e_trace.py "$B" work/res work/golden/trace_X.txt <ticks> $(cat docs/golden_scripts/scX.txt)`.
Số tick (tick cuối + 1): E 3300, F 8100, G 6000, H 5400, I 6200, J 6000, K 5300, L 7500, M 9000, N 12000.

## 6. Quirk của bản gốc đã gặp ở giai đoạn này (đã chép đúng, đừng "sửa")

- Đòn chí mạng có tùy chọn critical: chỉ `addCommonEffect`, bỏ qua `isBasicTargetEff` và hiệu ứng kind 10 (0x791bc → 0x7957c → 0x79224).
- `DM_DrawScreen565Ex` với W = 0, 4 bpp, cột nguồn lẻ (mode 0/2): mỗi hàng vẫn blend nibble thấp ở X và nibble cao của byte kế ở X+1 (mode 2 đi từ dưới lên; bản Ex bắt đầu lại X mỗi hàng, khác `DM_DrawScreen565`).
- `Remove_item` đi tiếp từ `head->next` sau khi xoá; `PickUpItem_endProcess` đọc link `next` của node đã xoá.
- `Event_Get_QuestStatusList` bỏ qua bản ghi bị dồn vào vị trí k sau `DelObject`.

## 7. Kỷ luật đã áp dụng (giữ nguyên)

Không float, không API ngoài CLDC 1.1 / MIDP 2.0 (scanner reject), không cấp phát trong `paint`/vòng lặp game (buffer tĩnh: `PickUp.TXT/NAME`, `Particles.XSP/XVAL`, ...), giữ kiểu byte/short/int như bản gốc, preverify trước khi đóng jar. J2ME Loader / MicroEmulator chạy được **không** chứng minh chạy được trên KVM thật.
