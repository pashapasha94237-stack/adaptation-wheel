# Autonomous direct-health foundation (4.26)

This is a before-write guard for unknown loaded Forge mods. It does not grant stages, resurrect a dead player, restore health after an attack, or change an optional mod's own classes.

## Implemented entry points

- `LivingEntity.setHealth(float)` / `m_21153_`: the requested value is scaled before the native clamped health write.
- `SynchedEntityData.set(EntityDataAccessor, Object, boolean)` / `m_276349_`: only the exact `LivingEntity.DATA_HEALTH_ID` accessor (`f_20961_`) and a server player are inspected. Ordinary entity data is untouched. The two-argument overload delegates to this one.
- Optional First Aid 1.1 `SynchedEntityDataWrapper.set(EntityDataAccessor,Object)` / `m_135381_` is guarded before its real limb-model translation; its class name is only an ASM target string, with no optional Java linkage.
- Both hooks have balanced successful-return and exception cleanup. A setter's delegated tracked-data write is not counted twice.
- A negative write from a previously unknown mod call site receives `attack:<mod>:health_write/<24 hex characters>`. The identity uses the declaring class, semantic method, descriptor, bytecode position and owning mod, plus the first outer caller from a different mod. Shared utilities therefore do not merge every consumer into one adaptation. Ownership is obtained from the loaded module or actual mod CodeSource; no package-name guess is used for an otherwise unknown jar.
- Classes and identities are cached with fixed limits of 2048 and 4096. Stack inspection is limited to 96 frames and occurs only for an active wearer's finite, negative health write. Healing, client entities, illegal off-thread writes and a maximum-health clamp are passed through.
- Successful calls feed the existing `AdaptationEvents.recordExposure` queue with requested damage and the measured before/after HP difference. Stages, real work-clock acceleration, timing overrides, toggles, persistence and completion healing remain owned by the normal adaptation engine. Protection uses its configured `damageMultiplier`; stage zero still loses native HP.

## Existing compatibility

`ModAttackCompatibility.healthWriteScoped(player)` identifies already wrapped audited setters. `directHealthWrite(player)` must include `AutonomousHealthGuard.isWriting(player)` so First Aid's downstream generic limb notifications do not create another broad adaptation for the same setter.

Native damage methods encountered before a mod writer, wheel-owned health writes, native load/respawn/operator-command and totem paths are excluded. An unknown mod's own setter reached before a native hurt frame remains attributable: a hurt scope by itself does not prove that Forge damage events handled that write.

## Scope and limits

The hook handles the native float health resource and its public tracked-data setter. Direct writes to a third-party limb field, private SynchedEntityData item values, a separate resource/capability, `kill()`/`die()` without a health write, maximum-health attribute removal and arbitrary world-rule death require their own supported state/damage hook. Several abilities implemented in one identical class/method/descriptor share this guard identity. A jar containing several mod IDs is attributed only when its module or package distinguishes its owner. Unsupported provenance passes through.

First Aid translates a vanilla health setter into its body model in its overridden two-argument tracked-data setter. The optional adapter hook scales that real request and suppresses duplicate accepted-damage notifications; it never heals all limbs or assumes the main HP float is the sum of limb HP. Its own `set_impl`/model synchronization frames are passed over when finding the content-mod issuer, so normal body synchronization is not a new First Aid attack. Direct private limb mutations are outside this foundation.

Exact signatures and private-field SRG names were derived with `javap` against Forge 1.20.1-47.4.0 mapped and SRG jars. Building and native validation are delegated to the root release pipeline; this document does not claim that all 616 reference mods have been run.

## Integration corrections

Readable owner/method slugs survive saves and remote clients. Exact audited Blood Magic LP payments, Draconic Evolution Dislocator teleport cost and Embers Inflictor Gem self-cost retain native HP payment. On a throwing native callback, observed already-committed HP loss still creates one receipt; a pre-commit failure with unchanged HP does not. Committed immune attempts feed CombatKnowledge outcome verification. New evidence DIRECT_HEALTH is appended to the saved ordinal mask, never guessed from a method name.
