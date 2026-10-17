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
1. **T0.4 xong (2026-10-17, phiên hẹn giờ).** Kịch bản module (`docs/golden_scripts/scX.txt` đầy đủ / `kX.txt` cho Java), trace gốc trong `golden/` (R/S/W: phần < 4800 chép từ trace_M, kịch bản giống M trước 5450; sinh phần sau từ snapshot M_4800):
   | Trace | Module | Kịch bản | Bản gốc chạm (kiểm bằng đếm lời gọi) | Java hiện dừng ở |
   |---|---|---|---|---|
   | **R** | menu trong game (GameMenuFrame / GMenu) | M tới 5450, `-16` mở menu, vào lần lượt Status, Inventory, Equipment, Skill, Quest, System, thoát | 46 hàm: `GMenu_Paint{MainMenu,Status,Inventory,EquipMent,Skill,Quest,System}`, các `*Key`, `GameMenuFrame_returnGame` | 5450 `exit to menu (soft key 2): later` |
   | **S** | shop vàng (NpcUiShop) | M tới 5450, warp cảnh 58 ô (13,16), quay lên, OK với NPC 2 → `NpcUi_Initialize(0, 0)`, duyệt/mua | 41 hàm: `NpcUiShop_*` (gồm `BuyOkKey`, `setSellItem`), `GMenu_draw*`, tạo item | 5451 `Eff_deleteEffectInList: target effects: later` (warp) |
   | **W** | boss + quái triệu hồi | M tới 5450, warp cảnh 150, OK mỗi 7 tick, hồi máu mỗi 25 tick, tới 6500 | `Boss_Initialize`, `Boss_AI_Attack`, `Boss_draw`… (26 hàm), `MONSTER_setSummon`, `MONSTER_checkSummon`, `SUMMON_create` | 5451 (warp, như S) |
   | **V** | game over → chọn 예 (hồi sinh) | L tới 5490, `-3` ở 5494, OK 5497 | `NpcUi_Initialize(6, 10)` = **cửa hàng tiền mặt qua mạng** (`NpcUiNet_serverWait` 700 tick, chờ server mãi) | 5497 `NpcUi_Initialize(6, 10): later` |
   Phát hiện: phím trong game (AND2011) `-16` = menu đầy đủ, `-8` = hộp lưu nhanh, `-7` = cửa hàng tiền mặt (mạng, `_map[0x375]`), `*` = minimap. Shop vàng mở bằng lệnh script 0x39/0x3a/0x3b khi nói chuyện với NPC (cảnh có shop: `tools/scn_dis.py`, ví dụ e0049, e0058, e0063). Chỉ số warp = **chỉ số cảnh** (`e%04d` class 0/1, `n%04d` class 2/3); boss ở cảnh e0147–e0161 / n0131–n0144 (header cảnh byte 6–8 = mã quái, ≥ 200 = boss). `docs/map_scan.tsv`: quét đủ 183 cảnh `e`.
   **Cần người dùng quyết định**: hồi sinh ở game over và phím `-7` trong AND2011 đều đi vào cửa hàng tiền mặt qua mạng (không dùng được offline). Đề xuất: bản J2ME thay bằng lựa chọn offline (ví dụ hồi sinh dùng bùa có trong túi hoặc miễn phí, `-7` bỏ hoặc mở shop vàng), ghi là lệch có chủ đích.
2. Giai đoạn 1 theo PLAN_NEXT §4a: chạy song song nhiều agent Sonnet trên các module D1 (`UM`, `LinkedList`, `MP`, `OBJ`, `Focus`, `PopupBox`, `BattleProc`, `EnemyAIParsing`, `MAPOBJ`, `DropItem`), mỗi agent một worktree, prompt như agent thử (xem `docs/agent_reports/D1_item_itembag.md` để biết định dạng nộp); Opus review + ghép + hồi quy tuần tự.
3. Mở: nhãn phím mềm KTF; cải thiện `tools/ktf_match.py`; `MONSTER_dropItem` có hai method Java cùng tên gốc (khử trùng); quy ước địa chỉ javadoc (một số file dùng địa chỉ Ghidra).
