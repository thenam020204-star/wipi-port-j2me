# Report: module D1 "UM / PointerList"

## Module / assigned functions
UM_Abs, UM_ByteToInt16, UM_ByteToInt32, UM_Int16ToByte, UM_Int32ToByte, UM_PutBytes, UM_Rand, UM_RandRatio, UM_TURN_L45/L90/R45/R90, UM_atoi, UM_cos,
UM_getAngleX, UM_getAngleY, UM_getWinColor, UM_turnOppositeDirection, UM_vecX, UM_vecY, UM_Free, UM_knlGetFreeMemory, UM_fileNameMode, UM_getMakeResName,
UM_LoadRes, UM_LoadResDes, UM_FileRead, UM_FileWrite, UM_FileDecoding, UM_FileEncoding, PointerList_initAll/isEmpty/release/releaseAll
(34 functions; all 34 now appear in `tools/port_cover.py --list`).

## Ported (original @real address -> Java), new code
- UM_TURN_R45 @0xc932c, UM_TURN_R90 @0xc9310, UM_TURN_L45 @0xc9364, UM_TURN_L90 @0xc9348 -> Um.turnR45/turnR90/turnL45/turnL90 (return the Tab.TURN_* table, like the original returns the table pointer)
- UM_getWinColor @0xc9980 -> Um.winColor (int[6] tables colorSelected/UnSelected/darkColorSelected/UnSelected; values read from the binary at 0xe1c58/70/88/a0)
- UM_atoi @0xc9c98 -> Um.atoi(byte[])
- UM_ByteToInt16 @0xc8efc, UM_ByteToInt32 @0xc8ea4, UM_Int16ToByte @0xc8f18, UM_Int32ToByte @0xc8ed4, UM_PutBytes @0xc9df8 -> Um.byteToInt16/byteToInt32/int16ToByte/int32ToByte/putBytes (delegate to Bytes.s16/s32/w16/w32 and System.arraycopy)
- UM_Free @0xca0e0 -> Um.free(byte[]) returns null (caller writes `p = Um.free(p)`; the GC frees)
- UM_knlGetFreeMemory @0xca104 -> Um.knlGetFreeMemory (Runtime.freeMemory; platform value)
- UM_getMakeResName @0xc9e40 -> Res.makeName(name, a, b, c) (returns the string instead of filling dst)
- UM_fileNameMode @0xc9e04 -> Res.fileNameMode(fmt, v) (single %d only)
- UM_FileRead @0xca57c -> Store.fileRead; UM_FileWrite @0xca4dc -> Store.fileWrite; UM_FileEncoding @0xca4e8 -> Store.fileEncoding; UM_FileDecoding @0xca588 -> Store.fileDecoding
- PointerList_initAll @0x87154 -> PList.initAll() (static); PointerList_releaseAll @0x87198 -> PList.releaseAll() (static)

## Already existing, javadoc `@0x` added only
UM_Abs @0xc9010 (Um.abs), UM_cos @0xc8cf0 (Um.cos; plus a negative-angle branch, see quirks), UM_getAngleY @0xc8dac (Um.angleY), UM_getAngleX @0xc8dd4 (Um.angleX),
UM_vecX @0xc92b8 / UM_vecY @0xc92e4 / UM_turnOppositeDirection @0xc928c (Um.vecX/vecY/turnOpposite), UM_Rand @0xc8fc0 (Rng.rand), UM_RandRatio @0xc8fec (Rng.ratio),
UM_LoadRes @0xca200 (Res.load), UM_LoadResDes @0xca16c (Res.loadDes), PointerList_release @0x87134 (PList.release), PointerList_isEmpty @0x868f0 (PList.isEmpty).
All were checked against the disassembly (instruction by instruction) as well as by the probes below.

## Port thêm (dependency, for the reviewer to de-duplicate)
- `j2me/src/hl4/sys/Md5.java` (NEW FILE): MX_md5GetCRC @0x35db8 = MD5Init/Update/Final (standard MD5, needed by UM_FileEncoding/Decoding; CLDC has none). Verified against the original on 15 inputs (0..4096 bytes, all padding boundaries).
- Des.encrypt/decrypt (existing) are used unchanged.

## Still `later`
- Res.makeName: stem of 30+ bytes / suffix of 5+ bytes (the original overruns its stack buffers: undefined behaviour); a name without '_' (the original scans off the string).
- Res.fileNameMode: any format other than a single `%d` (the game only uses `%d`).
- Um.cos: angle < -91 (the original reads the colour tables that precede um_cos).
- Not reproduced by design: platform failure paths of UM_FileWrite (-1106/-1107..., MC_fs errors) and the midas container file `kickass` (Store is the in-memory table).

## Mapping notes for the file/resource functions (not re-implemented at platform level)
- UM_LoadRes(name, &len) -> `Res.load(path)`; `*len` = `bytes.length`; "returns 0, *len = 0" = `null`. UM_LoadResDes -> `Res.loadDes` (Des.decrypt over the whole length, trailing len%8 bytes unchanged).
- The callers of UM_getMakeResName ("NPC/FACE_0_CIF", "/ITM/DAT/_ITM_0_SD") are inlined in the Java code as string concatenations (Faces.java, Items.java); `Res.makeName` reproduces the original function if a caller wants it. The UM_fileNameMode callers (NpcUi shop/combine/armsshop data load) are not ported yet.
- UM_FileRead/Write -> `Store.fileRead/fileWrite` (the in-memory table, same one as Store.load/save). UM_FileEncoding writes an md5+DES image through fileWrite, whereas Glob keeps the DECRYPTED image via Store.save, so one file must be used with one convention only.
- UM_FileEncoding encrypts the CALLER'S buffer in place (MX_desEncrypt on `buf`): reproduced.

## Per-function verification
Reference = the original in the emulator (tools/e_calls.py, snapshot M_4800); Java side tools/jcr.sh with the M trace (`--probe 4800`).
Scripts in `work/cr/`: `run_probe.sh <tag> <spec> [CR_SRC]` runs both sides + callcmp (probe files `work/cr/probe_<tag>.txt`, generators `gen_probe1.py` (= probe a), `gen_probe_b.py`, `gen_probe_c.py`).
The range given to e_calls is `4801 4800`: nothing is recorded during tick 4800, the probes are recorded as tick 4801 (otherwise natural calls of tick 4800 appear only in the reference file).

1. Pure int functions, probe `a` (2202 probe lines: Abs of edge values incl. INT_MIN; cos 0..363/400/1000/100000 and -91..-86/-5..-1; getAngleX/Y over 0..369 with radii incl. +-300000 (32-bit overflow); dirs -1..7; UM_Rand with b<a, wide ranges, INT range, 300 random pairs; UM_RandRatio -1000..1000; TURN accessors; getWinColor 16 combinations):
   `work/cr/run_probe.sh a UM_Abs,UM_cos,UM_getAngleX,UM_getAngleY,UM_turnOppositeDirection,UM_vecX,UM_vecY,UM_Rand,UM_RandRatio,UM_TURN_R45,UM_TURN_R90,UM_TURN_L45,UM_TURN_L90,UM_getWinColor`
   -> `callcmp: 2972 ref calls, 2972 java calls, 0 differences` (2972 > 2202 because nested UM_RandRatio->UM_Rand and UM_getAngleX/Y->UM_cos calls are recorded too)
   cos and getAngleX over the whole -91..-1 range (probe g, `run_probe.sh g UM_cos,UM_getAngleX`): `callcmp: 273 ref calls, 273 java calls, 0 differences`.
   Pointer returns (TURN_*, getWinColor) are only 'ptr' wildcards for callcmp, so I also read them from the emulator (`work/cr/emu_misc.py`): the 4 TURN tables are byte-identical to Tab.TURN_*; getWinColor returns 0xe1c58/0xe1c70/0xe1c88/0xe1ca0 for (p1,p2) exactly as Um.winColor selects, and their 6 ints equal the Java arrays (164,201,227,62,113,149 / 62,113,149,73,151,201 / 123,157,173,79,132,154 / 30,60,115,48,95,174). This part is a manual comparison, not an automated one.
2. byte[]/pointer helpers (UM_ByteToInt16/32, Int16ToByte, Int32ToByte, PutBytes, atoi), probe `b` (906 lines; buffers = the globals `_event`/`_game`, passed as absolute addresses, contents set with `!poke`; writes are checked through the `_game`/`_event` hashes; atoi on 12 strings incl. overflow, '-', letters, empty, offset starts).
   Adapter = a copy of the sources `work/cr/src_adapt` (via CR_SRC) with the real javadocs disabled and `UmAdapter` (int address -> G.event/G.game) added. `j2me/src` never received an adapter, so there is nothing to remove / restore.
   `work/cr/run_probe.sh b UM_ByteToInt16,UM_ByteToInt32,UM_Int16ToByte,UM_Int32ToByte,UM_PutBytes,UM_atoi work/cr/src_adapt`
   -> `callcmp: 557 ref calls, 557 java calls, 0 differences`.
   (One earlier DIFF was my own probe copying 100 bytes from `_game+0x20` into the masked pointer area 104..280: the original copies real heap pointers; the probe now stays below byte 104.)
3. PointerList_* via adapter `PListAdapter` (absolute address of _npcList/_monsterList/_targetEffectList -> G.* PList; PointerList_addObject adapter with pool objects o2.0, o3.0, o3.1):
   probe c (isEmpty/release/addObject incl. 130 adds = full list): `callcmp: 163 ref calls, 163 java calls, 0 differences`
   probe e (initAll after filling the 3 lists, twice): `callcmp: 20 ref calls, 20 java calls, 0 differences`
   probe f (releaseAll, same): `callcmp: 20 ref calls, 20 java calls, 0 differences`
   The observable state is only the isEmpty bool; that the 128 slots are zeroed is verified by disassembly (memset 0x200 + count = 0), not by a hash. `PointerList_releaseAll /0` was added to docs/ghidra_sig_fixes.tsv (single `b 0x87154`, no decompilation).
4. String / resource / file functions (string and buffer arguments cannot be probed). The original is called directly in the emulator (`g.call`, same snapshot; `work/cr/gen_res.py`), producing `work/cr/res_cases.txt` (1290 records), replayed against the Java methods by `work/cr/UmResTest.java`:
   `java -cp work/cr/cls:work/build/classes:work/res UmResTest` -> `umres: 1290 checks, 0 differences`
   Contents: UM_getMakeResName 900 (15 names x 60 (a,b,c) incl. negative, INT_MIN, c = 0..9, 10, 99, short truncation 0x12345 / 0xffff / 0x8000; string AND returned length); UM_fileNameMode 63 (the 3 real formats + others); UM_LoadRes and UM_LoadResDes 77 each (74 real resources from work/res incl. _EGDAT, _H_BH, _FR_BA, _ITM_OPTION + 3 missing: length and MD5 of the loaded bytes); MX_md5GetCRC 15; UM_FileWrite/Read/Encoding/Decoding sequences (container missing -1102, file missing -1107, empty file -1108, shorter/longer reads, len 70000 = 4464 u16 truncation, shrinking/growing rewrites, encode + read back, decode ok = 1, CRC mismatch = -1, wrong length, missing file = 0); the buffer contents after every call are compared (0xaa canary, so extra writes would show).
   What this does NOT prove: the reference runs the original code on the emulator's HLE (MC_knlSprintk, MC_knlGetResource, MC_fs* are emulator approximations, not a real WIPI device). UM_Free and UM_knlGetFreeMemory were verified by disassembly only (UM_knlGetFreeMemory is a tail call; the value is platform dependent: the emulator returns 0x400000). The Store layer reproduces the observable results of the midas container, not its file format.

## Build
`scan: 109 classes, 0 reject, 51 warn, narrowing casts 818` / `built .../work/build/hl4.jar 300042 bytes f545599cb1f8ccfa...` (my files add no scan warnings: `j2me_scan.py j2me/src --verbose` lists none for Um/Res/Store/PList/Rng/Md5).

## Regression trace M
`GameTraceTest ok ticks 501..9000`

## Quirks of the original (copied exactly)
- UM_cos has no lower bound check: a negative angle reads before um_cos, which is the end of um_sin: cos(-k) = UM_SIN[91-k] for k = 1..91 (verified); below -91 it reads colour tables (-> `later`). UM_vecX/vecY/turnOpposite: dir 8..15 reads the neighbouring tables (vecX(8..15) = -1,1,0,0,1,-1,-1,1, i.e. VEC_Y), dir < -1 garbage; Java throws ArrayIndexOutOfBounds there (callers only pass -1..7).
- UM_Rand uses an UNSIGNED modulo, so b < a gives a + (random % huge value); `b - a + 1 == 0` divides by zero (not guarded).
- UM_atoi does no validation: `n = n*10 + c - '0'` over all bytes up to NUL, 32-bit wrap.
- UM_getWinColor(p1, p2): p2 == 0 -> UNselected pair, p1 == 0 -> dark pair.
- UM_getMakeResName: the stem scan skips '/', '_' and everything above '9' and stops at the first digit (or NUL); the suffix is from the LAST '_'; c is a `short` (stack arg read with ldrsh): 0x12345 -> 0x2345, 0xffff -> -1; c in 0..9 gets a leading 0; b < 0 / c < 0 are left out. The 30-byte stem / 4-byte suffix stack buffers can be overrun.
- UM_fileNameMode does `dlib_memcpy(dst, NULL, strlen(dst))` (a memset was meant); harmless only because dst is an empty string at the callers.
- UM_FileRead/Write mask the length with 0xffff (70000 -> 4464). A zero-length file reads as -1108 (not 0). Reads return -1102 until the container exists (created by the first write).
- UM_FileEncoding encrypts the caller's buffer in place; UM_FileDecoding fills `out` even when the CRC mismatches, returns 0 when the read returned <= 0, -1 on mismatch, 1 on match.
- UM_LoadRes: when MC_knlCalloc fails it calls MC_knlFree() with no argument (harmless); UM_LoadResDes decrypts the whole length (trailing len%8 bytes stay).

## Escalation
None. No function needed more than one fix round (fixes: probe design for the masked pointer area, the `4801 4800` tick range, and the -1108 empty-file result found by a first experiment).
Note for the LinkedList agent: `Maps.tempInitAll` (not touched) inlines the three `release()` calls of PointerList_initAll; it could call `PList.initAll()`.

## Changes outside the assigned functions (every place)
- `docs/ghidra_sig_fixes.tsv`: + `PointerList_releaseAll /0`.
- `j2me/src/hl4/sys/Store.java`: private flag `sd` (set in `save`) + the four file methods.
- `j2me/src/hl4/sys/Res.java`, `Rng.java`, `PList.java`, `Um.java`: javadocs + the new methods listed above. `Um.cos` gained a branch for negative angles (previously ArrayIndexOutOfBounds).
- New file `j2me/src/hl4/sys/Md5.java`.
- No other file touched; no callers changed.

## Brief feedback
- §4: with the e_calls range `4800 4800` a function that the game itself calls in tick 4800 appears only in the reference file ("tick 4800 vs 4801"). `4801 4800` avoids it (probes are recorded at t1+1); worth writing into the brief.
- The adapter instructions say to put a TEMPORARY class into `j2me/src`; `CR_SRC=<copy of j2me/src>` (read by callrec_inst.py and passed through jcr.sh) is safer and leaves nothing to clean up. Mention it.
- The probe dispatcher cannot take string/array arguments and cannot read emulator memory: for string/buffer functions an emulator-direct harness (`g.call`, `alloc/write/read` after `e_snap.boot`) plus a Java replay test was the only practical way; a documented generic tool for "call the original with string/buffer arguments" would save every agent from hand-rolling it.
- Pointer returns show as `ptr` wildcards, so table accessors (UM_TURN_*, getWinColor) cannot be verified by callcmp at all.
- `!poke` targets only a fixed list of globals (and the Java side can only poke those). For pointer-argument functions, the absolute address of a global can be passed as a plain int (`_game` 1053136, `_event` 1048100, `_npcList` 1052208, `_monsterList` 1051116, `_targetEffectList` 1051668) and the adapter maps it: undocumented. The masked pointer areas of `_game` (bytes 8..12, 104..280) must not be copied from in probes.
- Tail-call thunks without decompilation (PointerList_releaseAll) need a tsv line; the brief covers it, but the error ("no Ghidra signature") appears from both e_calls and callcmp, which is confusing the first time.
- §1.5/§1.8: unclear whether a Java accessor should exist for functions that return a table pointer in the original (UM_TURN_*, getWinColor) when callers index `Tab.TURN_*` directly. I added cheap accessors so the function counts as ported; tell agents which is wanted.
- The brief does not say how to treat undefined behaviour of the original (negative table index, unguarded division by zero, buffer overruns): I reproduced the reachable behaviour where it was verified (cos with negative angle) and used `later` otherwise.
- The brief does not say what to do for functions with no Java counterpart by nature (UM_Free, UM_knlGetFreeMemory); I made one-line Java methods so the javadoc has a home.
- §6 says to nominate "port thêm" but not how to name new utility classes (Md5 placed in hl4.sys); a rule for where shared helpers go would help avoid duplicates between agents.
