# Adaptation Wheel 4.5.0: one installable JAR

## Own OptiFine-compatible menu: 4.5.2

Includes Pasha Boost 1.3.2. The default video destination is our Screen-based menu
with or without OptiFine. An advanced-settings button preserves OptiFine-specific
controls. Only the exact native screen explicitly created by that button is
exempt from replacement, including returns from its child pages.

Production I6_pre6 / Forge 47.4.13 passes with exit 0: own UI initialization,
three tabs, performance-settings round-trip, actual OptiFine shader page and
the full return path shader -> same native -> same own screen. Natural 30-FPS
adaptation and all post-unlock/unequip checks also pass. Plain Forge 47.4.16
menu opening, tabs and performance round-trip pass with exit 0.

## Compatibility patch 4.5.1

Includes Pasha Boost 1.3.1. Fixes the reported OptiFine video-menu crash caused by
an inherited field that no longer exists in OptiFine's replacement hierarchy.
The own screen is based directly on Screen. OptiFine's native screen, shader
controls and native behavior are preserved, with a new FPS+ access button.

The actual installed OptiFine I6_pre6 / Forge 47.4.13 production check passes:
native video opening, FPS+ round-trip, real 30 FPS qualification, stage-four
activation, active light/NBT/font hooks, saving preferences and release after
unequipping. Temporary graphics survive resource reload without overwriting
manual settings. The OptiFine JAR is used only for isolated testing and is not
included in the release or source archive.

The wheel contains the exact previously tested Pasha Boost 1.3.0 binary through
Forge's Jar-in-Jar loader. There is no change to damage adaptation, hit acceleration,
sword behavior, real-frame FPS sampling, stage timings or optimization gates.
Existing wheel and optimization configuration files remain compatible.

`build` and the wheel's `coreTest` validate compilation, packaging, timing,
configuration, catalogue selection and the FPS frame sampler. The embedded binary
retains its own MixinConfigs manifest, internal mod metadata and license notices.

Production integration is checked with one JAR in an isolated Forge 47.4.16 /
Minecraft 1.20.1 test profile using:

```
smoke-client.ps1 -CombinedWheelJar <built-wheel-4.5.0.jar> -LowLevel
```

The opt-in test creates a temporary world, equips the wheel, caps actual rendering
at 30 FPS and checks natural qualification. Controlled samples separately finish
all four stages and verify the real optimization API. It then verifies runtime
lighting, NBT save/reopen, compact fonts, graphics restoration, particle/entity
filtering and that saving options does not persist the temporary graphics profile.

The production run passed on 2026-10-20 with exactly one JAR in `mods` and exit
code 0. Forge discovered the one nested dependency. Natural sampling recorded
30 FPS and 6.113 seconds of first-stage work after 11.520 real seconds; both the
locked and unlocked gates passed. NBT, lighting, glyph, options-save and wheel-
removal restoration checks all emitted their PASS markers.

The source archive contains the wheel source and build scripts, the exact bundled
binary, and Pasha Boost's corresponding source archive including the original
LGPL-covered source and notices. This is a packaging/integration release, not a
new FPS-performance benchmark. Dedicated-server loading has been reviewed for
client-only class boundaries; no new dedicated-server benchmark is claimed.
