# Validation 4.7.1 — Infinity adaptation

Target: Forge 1.20.1, Jujutsu Craft v51 (original gameplay and the existing RU build).

The offline Gradle build and coreTest suite pass, including actual timer rates,
protection and configurable progression, persistence, sword settings and FPS state.

The integration probe runs in an isolated real client/integrated-server world.
It executes actual Gojo entity hurt callbacks, player.attack with a fist and iron
sword, the original InfinityActiveTickProcedure, and the original final JJC attack
guard for a second player. No production hit damage is forced through by setHealth.

Coverage:
- First blocked swing starts key condition:jujutsucraft:technique/205.
- A repeat swing increases the real server clock to x1.30 exactly once.
- Stages zero to three are still blocked by the original Infinity guard.
- At stage four real Gojo health decreases from normal fist/sword/direct attacks.
- The Infinity effect and amplifier remain on Gojo.
- An adapted player's arrow keeps its velocity through the actual stopping field.
- A different player's hit remains cancelled and their arrow remains stopped.
- Per-entry/global disable and unequip restore normal Infinity protection.
- Re-equipping restores bypass, and the completed key round-trips through saved state.
- A blocked arrow starts adaptation; repeating the same suspended arrow does not farm hits.
- An opponent without active Infinity does not start this adaptation.
- Existing 259 technique, status, domain and projectile-attribution checks still run.

The first test attempt used FakePlayer for a full damage pipeline. Jujutsu Craft
v51 deliberately excludes FakePlayer from its capability provider but dereferences
that capability in its attack handler. The test now uses its actual final guard
for this second-player assertion and its actual field for the other player's arrow.
Real player attacks against the real Gojo entity are tested through the full pipeline.

Compatibility is verified for Jujutsu Craft v51. Unknown future rewrites of its
procedures are not covered by this integration.

Final tested JAR SHA-256:
F6071DACD3DBBC15B12B6B96C5F94C57F3D446B18E41D0195B2A58E57D8F0957

JJC run: jujutsucraft-russian-1.20.1/probe/run-infinity-final-471-20261021-053318
JJC_INFINITY_QA PASS and JJC_WHEEL_QA PASS, process exit 0.
No-JJC regression: t2s-wheel-integration/probe/run-wheel-471-no-jjc-20261021-053529
All 26 T2S conditions, real disease procedures, partial protection and client mirroring
pass. The wheel also loads correctly with Jujutsu Craft absent. Process exit 0.
