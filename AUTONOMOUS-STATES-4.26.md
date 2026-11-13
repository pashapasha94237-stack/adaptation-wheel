# Audited custom-state execution boundary (4.26 work)

This note describes a bounded execution API and private bytecode evidence. It does
not claim universal inference of arbitrary capability semantics. A numeric field's
name, its constructor default or the presence of a setter does not establish that
it is harmful, nor that its value may safely be reset.

## Reproducible inventory

`tools/audit-customstates426.py <mods-directory> --report <external-lab>/inventory.json`
reads ZIP/class metadata without loading mod classes. The report deliberately
labels everything as a candidate. Twelve selected mods were read without class
parse errors (175 candidate classes). The report and `javap -p -c` evidence are
private in the external lab, not shipped as mod source:

`<lab>/autonomous-compat-1.20.1/reverse/customstates426/`

## Ten concrete custom-state systems inspected

| System and exact artifact | Actual native behavior found | Safe boundary / exclusion |
| --- | --- | --- |
| Tough As Nails 9.2.0.171 thirst | `ThirstHandler.onPlayerTick(Player)` decrements `IThirst.getThirst()` via `setThirst(I)V`; zero thirst eventually invokes real `hurt` with `TANDamageTypes.THIRST`. `ThirstData` also calls its setters during NBT loading. | The survival tick's exact decreasing write can be scoped to its player. Never instrument every setter invocation, including load/client sync. Drinking and peaceful recovery must stay native. |
| Tough As Nails hydration/exhaustion | Same tick subtracts hydration by one, clamped to zero, before subtracting thirst. Exhaustion is first consumed by adding a negative delta. | Distinguish the harmful hydration decrement from beneficial exhaustion consumption. A negative number is not itself evidence of harm. |
| Tough As Nails temperature | `TemperatureData` stores both actual and target `TemperatureLevel` enums; default is `NEUTRAL`, and also keeps timing/caches. | Not a scalar direction: cold and heat are opposite harmful extremes. Requires an audited enum adapter and symptom scope; do not zero ordinals or erase all timers. |
| Sanity: Descent Into Madness 1.1.0 | `SanityProcessor.addSanity(ISanity,F,ServerPlayer)` computes current plus a signed delta through the configured multiplier; tick's lambda also writes current plus passive delta. `Sanity.setSanity` clamps to `[0,1]`, marks dirty, and is used by deserialize. `getMostInsanePlayer` chooses the maximum value: despite its name, zero is healthy and increasing this meter is harmful. | Signed source scope and verified negative outcome are required. Recovery (negative meter deltas) must pass through. Do not blanket-block `setSanity`, cooldown maps, enderman anger timers or save loading. |
| Mekanism 10.4.16.80 radiation | `DefaultRadiationEntity.radiate(D)V` adds only positive dose to a capability; baseline is `1e-7`. `update(LivingEntity)` uses that dose for real radiation damage and food exhaustion. `decay()` deliberately reduces it via `set(D)V`. | Exact positive dose addition is a possible state sink with an attributed victim. Preserve decay, commands and NBT restore. Full protection must clear/suppress the active symptom, rather than leave accumulated dose running. |
| NuclearCraft 1.2.36 radiation | `PlayerRadiation` stores a `long` radiation counter. `updateContaminationStage` chooses stages at 22%, 44%, 66% of configured maximum and uses a native 1,000,000 damage hit at maximum. The stage changes apply potion symptoms. | An exact long/native adapter is needed; values above `2^53-1` cannot safely pass through a double hook. Protect actual harmful exposure/stage writes, preserve cleaning, timestamp and inventory bookkeeping. |
| Ad Astra 1.15.21 oxygen | `OxygenApiImpl.entityTick(ServerLevel,LivingEntity)` checks survival tags, armor and actual oxygen, then calls native oxygen damage and sets air to `-80`. Its API's `setOxygen` changes world positions/bubbles, not a player's scalar. | Guard the attributed living victim's damage/air write. Never change global oxygen maps or fill machine/tank resources on the strength of a setter name. |
| Vampirism 1.10.17 blood and sunlight | `BloodStats` tracks blood as its food-like resource. `VampirePlayer` separately tracks sunlight exposure and its `sundamage_cache`, faction, level and ability data. | Blood consumption is not automatically hostile: it also pays for vampire abilities. The sunlight hazard needs its own verified source scope; never reset faction/level, resource costs or the entire player capability. |
| Paraglider 20.1.3 stamina | `BotWStamina.update(Movement)` dispatches signed native movement deltas to `takeStamina` or `giveStamina`; both APIs have simulation flags. It separately stores a boolean `depleted`. | Voluntary movement consumption and simulated queries are not an attack. Do not globally bypass `takeStamina` or set every `depleted` flag false. A separately attributed external drain could be handled at its own sink. |
| Diet 2.1.1 nutrition | `PlayerDietTracker` exposes named nutrient values, eaten item history, suite, active flag and attribute-modifier sets, and persists them together. | A low nutrient can remove a benefit; arbitrary “low number = hostile” inference would manufacture nutrition and progression. No automatic state reset justified by the audited API. |

Additional rejected examples: Iron's Spellbooks 3.16.3 `MagicData.setMana` and
casting state are paid player actions, not blanket harmful states. Blood Magic
3.3.8-50 `PlayerSacrificeHelper` explicitly grants altar LP and then directly sets
the player's health as the sacrifice price: a generic health/state guard must not
turn this into free LP. Dragon Survival 23.07.2026's `DragonStateHandler` mixes
species, size, flight and altar progress; its whole capability must never be reset.

## Execution contract

`StatePlan` is pure typed policy: exact sink ID, stable condition key, native
scalar descriptor (`Z/I/F/D/J`), proven harmful direction, proven healthy baseline
and valid native bounds. A plan is only applicable when a structural coordinator
has independently proved the exact write site, affected server player, meaningful
native harmful outcome and non-self-cost scope. Values outside audited bounds
fail open. NBT deserialization and network synchronization are not exposure.

`AdaptiveStatePlans.register(plan)` stores at most 1024 distinct immutable plans;
conflicting definitions are refused. It performs no reflective field lookup and
does not touch any capability. The global bridge can call `number`, `floating`,
`integer`, `wide` or `flag` before the exact native mutation with `causalHarm=true`
only for the verified source scope. The endpoint checks the real server thread,
player life/spectator status, worn wheel, global/category enablement, then records
continuous exposure and reads the authoritative stage multiplier.

Partial protection scales only a worsening numeric delta. Natural recovery and
beneficial values are preserved. Coarse integers round toward the native request
so rounding cannot grant premature immunity. Boolean state changes have no honest
fractional representation and are stopped only at full protection. Full protection
returns the proven healthy baseline for a still-harmful requested value; merely
holding an already-sick value would leave its native symptoms active.

The generic API is an execution boundary, not an allowlist that discovers fields
by English names. Four exact source-scoped coremod method plans additionally
connect three audited systems: TAN thirst/hydration, SanityDim active/passive
meter writes, and Mekanism radiation update. Their version/schema assumptions
are in `compat/adaptive-state-plans.json`; `coremods/adaptive_states.js` checks
expected native call structures before transforming. SanityDim's actual harmful
direction is explicitly tested. Mekanism uses its native dose-severity function
and configured harmful threshold. Its two effective symptom-dose reads scale
partially while preserving the saved dose history and decay; full adaptation
clears an active dangerous dose before native symptoms.
The coremod still requires registration in the global manifest and native
before/after tests. A real fixture must show native state/symptom change when the
wheel is absent, partial mitigation where representable, and full symptom removal,
followed by native behavior after disable/unequip; beneficial and self-cost controls
must remain unchanged.

## Verification performed for this component

Standalone JDK 17 compilation and `StatePlanTest` passed for harmful directions,
partial deltas, native recovery, full clearing of preexisting symptoms, booleans,
unadapted exact passthrough, range failures and unsafe long precision. No Gradle
build, native modpack launch or live capability mutation is claimed by this note.

## Integration corrections

Sanity bridge mirrors native finite clampNorm before validating the requested value, including an existing 1 plus positive delta. Mekanism accepts all native finite positive doses up to Double.MAX_VALUE; no artificial trillion-unit ceiling. State entries have localised catalogue labels and explicit category profiles. A global vanilla effect dispatcher separately covers newly registered harmful MobEffect subclasses without expanding optional class manifests; it preserves native beneficial callbacks and cleanup.
