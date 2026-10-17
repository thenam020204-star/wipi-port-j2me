# Bàn giao Giai đoạn 0 (nền tảng tăng tốc) — phiên 2026-10-17

Đọc kèm: `docs/PLAN_NEXT.md` (kế hoạch; bảng trạng thái T0 ở cuối §3), `docs/AGENT_BRIEF.md` (giao việc cho agent), `docs/HANDOFF_E6.md` (trạng thái port), `docs/HANDOFF.md` (môi trường).
Nhánh: `hl4-j2me-port`. Người dùng đã duyệt (2026-10-17): bộ dịch c2j (T0.2) và chạy nhiều agent Sonnet song song.

## 1. Dựng môi trường (container mới)
`tools/setup_env.sh <KTF zip> <hl4_android.zip>` (giờ cài thêm `pyelftools`, `pycparser`). Trace A–Q đã có trong `golden/`.
Snapshot **không** commit (chứa bộ nhớ game gốc): sinh lại khi cần, ví dụ
```
B="work/orig/android/영웅서기4.apk!/res/raw/jarfile.mp3!/binary.mod"
python3 tools/e_snap.py "$B" work/res work/snap/M 500,4500,4800 $(cat docs/golden_scripts/scM.txt)      # ~7 phút
```
Brief và lệnh mẫu dùng `work/snap/M_4800.snap`, `work/snap/M_4500.snap`.

## 2. Công cụ mới (chi tiết ở đầu từng file)
| Công cụ | Việc |
|---|---|
| `tools/e_calls.py` / `tools/jcr.sh` / `tools/callcmp.py` | T0.1: ghi và so từng lời gọi hàm (gốc ↔ Java); probe gọi trực tiếp + `!poke`; `docs/ghidra_sig_fixes.tsv` |
| `tools/e_snap.py` | T0.3: snapshot bộ chạy tham chiếu; `SNAPLOAD`/`SNAPSAVE` cho mọi `e_*` |
| `tools/c2j.py`, `tools/c2j_eval.py` | T0.2: nháp Java từ pseudo-C, đánh dấu `// C2J-CHECK` (`docs/T02_c2j.md`) |
| `tools/e_scan.py` | T0.4: quét map (hàm module nào chạy ở map nào) |
| `tools/ktf_code.py`, `tools/ktf_match.py` | T0.7: đọc code KTF (Thumb), ghép tên với AND2011 (`docs/T07_ktf_ui.md`) |
| `tools/agent_env.sh` | `work/` cho git worktree của agent |

## 3. Sửa lỗi port tìm được nhờ T0.1
Ba chỗ bản Java đọc rect di chuyển của hero mà không làm mới như bản gốc (`CHAR_getHeroMoveRect`): `MONSTER_checkWithHeroRect`, `MONSTER_isHeroNear`, `MONSTER_isRectMoveable`. Không đổi hash khung các trace, nhưng là lệch logic thật.

## 4. Kết quả đo được
- Agent Sonnet thử (D1 Item/ItemBag/ArmsBag): 37 hàm / 33 phút, 0 leo thang thật, 15 góp ý cho brief đã áp dụng.
- c2j: 65% nháp đạt 0 lệch không cần sửa (20 hàm đã port tay làm mẫu).

## 5. Việc tiếp theo
1. **T0.4 đang dở (dừng theo yêu cầu người dùng 2026-10-17):**
   - Phím trong game (AND2011 `MainGameFrame_KeyPressed`): `-8` mở menu game (`_game[0]=3`), `-7` mở shop `NpcUi(0, 0xb)` khi `_map[0x375] != 0` (bật từ tick ~4500 trace M), `*` bật minimap.
   - `scR/kR` (menu) và `scS/kS` (shop) bắt đầu ở tick 5450 (chơi tự do ở map 51; ở 4600 hero đang trong sự kiện nên phím bị bỏ qua). Đã kiểm bản gốc: S gọi `NpcUi_Initialize(0, 11)` ở 5450; R nhận `-8` ở 5450 (menu đi qua `_game[0]=3`, không qua `GameMenuFrame_Initialize`: kiểm hàm `GMenu_*` bằng `tools/e_calls.py`). Trace gốc R/S **chưa sinh lại** cho kịch bản mới: `SNAPLOAD=work/snap/M_4800.snap python3 tools/e_trace.py "$B" work/res work/golden/trace_R.txt 6100 $(cat docs/golden_scripts/scR.txt)`, rồi ghép phần < 4800 từ trace_M (dòng `# seed`/`# snap` + tick 501..4799; kịch bản trước 5450 giống M).
   - `scV/kV` (hồi sinh: game over của L, chọn 예 ở 5494) đã viết; trace chưa sinh (bị dừng; từ đầu ~10 phút).
   - Quét map: `docs/map_scan_partial.tsv` (69/97 map; cột: map, số lần gọi theo họ hàm, hàm gọi nhiều nhất). Chạy tiếp các map còn thiếu: `python3 tools/e_scan.py "$B" work/res work/snap/M_4800.snap out.tsv <ids> Boss_AI,Boss_Initialize,SUMMON_AI,SUMMON_create,MONSTER_setSummon,Guardian,NpcUi_Initialize,NPC_create,MONSTER_Clone,MONSTER_AI,Map_Set_Shake,Event_Popup,Event_ToWorldmap_,MainGameFrame_endMapCalling 100`, rồi chọn map boss/summon cho kịch bản.
   - Nghiệm thu T0.4: mỗi kịch bản chạm hàm đích (`tools/e_cover.py` / `tools/e_calls.py` từ snapshot).
2. Giai đoạn 1 theo PLAN_NEXT §4a: chạy song song nhiều agent Sonnet trên các module D1 (`UM`, `LinkedList`, `MP`, `OBJ`, `Focus`, `PopupBox`, `BattleProc`, `EnemyAIParsing`, `MAPOBJ`, `DropItem`), mỗi agent một worktree, prompt như agent thử (xem `docs/agent_reports/D1_item_itembag.md` để biết định dạng nộp); Opus review + ghép + hồi quy tuần tự.
3. Mở: nhãn phím mềm KTF; cải thiện `tools/ktf_match.py`; `MONSTER_dropItem` có hai method Java cùng tên gốc (khử trùng); quy ước địa chỉ javadoc (một số file dùng địa chỉ Ghidra).
