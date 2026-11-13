# Validation 4.14.0 offline / online

All runs are isolated under multi-mod-compat-1.20.1/probe; no user installation,
world or config is modified. Stable 4.12 and the eight-mechanic 4.13 artifact kept.

Pure core tests: existing timing, exact x1.30/x2.20/x2.80 work-clock behavior,
manual settings, protections, classifier, FPS and client wheel rotation pass.
CombatKnowledgeTest additionally covers multi-evidence fingerprints, per-actor and
per-phase sequence isolation, three-repeat prediction threshold, persistent
transitions, stable intervals, actual-loss diagnostics, revisions, bounded memory
and strict neural fields/ranges. MetaMechanicsTest remains passing.

NeuralTransportTest uses an actual loopback HTTP server only: JSON/schema/model
request, fixture-only auth, valid bounded advice, HTTP429, invalid/oversized data,
timeout, redirects and endpoint restrictions pass. No external tokens were spent.

Native runs (read accompanying logs for exact fixtures):

- `run-deep414-key-20261022-060936`: actual attributed poison corrects weak light
  classification, current duration/work frozen; distinct native direct entities;
  beneficial speed excluded; actual full immunity contact verified; QA listener
  deliberately subtracts 3 HP outside protection and analyzer flags that actual
  loss instead of claiming a repaired block; Warden health bands; NBT reload.
  Real bow windup, projectile spawn, impact, explosion and debuff chain repeated
  to produce next-event prediction. Real client packet and Russian GUI; PASS.
- `run-neural414-offline-default-20261022-060948`: actual offline JAR exposes no
  registered remote config and makes zero requests to the available local fixture,
  while ordinary learning continues; PASS.
- `run-neural414-online-20261022-061000`: actual online JAR receives fixture
  advice into server classifier, preserves current duration/work, then HTTP429
  restores local mode while native ticks/progress continue. No player name/UUID
  in request; PASS.
- `run-smart414-native-final-20261022-061432`: unknown native damage, registry/name
  priority, actual HP mitigation/disable/unequip, real projectile context, neutral
  negative vs beneficial effects, Ice and Fire lightning vs fire, AlexCaves arrow,
  exact accelerated frozen timer, NBT, manual override, packet and GUI; PASS.
- `run-all24-414-final-20261022-061421`: combined original 24-mod suite, actual
  effect/HP/control/boss-phase paths, First Aid limb handling, native zombie
  x1.30 and exact 20%, collision scope cleanup and Anomaly execution control;
  PASS. This covers the named fixtures, not every untested move of every mod.

The final opaque-owner fallback also gets a native positive/negative control:
a newly registered QA caster uses generic damage. Learning broad generic/species
before meeting it does not grant its distinct ability; learning that ability
blocks actual HP, and disabling it restores HP loss. Final rerun results are
appended after completion.

Landscape and utility proofs remain in VALIDATION-4.13.0.md: real roof survives
vanilla chunk unload and JVM restart, cap60 frame sample, native border/pocket/
muted sound/notification/defeat/TPS fixtures. The optimizer and DH payload hashes
remain unchanged. Internet advisor is an experimental adapter; live provider
quality or arbitrary model accuracy has not been tested without user credentials.

Final composite-owner proof: `run-deep414-composite-final-20261022-062140`
PASS including the newly registered caster with actual generic damage, broad
generic/species learning before first exposure, distinct ability stage IV HP
protection and disable restoring actual damage, followed by the deep native
sequence/revision/leak/GUI suite.
`run-all24-414-composite-final-20261022-062152` combined24 suite PASS.
`run-neural414-online-final-20261022-062204` actual online JAR + local advice/429
fallback PASS. After these tests only documentation and the utility completion
chat label were changed: utility progress now reports learned stage, not a
misleading combat-reduction percentage. Final builds rerun the core checks.


Packaged-artifact checks:
- `run-release414-deep-20261022-062933` and
  `run-release414-priority-deep-20261022-063321`: separate unknown-caster HP
  protection/disable, actual event sequence, revision, leak and GUI PASS.
- `run-release414-optifine-20261022-062921`: packaged online JAR, actual own
  video menu with supplied OptiFine, muted sound event and repeat safety PASS.
- `run-wheel414-infinity-final-20261022-063310`: JJC 259 techniques, negative/
  service/positive effect rules, real Gojo Infinity stage4/fist/sword/arrows,
  existence unlock, exact 150HP World Slash through armor/resistance/absorption,
  1200tick cooldown and actual GUI PASS. A long Windows dimension path exposed
  a genuine SQLite CANTOPEN in the newly bundled terrain engine; fixed by our
  public-API save override, preserving ordinary/existing databases.
- `run-wheel414-short-db-final-20261022-063853`: all those JJC checks PASS again;
  no CANTOPEN or landscape initialization failure. Cursed dimension's real
  SQLite database exists under isolated adaptationwheel-terrain/<hash>/ and is
  61440 bytes. Identified JJC techniques have priority over opaque generic
  fallback, so their earned protection is retained.
- `run-release414-archive-preserve-20261022-063905`: actual old red roof read
  from the unchanged pre-existing SQLite archive after JVM restart while its
  vanilla chunk is absent; PASS on the final online JAR.

Combined24 logs also contain a pre-existing nonfatal AoA recipe-book mixin NPE
(RegistryUtil.getId(RecipeType) returns null during client recipe sync), also
present in preserved 4.12/4.13 runs. It is not an adaptation assertion failure;
no claim of an error-free entire third-party pack is made. The intentionally
thrown projectile-scope test exception is expected and followed by passing
cleanup/normal-melee controls.

Final packaging checks: both variants have identical runtime class hashes and
separate correct online capability/version resources. Embedded optimizer and DH
hashes/notices retained. RU/EN key parity; no QA code in runtime JAR; normalized,
CRC-verified source archive includes exact component sources and native probe.
Stable 4.12 JAR SHA remains
E4B9BB9C449E17AEAC29472B0906A04CCC5789B62A70ACA12F975C057536887C.
