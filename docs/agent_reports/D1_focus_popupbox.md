Module / assigned functions: D1 Focus / PopupBox. Focus_CursorInit, Focus_canInputKey, Focus_endProcess, Focus_pointer, Focus_pointerLeft, Focus_pointerRight, Focus_pressSelectKey, Focus_return, PopupBox_buttonSlot, PopupBox_draw, PopupBox_outline, PopupBox_select, PopupBox_selectString, PopupBox_setBoxAlert, PopupBox_setBoxSub, PopupBox_setBoxWithFocusValue, PopupBox_window, PopopBox_key.
Start: worktree reset to 6d3c2e5, docs/agent_wip/focus_popupbox.patch applied (minus the tsv), every function re-read against disassembly.

Ported (original @real address -> Java):
- Focus_CursorInit @0x5f40c -> Focus.cursorInit; Focus_canInputKey @0x5f448 -> Focus.canInputKey; Focus_return @0x5f45c -> Focus.isReturn; Focus_endProcess @0x5f470 -> Focus.endProcess; Focus_pressSelectKey @0x5f4bc -> Focus.pressSelectKey
- Focus_pointer @0x5fb04 -> Focus.pointer; Focus_pointerLeft @0x5fc7c -> Focus.pointerLeft; Focus_pointerRight @0x5fef0 -> Focus.pointerRight
- PopopBox_key @0xbbb20 -> Popup.key; PopupBox_window @0xbb090 -> Popup.window; PopupBox_outline @0xbb17c -> Popup.outline; PopupBox_buttonSlot @0xbb638 -> Popup.buttonSlot; PopupBox_select @0xbb7f8 -> Popup.select; PopupBox_selectString @0xbb49c -> Popup.selectString; PopupBox_draw @0xbb88c -> Popup.draw; PopupBox_setBoxWithFocusValue @0xbbf28 -> Popup.setBoxWithFocusValue; PopupBox_setBoxSub @0xbbe30 -> Popup.setBoxSub; PopupBox_setBoxAlert @0xbbed4 -> Popup.setBoxAlert
Ported in addition ("port thêm", reviewer please dedupe): Focus_Init @0x5f430 -> Focus.init (was inline in Popup.setBoxWithXyPos); MainFrame_isQcif @0x88ca4 -> Focus.isQcif; drawMainMenuBox_H @0x62bbc -> Popup.drawMainMenuBoxH (its text branch is unused by the assigned functions); Touch_drawRoundRect @0x37e00 -> Touch.drawRoundRect; PopupBox_setBoxWithXyPos type 1 branch (table C.0.18057 @0xd94b0 read from the binary = {30,31,32,33,34,35,30,31,32,33,34,3}).
Existing, javadoc only: none (PopupBox_setBox javadoc already there; its body now forwards to setBoxWithFocusValue as in the original).

Corrections of the WIP patch found by disassembly:
- PopupBox_draw has 2 parameters (box, texts); the caption of type 2 is the box's +0x24 pointer, not a parameter. Java: draw(box, texts), caption via Popup.evtText / boxText (parallel fields, byte slot stays 0).
- Several javadocs started on the line after "/**"; first-line form restored (§1.5).
- Everything else (key, window, setBox*, select, selectString, Focus_*) matched the disassembly.

Left as later:
- PopupBox_setBoxWithXyPos type 1 with (gameW/300*6 + count - 1) outside 0..11: the original indexes past its stack copy of the table (garbage); Java throws "...: later". Not reachable with count 1..12 and gameW < 600.

Other files touched (minimal):
- Event.java: Event_Key: "PopopBox_key (open box): later" replaced by Popup.key(evtBox, key) + return if still open (original calls it unconditionally, it returns 2 for a closed box: same). EVENT_EndProcess: "evtBox focus: later" replaced by Focus.endProcess -> It.alertConfirm(s8 evtBox[0x18]) -> Popup.end -> e[0x1ff]=0 (checked against disassembly @0x5d308).
- MainGameFrame.java: drawGameOver now calls Popup.window(9, 0, gameW/2-0x48, (gameH-0x94)>>1, 0x90, 0x94, 0) (result unused, as in the original).
- Popup.java: fields evtText/boxText, setBox -> setBoxWithFocusValue, setBoxWithXyPos uses Focus.init (the old code wrote +0x1c = 0x9c and +0x20 = 0 which the original does not; trace M still matches), struct doc comment.
- Touch.java: drawRoundRect. New file Focus.java.
- docs/ghidra_sig_fixes.tsv: added PopupBox_outline /6+r (disassembly @0xbb17c: pushes r4/lr, loads [sp+0x18],[sp+0x1c] = args 5 and 6, stores two zeros and calls PopupBox_window with 7 argument slots, returns r0).

Per-function check (§4b probe). Method: probes at tick 4800 on snapshot M_4800 with pointers to scratch bytes inside _event (+0x700 focus struct, +0x740 48-byte box; so the e= hash covers the struct bytes after each call). Java parameters are byte[]: TEMPORARY adapter hl4.game.D1Adapter (raw address -> G.event/offset, box copied in/out), real javadocs of Focus/Popup disabled during the measurement, adapter deleted and javadocs restored afterwards (backups, adapter, generator, probe files and driver in work/cr/: D1Adapter.java, genprobe.py, drive.py, bak/). Focus_pointer was measured through its real javadoc (spec Focus_pointer/6=0:2:3:4:5:6) by probing pointerLeft with k = cursor (d = 0) and pointerRight; the originals tail-call so ref shows Focus_pointer lines. Each function in its own run (the original nests calls). Command: python3 work/cr/drive.py (tools/e_calls.py + tools/jcr.sh --probe 4800 + callcmp --only NAME).
callcmp results (all "0 differences"):
Focus_CursorInit 1, Focus_Init 6, Focus_canInputKey 9, Focus_return 9, Focus_endProcess 36, Focus_pressSelectKey 27, Focus_pointer 261 (from pointerLeft probes) + 16 (pointerRight probes), PopopBox_key 1849, PopupBox_setBoxWithXyPos 484, setBoxWithFocusValue 48, setBox 24, setBoxSub 90, setBoxAlert 90, PopupBox_window 104, PopupBox_outline 26, PopupBox_buttonSlot 4, PopupBox_select 20, PopupBox_selectString 8, PopupBox_draw 7.
Coverage limits (honest): the hash does not include the screen, so the drawing functions (Focus_pointer pixels, buttonSlot, select, selectString, draw, drawMainMenuBox_H) are verified for no exception/same state/same forwarded arguments plus line-by-line disassembly reading, not pixel equality. selectString's label loop and draw types 1/3/4/5 with count > 0 need a text pointer array (probe cannot build one): probed with start >= end / count 0 only; the loop was checked against the disassembly (area codes 0x3c8d + k, colour test on the low 16 bits, text len 100, anchor argument). Focus_pointerRight probed with the build's gameW only (one MainFrame_isQcif branch).

Build: scan: 110 classes, 0 reject, 50 warn, narrowing casts 864; built .../work/build/hl4.jar 305613 bytes
Trace M: GameTraceTest ok ticks 501..9000

Original quirks copied: PopopBox_key spelling; Focus_endProcess/canInputKey use signed state (state -100 counts as "can input" but not "counting"); key handler writes a decremented cursor and tests bit 7 of the u8 (wraps to count-1); area code 0x3b21+a with a == cursor turns into OK; PopupBox_setBoxSub height loop equals 19*lines+2 for lines >= 2; PopupBox_window for type 9 with count <= 0 falls through to a = 0.

Escalations: none.
