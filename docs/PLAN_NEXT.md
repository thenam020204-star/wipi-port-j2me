# Kế hoạch các phiên tới: gameplay trước, story sau, chia theo độ khó

Lập: 2026-10-17. Đọc kèm: `docs/HANDOFF_E6.md` (trạng thái hiện tại), `docs/HANDOFF.md` (môi trường, công cụ, mô hình code).
Mục tiêu: đẩy tốc độ port lên mà **không** bỏ kỷ luật J2ME (CLDC 1.1 / MIDP 2.0, không float, không cấp phát trong vòng lặp, preverify, scanner) và **không** bỏ kiểm chứng với bản gốc.

## 0. Nguyên tắc

1. **Gameplay trước, story sau.** Story = màn story mode (E4), cutscene/hội thoại dài, chuỗi quest chính, popup thưởng quest, tranh kết thúc. Đi tới nội dung cần thử bằng warp `t:wSCENE/X/Y/W` và fixture test, không đi theo cốt truyện.
2. **Port theo module trọn gói**, không theo từng blocker.
3. **Kiểm từng hàm** (so với hàm gốc chạy trong emulator) là chuẩn để coi một hàm là xong. Trace cả game chỉ để nghiệm thu cuối mỗi module.
4. **Hai mức kiểm:** logic (chỉ số, RNG, trạng thái struct, save) phải khớp tuyệt đối. Lệch hình vài pixel thì ghi vào `docs/known_diffs.md` và sửa dồn, không chặn tiến độ.
5. **Phân việc theo độ khó:** việc dễ/vừa giao agent Sonnet 5.5, việc khó và review do Opus làm (bảng §2). Agent chỉ được nộp khi test của nó qua.
6. Không coi chạy được trên MicroEmulator / J2ME Loader là bằng chứng chạy được trên KVM thật.

## 1. Cần người dùng xác nhận ở đầu phiên sau

| Câu hỏi | Mặc định nếu không trả lời |
|---|---|
| Cho phép **bộ dịch bán tự động** pseudo-C → Java (T0.2)? Code sinh ra vẫn phải qua kiểm từng hàm. | Không dùng bộ dịch; agent chép tay từ pseudo-C + disassembly như hiện nay |
| Cho phép chạy **nhiều agent Sonnet song song**? (Tool Agent với `model: "sonnet"`; tool Workflow chỉ dùng khi người dùng nói rõ "dùng workflow") | Chạy tuần tự từng agent một |

## 2. Thang độ khó (tự đánh giá)

| Mức | Đặc điểm | Ai làm |
|---|---|---|
| **D1 dễ** | Hàm lá, tra bảng, tính chỉ số, list/pool; ít nhánh, không vẽ | Sonnet, không cần review từng hàm |
| **D2 vừa** | Module có trạng thái (menu, shop, summon, buff), có vẽ UI theo mẫu đã có | Sonnet port, Opus review diff + chạy hồi quy |
| **D3 khó** | Vẽ có blend/clip, RNG/tick timing, quirk bản gốc, quyết định kiến trúc J2ME (save RMS, âm thanh, kích thước jar, heap) | Opus |

Quy tắc leo thang: agent Sonnet không qua test sau **3 vòng sửa** thì dừng, nộp báo cáo (hàm, input, giá trị gốc vs Java), Opus xử lý.

## 3. Giai đoạn 0: nền tảng tăng tốc (Opus, làm trước hết, D3)

Không có giai đoạn này thì không nên giao việc cho agent: thiếu test thì lỗi của agent lọt qua.

| ID | Việc | Nghiệm thu |
|---|---|---|
| T0.1 | **Ghi và so lời gọi hàm.** Mở rộng `tools/e_ret.py`: với các hàm chỉ định, ghi mỗi lần gọi (thứ tự, đối số, giá trị trả về, hash các struct liên quan sau lời gọi: `_game`, `_event`, Char hero, pool) vào file. Phía Java: bộ ghi tĩnh tương đương, bật trong test (`hl4.test.CallRec`), không có trong bản release. Thêm `tools/callcmp.py` để chỉ ra lời gọi lệch đầu tiên. | Chạy trên trace M với 10 hàm đã port: 0 lệch. Cố ý làm sai 1 hàm thì tool chỉ đúng lời gọi đó. |
| T0.2 | **Bộ dịch** `tools/c2j.py` (nếu được duyệt): pseudo-C Ghidra → Java đúng kiểu code hiện tại (`Bytes.s32(m, off)`, javadoc `TênGốc @0xaddr`, nhánh không dịch được thì `throw ...: later`). Đánh dấu các mẫu Ghidra hay sai (vòng lặp, signed/unsigned, đối số stack, bảng `C.x.y`) bằng comment `// C2J-CHECK` để người sửa đọc disassembly đúng chỗ. | Dịch lại 20 hàm đã port tay; so bằng T0.1 trên trace M: sau khi sửa các chỗ `C2J-CHECK` thì 0 lệch. Đo tỉ lệ hàm dịch xong không cần sửa. |
| T0.3 | **Snapshot cho bộ chạy tham chiếu:** lưu/khôi phục trạng thái emulator (bộ nhớ, thanh ghi, trạng thái HLE) ở tick N, để `e_trace/e_ret/e_raw` chạy tiếp từ snapshot thay vì từ đầu. | Trace M từ snapshot 4500 cho kết quả giống hệt chạy từ đầu ở 4500..9000; thời gian giảm tương ứng. |
| T0.4 | **Kịch bản thử theo module**: mỗi module một kịch bản warp + fixture (ví dụ map có boss, NPC shop, summon) trong `docs/golden_scripts/`, sinh trace gốc ở nền. | Mỗi kịch bản chạm được hàm đích (kiểm bằng `tools/e_cover.py`). |
| T0.5 | **Bản giao việc cho agent** `docs/AGENT_BRIEF.md`: quy tắc J2ME, mẫu code, cách chạy T0.1, cách tự kiểm (build, scanner, test), định dạng nộp, quy tắc leo thang. | Một agent Sonnet thử port 1 module D1 chỉ với brief này mà không cần hỏi thêm. |
| T0.7 | **Giao diện bàn phím từ KTF** (quyết định 2026-10-17, xem `NOTES.md`): (a) xác minh bản LGT 2010 có cùng HUD với KTF không (chạy LGT 2010 trong bộ chạy tham chiếu hoặc so tài nguyên/chuỗi UI); (b) liệt kê phần giao diện khác AND2011: thanh dưới (ô tắt 1/3/5/7/9/0 + icon, HP/MP), thanh tên/cấp quái phía trên, nhãn phím mềm ("Phím phải SHOP"), menu; (c) kiểm ảnh UI của KTF có trong tài nguyên AND2011 không (so hash `docs/manifest_ktf.tsv` ↔ `docs/manifest_android.tsv`); (d) tìm hàm vẽ tương ứng trong KTF (raw Thumb, không symbol) bằng ghép chuỗi/cấu trúc với AND2011 (`tools/xmatch_strings.py`, `docs/xmatch_strings_ktf_so.tsv`). Không đoán bố cục từ ảnh chụp: tọa độ phải lấy từ code gốc. | Báo cáo (a)–(d) + danh sách hàm; người dùng gửi lại ảnh chụp KTF nếu cần (ảnh không đưa lên git vì chứa tài nguyên gốc). |
| T0.6 | Lựa chọn class test: lối tắt chọn class 1–3 (thay màn chọn class E4), ghi rõ là tạm. | Vào game được với cả 4 class; trace gốc tương ứng sinh được. |

## 4. Giai đoạn 1: gameplay, theo thứ tự (số trong ngoặc = hàm đã port / tổng của module, theo `tools/port_cover.py`)

### 4a. D1 dễ (Sonnet, có thể chạy song song)
- `Item` (25/58), `ItemBag` (6/18), `DropItem` (8/14): tra dữ liệu, ghép tên, đếm số lượng, túi vũ khí `ArmsBag_setItem`, popup túi đầy `GMenu_repayErrorPopSet`.
- `UM` (10/48): hàm tiện ích; `LinkedList` (9/15), `MP` (5/20), `OBJ` (4/19), `Focus` (1/10), `PopupBox` (3/16) gồm hộp type 1, `PopopBox_key`.
- `BattleProc` (8/15): passive skill/vật phẩm của quái và non-hero, `StatBN(monster)`, bonus bộ đồ `Item_loadSetOptData`.
- `EnemyAIParsing` (12/16), `MAPOBJ` (7/14).

### 4b. D2 vừa (Sonnet port, Opus review)
1. **Hero các class còn lại:** `HERO_check_skilluseHG/SK/SM`, `HERO_HG/SK/SM_process/endprocess`, Sunju key, ô dùng nhanh, teleport, trừ thể lực, hồi máu tức thì (`HERO` 66/156, `Hero` 10/29).
2. **Buff / trạng thái / hiệu ứng:** `CHAR` (48/113), `TargetEffect` (9/15), `Eff` (4/12): sát thương buff, hết hạn buff/trạng thái, buff sau đổi map, emoticon.
3. **Summon và Guardian:** `SUMMON` (4/48), `Guardian_loadBasicData`, icon guardian.
4. **Boss:** `Boss` (5/36), hộ tống boss, boss cuối; `MONSTER` còn lại (92/148), `MONSTER_setSummon`.
5. **Menu trong game:** `GameMenuFrame` (0/14), `GMenu` (5/78): túi đồ, trang bị, chỉ số, skill; trạng thái game ≠ 0/2 của `MainGameFrame` (20/50), thoát về menu bằng phím mềm 2.
6. **Shop và NPC:** `NpcUi` (0/20), `NPC` (26/36) gồm `NPC_setMove`; hồi sinh ở game over (`MainGameFrame_revive`, shop bùa `NpcUi(6, 10)`).
7. **Đi lại giữa vùng:** worldmap (`Event_ToWorldmap`, `EVENT_worlmapTick`, `MainGameFrame_endMapCalling`), lệnh script chuyển map và shop (chỉ phần gameplay của `Event`/`IT`).
8. **HUD và map:** HUD bàn phím theo KTF (sau T0.7; đây là việc **D3** vì nguồn KTF không có symbol và phải ghép hai binary), icon ô dùng nhanh, icon buff, minimap, tile animation (`Map` 52/128), `Map_screenLoseEye`, `SprObject_draw`, `SPR` (12/35).

### 4c. D3 khó (Opus)
- **Lưu/tải game trên RMS** (`LoadGameData`, save): chọn cách ánh xạ file save của bản gốc sang RecordStore, chịu được lỗi ghi giữa chừng, kích thước bản ghi trên máy thật. Quyết định định dạng → ghi `NOTES.md`.
- **Âm thanh** (`SM` 0/15): MMF/SMAF qua `javax.microedition.media.Player`; máy không hỗ trợ MMF thì im lặng, không crash. Đánh dấu ⚠️ phụ thuộc máy.
- **Vẽ có quirk:** water effect dưới đồ rơi, ô địa hình loại 7, thân hero class 2 (`SPR_drawSpriteSK`), particle lớp 2, `skipTimeParticle`.
- **Ngân sách tài nguyên J2ME:** đo heap đỉnh (mục tiêu ≤ 2 MB), số object/String cấp phát mỗi tick, kích thước jar (hiện 2,5 MB; đề xuất tách/nén tài nguyên hoặc nạp theo vùng), nhịp tick thật trên máy.
- Gỡ các lệch agent leo thang lên; review và ghép code agent; hồi quy trace.

## 5. Giai đoạn 2: story (sau khi gameplay xong)

E4 story mode (trace L là trace nghiệm thu), cutscene và hội thoại còn lại, chuỗi quest chính, popup thưởng quest (`Event_PopupRepay`, blocker tick 4738), `Event_Next_Scene`, `MainGameData_saveStory`, quest phụ, tranh kết thúc, phụ đề số. Bỏ các lối tắt test (màn đồng ý, vào thẳng game, chọn class).

## 6. Quy trình mỗi module

1. `tools/e_cover.py` trên kịch bản của module → danh sách hàm cần port.
2. (Tùy duyệt) `tools/c2j.py` sinh nháp; sửa các chỗ `// C2J-CHECK` theo disassembly (`tools/disf.py`, `tools/disr.py`).
3. Build: `python3 tools/j2me_build.py build` (scanner 0 reject, preverify).
4. Kiểm từng hàm bằng T0.1 trên trace của module: 0 lệch logic.
5. Hồi quy **tuần tự** F/G/H/J/K/L/M (`docs/HANDOFF_E6.md` §4), cộng trace của module.
6. Kiểm tay: không float, không cấp phát trong `paint`/vòng lặp, giữ kiểu byte/short/int, không đổi descriptor đã có.
7. Cập nhật `docs/phaseE.md` (bảng module + quirk mới), `docs/known_diffs.md`; commit + push lên `ccr-f53d26cc-ct4qk8`.
8. Báo cáo ngắn: Đã phân tích | Đã triển khai | Đã build | Chạy emulator | Đối chiếu bản gốc | Chạy thiết bị thật (chưa xác minh).

## 7. Đo tốc độ

- Sau T0 và sau mỗi module: ghi vào bảng dưới (hàm port được / giờ, số vòng leo thang, % hàm `c2j` dùng được ngay).
- Nếu sau 2 module D1 mà tốc độ không tăng ít nhất gấp đôi so với trước (khoảng 650 hàm / 2 ngày), dừng lại và đánh giá lại cách làm với người dùng.

| Module | Hàm port | Thời gian | Leo thang | Ghi chú |
|---|---|---|---|---|
| (chưa có) | | | | |

## 8. Phiên kế tiếp bắt đầu thế nào

1. Dựng môi trường nếu container mới (`docs/HANDOFF.md` §2), sinh lại trace nền (`docs/HANDOFF_E6.md` §4–§5).
2. Hỏi 2 câu ở §1.
3. Làm T0.1 → T0.3 → T0.5 → T0.6 → T0.7 (T0.2 nếu được duyệt, T0.4 song song khi trace chạy nền).
4. Thử agent Sonnet với 1 module D1 (gợi ý: `Item` + `ItemBag`), đo, chỉnh brief, rồi mới mở rộng song song.
