# Phase C — Bảng dữ liệu (E, HDAT, ITM, NPC, FR, AI) của AND2011

Nguồn sự thật: `binary.mod` AND2011. Công cụ: `tools/framing.py` (khảo sát khung), `tools/tables.py` (đo bố cục từ loader gốc),
`tools/ai_data.py` (AI, kiểm vi sai). Kết quả: `docs/phaseC_framing_survey.txt`, `docs/phaseC_tables_layout.txt`, `docs/phaseC_ai_selftest.txt`.

## 1. Khung F1 (CONFIRMED)
Phần lớn bảng dùng chung một khung: dãy bản ghi `{ s16le len, body[len] }`; bản ghi `k` tìm bằng cách nhảy `k` bản ghi (`Item_getOffset` @0x92c48;
`MONSTER_dataLoad_d/g`, `Boss_dataLoad_*`, `SUMMON_*` tự lặp lại đúng phép nhảy đó). File DES-giải-mã có thể kết thúc bằng ≤ 7 byte `0x00` đệm
(CONFIRMED: mọi file "dừng ở N−1" có đuôi `00`). Với quy tắc này **70 file** tiêu thụ đúng bằng kích thước; 71 file còn lại là định dạng khác (bảng §2).
`len` có thể bằng 0 (bản ghi rỗng, chiếm chỗ chỉ số).

| Nhóm | File (DES?) | Khung | Loader gốc |
|---|---|---|---|
| Quái | `E/_ESDAT_0..2` (DES; chỉ số theo `_game[1]`) 168 bản ghi; `E/_EGDAT` (thô) 167 bản ghi × 30 B | F1 | `MONSTER_dataLoad_d/g` |
| Boss | `E/_BSDAT_0..2` (DES) 38; `E/_BGDAT` (thô) 19 × 29 B | F1 | `Boss_dataLoad_d/g` |
| Hero | `HDAT/_H_S000..003` (DES) 16 bản ghi/lớp; `_H_SA` (DES) 40 × 22 B; `_H_BH` (DES) 6 | F1 | `HERO_loadSkillInfo`, `HERO_loadArmsSkillInfo`, `HERO_load_basic_stat` |
| Triệu hồi | `HDAT/_H_BS` (DES) 5; `_H_SG` (thô) 10 × 15 B; `_H_SS` (DES) 32 | F1 | `SUMMON_loadSummonData_initD/_G`, `SUMMON_loadSummonSkillInfo` |
| Vật phẩm | `ITM/DAT/_ITM_nn_DAT`, `_ITM_n_SD`, `_ITM_OPTION`, `_ITM_SET_OPTION`, `_ITM_CASH_RANOMBOX`, `_ITM_REPAY_n`, `_ITM_Q_REPAY_n` (DES); `ITM/BASIC_SM_DAT`, `ITM/_ITEMDROP` (thô) | F1 | `Item_load*`, `Item_loadOptionData`, `Item_loadSetOptData`, `GMenu_randomBoxData`, `Item_RepaySet` |
| NPC | `NPC/NAME_DAT`, `_NPCG_DAT(_)`, `_NPCUI_COMBINE_DAT(_n)`, `_NPCUI_SHOP_DAT(_n)`, `NPCUI_ARMSSHOP_DAT(_n)`, `NPCUI_GUARDIANSHOP_DAT` (DES), `PROBABILITY_DAT` (DES), `_QUEST_0/1_DAT` (DES) | F1 | `NPC_dataLoad`, `NpcUi*_dataLoad`, `NpcUi_probabilityLoad`, `LoadPartQuestData`, `Guardian_loadBasicData`, `Event_LoadTextData` |

`NPC/NPCUI_GUARDIANSHOP_DAT` được `NpcUiGuardianShop_dataLoad` nạp bằng `UM_LoadResDes` (@0xbf4b4) — bổ sung vào danh sách DES của `tools/hl4des.py` (trước đây ghi INFERRED).

## 2. Không theo khung F1
| File | Định dạng | Trạng thái |
|---|---|---|
| `E/AI000..050` (43 file, thô) | script AI: §3 | **CONFIRMED + kiểm vi sai 43/43** |
| `HDAT/_H_PDAT` | `u16le n`, rồi `n × 6 byte` → bảng 6 byte/mục tại `hero+0x1f0` (`HERO_loadParticleInfo` @0x8daec) | CONFIRMED (bố cục đo được, §4) |
| `HDAT/_H_P000..005` | bộ hạt: `u8 kind`, `s16 w`, rồi `k × 50 B` (`k` theo bảng `kind`→số mục trong `loadParticleSet` @0xc9690; kích thước file = `3 + 50k` khớp cả 6 file) | CONFIRMED (bố cục đo được, §4) |
| `HDAT/_H_PS000..008` | sprite CIF (`SPR_loadNewSprite(.., mode 0x11)` @0x8d940) | CONFIRMED (cùng định dạng `tools/cif.py`) |
| `ITM/_ITM_SD0..2` | MrgText | xem `phaseC_formats.md` §5 |
| `ITM/_ITM_ICN` | GBM đơn (`loadAllImages`) | xem `phaseC_formats.md` §3 |
| `NPC/_EVENT_POP_TXT` | LinkedText | xem `phaseC_formats.md` §5 |
| `FR/_FR_{BA,PL,EN,SU,SK}` (DES) | bytecode công thức chiến đấu (đệ quy: đọc bởi `FR_*`, §5) | **UNKNOWN chi tiết — để Phase E cùng bộ ước lượng** |
| `ITM/_ITM_EMPTY` (15 B) | trông như GBM nhỏ (`0b 04 00 01 00 …`) nhưng **không có nơi gọi** (không thấy chuỗi đường dẫn trong binary) | UNKNOWN / INFERRED chết |
| `DAT/_DAT_DES` (824 B) | bảng hoán vị 64 giá trị (`3a 32 2a 22 …`) cho DES; **không có nơi gọi** | INFERRED chết (bảng IP/E… nằm trong binary, `hl4des.py` đã kiểm) |

## 3. Script AI (`E/AI###`) — CONFIRMED, kiểm vi sai 43/43
Bố cục (`EnemyAIParsing_LoadData` @0xe1c60, `EnemyAIParsing_Init` @0xe18f0):
```
u8   kind                                   -> ai+0x20c
TRIGGER: s16le scriptLen, u8 nTrig, u8 cnt[nTrig], u8 size[nTrig], u8 script[scriptLen]
         bảng vị trí (dẫn xuất): pos[0]=1, pos[i]=pos[i-1]+2+size[i-1] (8 bit)
ACTION : s16le scriptLen, u8 nAct,  u8 cnt[nAct],  u8 size[nAct],  u8 pos[nAct], u8 script[scriptLen]
STRINGS: u8 nStr, u8 len[nStr], các chuỗi nối liền (không NUL)
```
`tools/ai_data.py selftest` so từng trường với heap của hàm gốc: 43 file, 0 lệch; file được tiêu thụ đúng bằng kích thước.
Luồng byte của `script` do `EnemyAIParsing_*` (Token_*) diễn giải — thuộc Phase E (giữ nguyên byte).
(Lưu ý: `*(script+4)` = độ dài chỉ có tác dụng tạm thời, bị `dlib_memset(…,0x14)` xoá ngay sau đó; chỉ truyền cho `Token_New`.)

## 4. Bố cục đo từ loader gốc (CONFIRMED) — `tools/tables.py`
`tools/tables.py` chạy **chính loader gốc** trong emulator đối chứng và ghi, với mỗi lệnh ghi vào cấu trúc đích, các byte file đã đọc kể từ lệnh ghi
trước (offset tương đối trong bản ghi) → bản đồ `file → offset trong cấu trúc, độ rộng`. Không dựa vào decompiler (tránh lỗi dấu/độ rộng kiểu Ghidra).
Đã đo (`docs/phaseC_tables_layout.txt`): `MONSTER_dataLoad_d/g`, `Boss_dataLoad_d/g`, `HERO_loadArmsSkillInfo`, `HERO_loadSkillInfo` (lớp 0 và 2),
`HERO_load_basic_stat` (lớp 0, 1), `SUMMON_loadSummonSkillInfo`, `SUMMON_loadSummonData_initD/_G`, `NPC_dataLoad`, `Item_loadSetOptData`,
`HERO_loadParticleInfo`, `loadParticleSet` (4 lớp).
Chú thích đọc kết quả:
- `rec+a..b -> dst+X wN`: các byte `a..b` của bản ghi được dùng cho một lệnh ghi N byte tại offset `X`; `rec+a..b` rộng hơn N khi
  chuỗi (sao chép bằng `dlib_memcpy`) đứng trước trường đó — các byte chuỗi được gộp vào lệnh ghi kế tiếp (chuỗi = `u8 len` + `len` byte EUC-KR).
- `computed`: giá trị loader tự tính hoặc đến từ tài nguyên thứ hai mà loader đọc (không theo dõi).
- Nghĩa của từng trường **chưa gán tên** (offset là của cấu trúc nội bộ game); tên đến cùng logic game ở Phase E (INFERRED cho đến khi có bằng chứng sử dụng).

## 5. Chưa phân tích / để Phase E (UNKNOWN)
- Bố cục từng trường của: `Item_load*`/`Arms_loadItem(s)`/`Item_loadOptionData`/`Item_skinDataLoad*` (`ITM/DAT/_ITM_nn_DAT`, `_ITM_n_SD`), `NpcUi{Shop,ArmsShop,Combine,GuardianShop}_dataLoad`,
  `NpcUi_probabilityLoad`, `LoadPartQuestData`, `Guardian_loadBasicData`, `GMenu_randomBoxData`, `Item_RepaySet`, `EVENT`/`NAME_DAT`.
  Khung F1 đã CONFIRMED; việc còn lại là chuyển từng loader sang Java rồi kiểm bằng **golden dump** (cấu trúc sau khi nạp: Java vs. loader gốc),
  vì các loader này cấp phát động (`MC_knlCalloc`) hoặc dựa vào trạng thái game (`MP_getHero`).
- Công thức `FR/*` (bytecode): đọc bởi `FR_CalcItem` @0x7... và nhóm `FR_*`; sẽ chuyển cùng bộ ước lượng + kiểm vi sai với `FR_CalcItem` gốc.
- Container cảnh `_scn` (`SCENE_Change`) và VM `IT_*`: Phase E.
