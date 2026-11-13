# Adaptation Wheel 4.6.0 — Try 2 Survive integration

Date: 2026-10-21. Minecraft Java 1.20.1, Java 17, production Forge 47.4.16.

The release retains the embedded Pasha Boost 1.3.2 and its FPS unlock gate. T2S is optional and is not bundled or modified by this release. The integration targets the player capability of T2S 2.0, including the original and localized build (identical schema). No T2S implementation sources are included in the wheel.

## Implementation

- Registers 26 adaptation sources, with existing harmful MobEffect keys reused for seven effects. The remaining internal diseases/states use `condition:trytosurvivetwo:<id>` and are included in the J catalogue. Both language files contain their display names; the Configured timing validator accepts condition keys.
- Observes T2S player data before/after its tick. Starts on genuine negative state exposure, scales newly added numeric progression by the current protection, reduces accumulated negative severity once per completed stage, and clears harmful fields/flags on full immunity. Positive deviations from healthy reference values are preserved.
- Optional Forge coremod entry guards prevent seven severe disease procedures and seven temporary disease/progression procedures from executing on immune server players. Guards apply to the real T2S classes at runtime; no T2S JAR needs replacing.
- Unattributed damage inside known disease procedures is classified to its own adaptation key through a bounded caller stack inspection, while excluded vanilla damage keeps its existing policy. No thread-local damage attribution can leak into later events.
- T2S icon refreshes do not grant repeat-hit bonuses; new episodes or stronger effect applications have a one-second minimum interval. Normal combat acceleration is unchanged.
- Full stamina-loss protection clears its block/countdown and restores the available stamina reserve. Hunger immunity also neutralizes T2S's food depletion accumulator. Server-granted immunities are mirrored into the client's T2S indicators without accepting client claims of immunity.
- Optional linkage is resolved lazily and validated against the capability schema. With T2S absent, no capability classes are loaded and no unencountered T2S conditions appear in the catalogue.

## Executed checks

`build` and core checks passed, including countdown speed, stage protections, healing/config switches, source timings, FPS boundaries, saved FPS progression, wheel animation and selection models.

Production integration run: `t2s-wheel-integration/probe/run-russian-qa-20261021-011101` in the task workspace. Fixture: supplied T2S 2.0 Russian localization, SHA-256 `0119351DC11AEE8D4A1CC3569AECD426353B82A2476D483FA0CA2EA0285CAABA`.

- All 26 sources detected and present in the catalogue.
- All 26 internal states neutralized at full protection; disabling each category and unequipping the wheel stopped protection. Re-equipping restored protection.
- Partial numeric progression verified: after the first stage, a new increase from 100 to 200 becomes 180. An unchanged subsequent tick stays at 180 rather than repeatedly multiplying the whole disease value.
- Invoked all seven severe disease procedures directly with bad internal values while immune; actual runtime guards cleared those values and prevented procedure execution.
- Thirty identical icon refreshes generated no bonus hits. A fully adapted harmful effect was rejected.
- All five beneficial T2S effects remained applicable and did not create adaptations.
- Client data respected the synchronized server immunity and stopped local protection immediately when the local helmet was removed.
- Client exited with code 0 and `T2S_WHEEL_QA PASS` / `T2S_WHEEL_QA CLIENT PASS`.

Production smoke run without T2S: `t2s-wheel-integration/run-smoke-no-t2s-4.6.0`. The combined JAR loaded, original graphics menu opened, startup optimization remained locked and the client exited with code 0 / `PASHABOOST_SMOKE PASS`.

Test harness sources are included under `compat-tests/t2s/`; its JAR is not included in the release. Existing saves, adaptation keys, configuration names and embedded optimizer version are retained. The optional compatibility has been tested against T2S 2.0; incompatible future capability schemas are logged and skipped instead of crashing the game.
