# Eight mechanics implementation — 4.13.0 work in progress

Authorized: world border personal passage, safe exact-repeat system message grouping,
nine-slot wheel pocket, persistent disk-backed landscape LOD, muted-sound direction
markers, heap-pressure adaptation, low server TPS adaptation, defeat event memory/trail.

Preserve stable dist/4.12.0. No edits to actual .minecraft mods/worlds/configs.
No agents are permitted by current developer instruction. All work stays in this task.

Landscape: integrate unmodified provided Distant Horizons 3.3.3 as optional bundled
engine, gated behind landscape adaptation. Disable distant generation (only received
chunks), low background thread budget, camera/frustum LOD streaming, no archive area
deletion. RAM-pressure response reduces own/engine active work, not archive history.
Must measure native frame times, prove a real constructed distant house persists
after normal chunks unload and after restart; no zero-FPS-cost promise.

DH JAR SHA A537A71171D935540B56B7598D0DE2C106F6F5026336C713D89A1D9EA0EA14E3.
Corresponding root source commit 575517f261dfd04e302a37719f1459e9b5633d9f;
coreSubProjects commit 6c1be680011194b2cf04ef842ace40c50683b09c.
Both official source ZIPs downloaded via GitLab API to embedded-src; root archive
is 20MB, core 4MB. Original notices/licenses retained. Core extracted under .tools/dh-core
only for API/code inspection, excluded from release. Use public APIs via reflection.

TODO: implement MetaServer/Pocket/DefeatMemory, client utilities/landscape bridge,
network action and warning packets, catalogue/menu/locales/config, tests + real
native suite, Optifine screen/render smoke, runtime and house persistence benchmarks,
package release/docs/source/license integrity.

Queued by user AFTER eight mechanics: richer multi-evidence ability identity,
event sequences (windup/projectile/explosion/debuff), observed protection leak
verification, category correction with explanation, boss timing/phase memory.
Two final JARs requested: OFFLINE regular improved analyzer; ONLINE external
neural HTTP advisor with regular analyzer fallback. User explicitly chose one
internet-required and one internet-free build. No API provider/credentials given:
provide opt-in endpoint/model/environment-key settings, no actual remote requests
until configured by owner, mock loopback tests, no personal/chat/world contents
sent. Neural output is advisory, strictly bounded; server damage remains authoritative.


2026-10-22: Eight mechanics completed and preserved in dist/4.13.0 (JAR SHA
43868FF596E8414B906A70EF43CA4F7A65D88BCCFEA7C3EE521066C9D03E5056).
Native utilities, actual defeat/respawn, low-TPS batching, real roof/unload/restart,
OptiFine own graphics screen and combined 24-mod suite all PASS. See validation docs.

Phase two implemented as 4.14.0-offline / 4.14.0-online. CombatKnowledge,
CombatObserver, ObservedAbilities, NeuralAdvice/Transport/Config/Advisor;
packet protocol17, detailed Russian UI, preserved current work/time, bounded
empirical sequences, native effect-caused correction and actual leak checks.
Core + loopback HTTP tests PASS. Native deep events, offline no requests and
online advice+HTTP429 fallback PASS; final composite-owner and all24 reruns
pending in current probe runs. No real external model/API has been contacted.
Remaining: verify final native runs, sync own QA source into compat-tests,
package both JARs and full sources/licenses, verify hashes/flavors and deliver.


COMPLETE 2026-10-22: final native combined24, deep unknown-caster/event/HP/GUI,
offline no-request, online advice/fallback, OptiFine own menu, JJC259/Infinity/
WorldSlash150HP and old landscape archive restart PASS. Identified JJC technique
priority preserved over generic fallback. Actual Windows long dimension SQLite
path failure fixed via public API short-path archive fallback; final native JJC
run confirms working real DB without CANTOPEN. Both JAR variants built/packaged,
matching class payloads and unchanged embedded engine/license hashes verified.
Remaining only final source ZIP hash refresh and user delivery.
