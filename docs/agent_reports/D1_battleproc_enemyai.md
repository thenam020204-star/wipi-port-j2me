Module / assigned functions: D1 BattleProc / EnemyAIParsing: BattleProc_setSetItemPassive, setSkillEff_Class/Item/Magical/Summon/Etc, EnemyAIParsing_Init/Finalize/scriptRelease (+ small dependencies).
Start: WIP patch docs/agent_wip/battleproc_enemyai.patch applied on 6d3c2e5, every hunk reviewed; nothing reverted (all edits were inside the allowed minimal-edit rules).

Ported (original @addr -> Java):
- BattleProc_setSetItemPassive @0xccb40 -> BattleProc.setSetItemPassive
- BattleProc_setSkillEff_Magical @0xcb16c / _Class @0xcb174 / _Summon @0xcb17c / _Item @0xcb184 (empty stubs, return 0) -> BattleProc.setSkillEffMagical/Class/Summon/Item
- BattleProc_setSkillEff_Etc @0xcd0e4 -> BattleProc.setSkillEffEtc
- EnemyAIParsing_Init @0xd18f0 -> EnemyAi.init (Java works on the parsed AiData; sets counts, tokens, action state, returns the file offset after the section)
- EnemyAIParsing_scriptRelease @0xd1758 -> EnemyAi.scriptRelease; EnemyAIParsing_Finalize @0xd1794 -> EnemyAi.release
Port thêm (small dependencies, new): CHAR_addCommonEffect2 @0x3c5a0 (TEffects.addCommonEffect2), CHAR_removeBuffEffect @0x3af54, CHAR_addEtcStatus @0xcb474, CHAR_removeBuff @0xcbc68, CHAR_addBuff @0xcbf3c (all Chars).
Javadoc only (existing code): CHAR_removeCommonEffect @0x3af04, CHAR_removeTargetEffBad @0xcb5e0, CHAR_removeTargetEffMagic @0xcb540 (addresses checked against disf).
Call sites changed (minimal): HeroBattle (4 later -> BattleProc.setSkillEff{Etc,Class,Summon,Item}; Magical), MonBattle (Magical later -> stub call), Monsters.releaseAi -> EnemyAi.release, Maps.java Map_Finalize loop: added Monsters.releaseAi((Char) o) (was only a comment; order matches disassembly 0x9bfc0..0x9bfd0). Hero.setHeroStatusItemPassive keeps its later (Item_loadSetOptData not mine), so setSetItemPassive is not wired in.
docs/ghidra_sig_fixes.tsv: the 5 lines from the brief added (stubs verified: mov r0,#0; bx lr; addCommonEffect2 reads [sp+0x38..0x44]).

Later: none inside the assigned functions. Etc: attacker of a type other than hero/monster/summon (original uses an uninitialised stack variable) is not reproducible; setSetItemPassive with a non-hero self throws "later" (original uses stale registers).

Per-function check (work/cr/run.sh, probes from work/cr/gen_*.py, snapshot M_4800, hashes + CALLDUMP):
- stubs x4: 4 calls, 0 differences
- setSetItemPassive: 80 calls, 0 differences (random kinds/values on item o8.0, hero and monster tgt)
- deps (addCommonEffect2 + CHAR_targetEffect args, removeBuffEffect, addEtcStatus, removeBuff, addBuff, removeCommonEffect(+r), removeTargetEffBad/Magic): 2680 + 3793 calls, 0 differences
- setSkillEff_Etc: seeds 1,2,3,4,7 (kinds 0x3d..0x5f + defaults/out of range, hero/monster/summon self, hero/monster tgt, monster immunity types 0..8, hero class 0/1/2, flag 0/1, infoType 1/2): 2386 + 8816 + 8833 + 8932 + 1136 calls, 0 differences (1500+1500+1500+700+400 Etc calls)
- EnemyAIParsing_Init/Finalize/scriptRelease (trace calls, spec EnemyAIParsing_Init=0:-:-:3:4,EnemyAIParsing_Finalize,EnemyAIParsing_scriptRelease=- ): M_4500 ticks 4500..4800: 62 calls, 0 differences (Init 20 incl. return offsets, Finalize 14, scriptRelease 28); M_500 ticks 2500..4500: 21 calls, 0 differences
Build: scan: 109 classes, 0 reject, 50 warn, narrowing casts 887 / built .../work/build/hl4.jar 306788 bytes
Trace M: GameTraceTest ok ticks 501..9000

Quirks of the original reproduced:
- Etc: MONSTER_isImmune is re-asked for each comparison; 0x4e has no immune==1 branch; effect owner type for other attackers uninitialised.
- setSetItemPassive: second value is signed byte, value signed 16 bit.
- Tail calls: CHAR_removeTargetEffMagic (hero class 0/1) leaves no record in the original; verified with hero class 2 plus nested removeCommonEffect(+r).

Escalations / findings outside my functions (not changed):
1. BattleProc.setSkillPassive reads the value array as bytes (V1[i]); the original copies 4 bytes but reads halfwords ldrh [sp+0x30+2i] (slots 2,3 stale stack). Differs when the hero has skill passives with values (seen: StatBN a=10 vs 0 at skill 15 info 1). Probes worked around it by poking the hero skill-kind tables to 0.
2. Monsters.setMonsterStatus passes p6=0 to setSkillPassive; the original passes the monster (only the recorded arg differs, p6 unused).
3. getSkillEffValue with infoType 0 (or HERO_set_HeroStatus tail with hero +0x10c active) uses uninitialised stack in the original (e.g. StatBN kind 1 a=3404); not reproducible. Probes keep +0x10c = 0.
4. Java summon struct (Pool.summon) differs from the original at M_4800 (info 3 passive tables), so infoType 3 buffs on monsters differ; probes use infoType 1/2.
5. TEffects.init throws ArrayIndexOutOfBounds for kind 7 effect 0x28 when the owner is a monster (Etc 0x4f infoType 2, attacker class 0/1); the original works. Etc itself passes the same arguments.
