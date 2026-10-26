# Live quest testing — findings and fix status

Running list from live play (client built from branch `quest-automation-fixes`, driven via the Agent Server).
Status: **fixed** = committed and in the running build; **in progress** = being fixed in source, needs a rebuild + client restart; **open** = found, not yet fixed.

## Priest in Peril

| # | Finding | Status |
|---|---------|--------|
| 1 | Looped banking for 50 essence (can only carry ~27) | fixed (6a4e2c7) |
| 2 | Full inventory blocked the bucket withdrawal; essence now banked to make room | fixed — confirmed live |
| 3 | `talkToRoald` targets `KING_ROALD_CUTSCENE` (1399); live Roald is 5215, so the script stood next to him doing nothing | fixed in source (f4385d9), needs rebuild |
| 4 | When a step tracks no NPC, the script silently idles (no log, no fallback) | fixed in source (f4385d9), needs rebuild |
| 5 | Dialogue option "I'm looking for a quest!" not selected; script spammed space | fixed in source (f4385d9), needs rebuild |

| 11 | `killMonk` targets `PRIESTPERILEVILMONK3` (3486); live monk is `_VIS` 16273, so the script idled. Sweep all quests for base-vs-`_VIS` id mismatches | fixed in source (sweep: 19 steps, 11 quests), needs rebuild |
| 12 | Kill step: aggressive non-target monks (lvl 17/22) attack you and auto-retaliate overrides the target. Should finish the attacker, then re-target the step's NPC (and not wander out of the zone, which flips the step back to "Return to the temple") | fixed in source, needs rebuild |
| 13 | Walker struggled through the temple's large door (post-handleObject landing unresolved at 3409,3488) — user had to help | likely caused by #12 (fight pulled player off the door); transport data looks correct |
| 14 | No food brought for the dog/monk fights (HP fell to 19/41) — covered by the combat setup prompt (#8) once rebuilt | fixed in source (d98b176) |
| 15 | "Go upstairs" → "Climb up the ladder" to reach Drezel (plane 2): user had to climb manually; walker exited with `closest-index-none` at the stairs (3418,3493) | root cause = #23 (stale object ids) |
| 16 | "Talk to Drezel" (behind cell bars, plane 2): old build idled ~2 min without clicking — likely the line-of-sight gate in applyNpcStep. Retest on the new build | fixed — confirmed live on new build (talked in ~13 s) |
| 17 | Auto-eat: eats any edible item at ≤50% HP (or loadout %), every 600 ms, with or without a combat setup; banks for food when out. Must never eat quest items | fixed (cc3d5f3) — confirmed live: banked at 12/41, withdrew 10 Tuna, ate to 33/41, kept Golden key + Bucket |
| 23 | Object ids changed by a game update: temple `SPIRALSTAIRS` 16671 is live 61189/61190, `LADDER` 16683 is live 16679. ObjectStep tracked nothing, player idled at the stairs 2+ min. Fix: name-based object fallback near the step point (mirrors the NPC fallback) + PiP alternates | fixed in source (uncommitted, waiting on setup agent), needs rebuild |
| 24 | Essence trip: only 17 essence withdrawn because 10 Tuna + an obsolete Bucket held 11 slots. When gathering a carry-limited quest item, bank excess food down to ~2-3 (if HP is fine) and items no longer required by any remaining step | fixed in source (no fights left → bank everything but quest items, fill inventory; 1 run potion if enabled), needs rebuild |
| 9 | `killMonk` requires the golden key it drops; logs "missing Golden key" every ~10 s during the fight (fight still runs) | fixed in source (d98b176), needs rebuild |

## Feature: short idle breaks (requested)

| # | Item | Status |
|---|------|--------|
| 10 | Config: take short breaks every random 5–30 min, idle 20 s (configurable); only when not fighting / in dialogue / on a kill step | fixed in source (d98b176), needs rebuild |

## Feature: combat setup prompt (requested)

| # | Item | Status |
|---|------|--------|
| 8 | On starting a quest that has fights, ask once: gear (Inventory Setup or current), food + eat-at %, prayer/restore pots, prayer use (off / quick / protect), combat style. Remember per quest; accept the same via Agent Server start; bank for it before the first fight; eat/drink/pray during kill steps; warn on quest restrictions (e.g. PiP dog: no magic) | fixed in source (d98b176), needs rebuild |

| 18 | Stamina/energy potions as a quest-wide supply: on/off, quantity, drink-at run energy % | in progress |
| 19 | Per-fight combat style + optional gear setup (e.g. PiP dog: no magic) | in progress |
| 21 | Free gear choice per quest/fight (e.g. Fight Arena mage, PiP full rune): default = snapshot of what you're wearing; Inventory Setup or custom items; style "Auto" from worn weapon | in progress |
| 22 | Custom inventory items per quest (+ snapshot current inventory) with N slots reserved for quest items and a live slot counter | in progress |
| 20 | Setup dialog did not appear when the quest was started via the Agent Server mid-quest (no saved setup) | in progress |

## Walker

| # | Finding | Status |
|---|---------|--------|
| 7 | Route to Paterdomus tried the gate at 3261,3447 (needs The Dig Site); walker blacklisted it after ~20 s and re-routed. Transport data should carry the quest requirement | fixed in source, needs rebuild |

## Agent Server

| # | Finding | Status |
|---|---------|--------|
| 6 | `/quest-helper/start` writes `TurnOn` to the RS-profile config, but the script reads the global key, so automation stays off | fixed — confirmed live |

## Confirmed working live (new build)

- Auto-eat / low-HP bank-for-food (#17)
- Agent Server start turns automation on (#6)
- Priest in Peril monument: golden key → iron key, automated in ~40 s (MonumentKeyStep)
- Priest in Peril: murky water at the well, cell door with iron key, bless water, pour on coffin — all automated
- Priest in Peril: essence hand-in in bank trips (17 → 26 → 7), quest FINISHED 15:45 — Wolfbane received

## The Restless Ghost

| # | Finding | Status |
|---|---------|--------|
| 25 | Setup window prompted twice on start (two "waiting for quest setup" lines), then "Quest setup cancelled" stopped the quest with no user Cancel — a code-closed window counted as Cancel; status showed awaitingSetup=false / turnOn=false mid-prompt | fixed in source (root cause: reset ran every tick while TurnOn was false + queued dismiss closed the new window → default CANCEL), needs rebuild |

| 28 | Must-be-worn items (Ghostspeak amulet) never auto-equipped: handleRequirements counts them only when already worn, so the wear line is unreachable; the other wear path needs "obtain missing items". Worn by hand via Agent Server | fixed in source, needs rebuild |
| 26 | "no NPC found for step …" warns while the player is still far from the step (e.g. walking from Paterdomus to Lumbridge). Should only warn when within a few tiles of the step's point | fixed in source, needs rebuild |

## Feature: quest preflight (requested)

| # | Item | Status |
|---|------|--------|
| 27 | Before each quest: list every tradable item the whole quest needs, check inventory + equipment + bank, buy the shortfall on the GE, log a summary; stop with a clear message if coins are short. Config toggle + Agent Server `preflight` | fixed in source, needs rebuild (gap: tradable intermediates may be bought needlessly) |

## Black Knights' Fortress

| # | Finding | Status |
|---|---------|--------|
| 29 | Gear snapshot matches charged items by exact id: "Ring of wealth (4) is not in the bank, continuing without it" though a ring with other charges is owned. Match charge variants (same name minus "(n)") | fixed in source, needs rebuild |
| 30 | Nearest bank from the Black Knights' Fortress resolved to ISLE_OF_SOULS: walked to Edgeville and took the Soul Wars portal (portal cost ~0) instead of Falador bank | fixed in source, needs rebuild |
| 31 | Disguise (iron chainbody + bronze med helm, must be worn) missing; obtainMissingItems=ASK prompt hidden, script continued the step and walked to a door it can't open. Set config to YES live; fix: block the step until worn, pause while ASK is pending, put the choice in the setup dialog | fixed in source, needs rebuild |
| 32 | After a GE purchase, must-be-worn items log "purchased item … did not land in inventory" (check only looks at worn items); harmless but misleading | fixed in source (47ecdb9c) |
| 33 | Black Knight attacked during the chapel ladder step: click interrupted, script waited ~4 min trading hits, then stopped "Out of food at 49% HP" (setup was Skip, no food). Manually escaped down a ladder at 17/41. Fix: keep clicking the step target while attacked, escape+bank instead of stop, default food when a quest has fights | fixed in source, needs rebuild |
| 34 | USER RULE: only fight what the quest requires. Non-kill steps: auto-retaliate off, ignore attackers, keep doing the step. Kill steps: attack only the target (replaces #12 "fight off aggressor") | fixed in source, needs rebuild |
| 35 | Low-HP food trip from inside the Black Knights' Fortress (3022,3510,p0): "Bank location is null, cannot walk to bank" — nearest-bank lookup returns nothing (likely the new NearestBankPolicy rejecting all / fortress region), script stuck at 15 HP. Walked to Falador West by hand | fixed in source, needs rebuild + live check (BKF transport tiles guessed) |
| 36 | Setup gear restore withdrew Black full helm + platebody to re-equip, which would swap out the quest's required disguise (iron chainbody + bronze med helm). Quest must-be-worn items must win their slots | fixed in source, needs rebuild |
| 37 | East-room door (id 2338, 3020,3515) needs dialogue option "I don't care. I'm going in anyway." before the ladder (17159). The walker opens doors but never answers quest dialogue, so the door stays shut; plus dialogue is only answered on the 0.4–1 s tick. User did it by hand. Fix: answer the step's dialogue choices from inside walker/door handling, fast-poll dialogue (~100 ms) | fixed in source, needs rebuild + live check (BKF transport tiles guessed) |
| 38 | After the cabbage was used, fortress ladder sub-steps (which list the cabbage as a requirement) made it bank for a cabbage (Edgeville). Only fetch step items the step actually uses (equip / icon / highlight / bring-give text) | fixed in source, needs rebuild |
| 39 | BKF ladder transports were guessed wrong (ladder tile instead of the adjacent tile). Measured live: 17159/17160 ↔ 3022,3517 p0/p1 | fixed in source, needs rebuild |

## Proactive audit (code read, not yet seen live)

| # | Gap | Status |
|---|-----|--------|
| A1 | NpcEmoteStep NPE when Emotes tab closed (Below Ice Mountain loops forever); EmoteStep never handled | fixed in source (47ecdb9c), needs rebuild |
| A2 | NPC shop buying: chooseCorrectNPCOption prefers Talk-to over Trade; buy only hard-coded for Pirate's Treasure (Cook's Assistant, Vampyre Slayer, Witch's Potion, Prince Ali, Below Ice Mountain) | fixed in source (47ecdb9c), needs rebuild |
| A3 | Dialogue menu with no matching option: closes menu, re-clicks NPC, infinite loop | fixed in source (47ecdb9c), needs rebuild |
| A4 | expectedPreviousLine TODO (Land of the Goblins quiz) | fixed in source (47ecdb9c), needs rebuild |
| A5 | Shield of Arrav needs a 2nd player — preflight should stop with a clear message | fixed in source, needs rebuild |
| A6 | "kill" in step text makes talk steps attack (4 steps) | fixed in source (47ecdb9c), needs rebuild |
| A7 | Top-level WidgetStep never dispatched (19 files) | fixed in source (47ecdb9c), needs rebuild |
| A8 | 3+ item-on-item combines click too fast (Dragon Slayer map) | fixed in source (47ecdb9c), needs rebuild |
| A9 | Safespots defined in 10 quests, unused (Witch's House) | fixed in source (wiring for A5/A10 preflight pending) |
| A10 | Elvarg: antifire potion never drunk / no pre-entry check | fixed in source, needs rebuild |
| A11 | Spell-casting steps (5 members quests), Sailing steps (5 members quests) | later |
| A12 | Hint-only puzzles (Misthalin Mystery mirrors) | later |

## Plague City

| # | Finding | Status |
|---|---------|--------|
| 40 | Setup dialog greys out food/potions/prayer with no explanation on a no-fight quest; Skip button looks disabled. Make food/potions always editable (optional supplies), explain disabled combat options | fixed in source, needs rebuild |
| 41 | Status shows preflight="done" while still awaiting setup — check the preflight really runs after the setup answer | not a bug — preflight ran right after setup and bought the shortfall (confirmed live) |
| 42 | From East Ardougne the bucket trip picked LUMBRIDGE_TOP (free home teleport scores ~0) then walked back across the map. Score banks by round trip to the quest step; home teleport not free; prefer a nearby bank | fixed — confirmed live (round-trip picked VARLAMORE_EAST next to the Colosseum) |
| 43 | Walker stuck ~60 s on Lumbridge Castle stairs (MAX_PROCESS_WALK_TAIL_ITERATIONS at 3229,3212,p1), recovered by itself | fixed in source (livelock abort 45 s + watchdog) |

## Twilight's Promise

| # | Finding | Status |
|---|---------|--------|
| 44 | Setup food defaults to Lobster; user owns Tuna, so "no Lobster in the bank … continuing without food" on a quest with fights. Default to best owned food; fall back to best bank food | fixed — confirmed live (brought Tuna instead of Lobster) |
| 45 | Route East Ardougne → Civitas (1687,3141) climbed ladder 16683 (2665,3334) onto a dead-end platform, then looped recovery_target_walled to 2666,3334,p1 for minutes. Climbed down by hand; quest paused | fixed in source, needs rebuild |
| 46 | Pausing the quest (TurnOn=false) does not cancel the in-flight walk: it kept walking/transporting to 3240,3302 until the 300 s no-progress budget stopped it | fixed in source, needs rebuild |
| 48 | Twilight's Promise escort ("Lead the knight to the fountain") is a plain walk step: script sprints ahead, knight stops following, quest resets to the pub talk → loop. Generic escort mode: run off, short hops, wait for follower, re-talk | fixed in source (0ab8974a); manual escort confirmed the pattern: knight stops every 3–5 tiles, re-talk + 3-tile hops works |
| 49 | Colosseum knight prays against your style ("swap combat styles"). Needs secondary style + weapon in setup, preflight buys it, mid-fight swap on the target's protection prayer. Paused before the fight | fixed in source, needs rebuild |
| 50 | Colosseum knight (lvl 81) at 29/40 HP for 2 min while melee was prayer-blocked; ran out of 6 Tuna, escaped at 21 HP. User asked for super attack/strength potions → add boost potions to setup (preflight buys, drink at fight start / when expired). Also: the kill step never started attacking until I clicked Attack | boosts fixed in source; kill-step-not-attacking under investigation |
| 51 | Prayer: best offensive prayers for the current style + altar recharge before fights (user request) | fixed in source (87e83e31) |
| 52 | Bank clearing deposited the prayer potions the user put in the inventory. Keep user-brought consumables | fixed in source, needs rebuild |
| 53 | Setup: potion list (any type + count) — preflight buys, supply trip withdraws (user request) | fixed in source, needs rebuild |
| 54 | Preflight walked to the GE then stopped: 0 gp for 75 arrows (450 gp). Confirm purchases up front (list, cost, coins, warning; Buy / Start without buying / Cancel) and pre-check coins before walking | fixed in source, needs rebuild |
| 55 | "need 450 gp, have 0" — bank had 4.7M coins but the inventory was full (28/28), so the coin withdrawal could not land. Free a slot before withdrawing coins | fixed in source, needs rebuild |
| 56 | Walker hesitated 45 s near Primio (3277,3421) before the transport; the new livelock abort fired and the quest re-planned and took Primio by itself (self-recovery confirmed live) | minor — recovered automatically |
| 57 | Style-swap fight started without the Shortbow ("not in the inventory, cannot swap styles"); knight stayed 37/40 for minutes | fixed in source, needs rebuild |
| 58 | Prayer drained 37→0 with 4 Prayer potion(4) in the inventory, none drunk (not in setup list); held boosts unused. Drink held potions whenever prayer is on/boost expired | fixed in source, needs rebuild |
| 59 | Watchdog commit fd866057 deleted 5 upstream walker tests — restoring and fixing before the PR | fixed — restored, full suite 3166 tests pass |
| 60 | ships.tsv Veos (Port Sarim → Port Piscarilius) uses NPC id 10724; live Veos is 8484 → walker stalled at the dock (livelock-abort). NPC transports should fall back to the NPC name | open |
| 61 | Arceuus Library plugin (Hub): started a walk every tick, ignored UNREACHABLE, and disabled all teleports/boats so Kourend was unreachable from Varlamore. Fixed → Hub PR | fixed (Hub PR) |

## Quest chain (X Marks the Spot → …)

| # | Finding | Status |
|---|---------|--------|
| 62 | Setup dialog pops for every quest (even no-fight Rune Mysteries) and blocks the chain. Auto-apply last setup unless the quest needs a decision | fixed in source, needs rebuild |
| 63 | Preflight bought an Air talisman for Rune Mysteries, but Duke Horacio gives it. Skip quest-given items | fixed in source, needs rebuild |

## Quest results

| Quest | Result | Manual help needed (all now fixed in source) |
|---|---|---|
| Priest in Peril | FINISHED 2026-10-25 15:45 | Roald talk ×2 (#3/#5), monk targeting (#11/#12), stairs (#23), Drezel talk on old build (#16), food (#17), skip setup after tuna stop (#24) |
- Restless Ghost: Father Aereck quest-start dialogue picked automatically (confirms #5)
| The Restless Ghost | FINISHED 2026-10-25 16:00 | setup-window false cancel (#25, restarted with Skip), amulet worn by hand (#28) — otherwise fully automatic: Aereck, Urhney, coffin, ghost, Wizards' Tower skull, return |
| Black Knights' Fortress | FINISHED 2026-10-25 ~16:40 | many: disguise buy (#31), Isle of Souls detour (#30), fights (#33/#34), door dialogue (#37, user did it), fortress exits (#35/#39), cabbage detour (#38) — all fixed in source; final Amik talk done via Agent Server |
- Plague City: preflight confirmed live — listed 7 items, found Spade/Rope in bank, buying Dwellberries, 3 Buckets of water, Bucket of milk, Chocolate dust, Snape grass
- Plague City: auto-wear confirmed live (gas mask equipped, pipe climbed); mud patch ×4 buckets, spade, rope-on-grill, Edmond all automatic
| Plague City | FINISHED 2026-10-25 ~17:31 | none — fully automatic. Preflight bought items; only issue was the long Lumbridge bank detour (#42, fixed in source) |
- Twilight's Promise: correct way to Varlamore = Primio (NPC 12888, Varrock 3277,3411, "Travel") → Civitas 1700,3141; needs Children of the Sun. Charter ships cost 2000+ coins (player had none) — the planner should prefer Primio
| X Marks the Spot | FINISHED 2026-10-26 10:43 | none — fully automatic (~4 min) |
