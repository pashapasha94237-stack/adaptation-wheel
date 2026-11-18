# Licences and integrated components — Adaptation Wheel 4.51.2

The Adaptation Wheel's own code, data, models, textures, sounds and texts are
released under the **MIT License** (see `LICENSE` in the repository root).
This file lists everything in the installable JAR that is *not* the wheel's own
work, with its licence, origin and the exact source that corresponds to it.

The release is one installable JAR. Forge loads one further mod from inside it
(Jar-in-Jar, `META-INF/jarjar/metadata.json`): the **Adapt Boost** optimizer. It
keeps its own mod ID, licences, notices and configuration files. No notice or
author attribution has been removed from it.

Since 4.50.0 the wheel no longer bundles or uses Distant Horizons. Terrain data it
recorded earlier (`DistantHorizons.sqlite` files) stays on disk and can be
deleted by hand; Distant Horizons can still be installed as a separate mod.
Adapt Boost's optional Distant Horizons lightmap optimisation only applies then.

## Adapt Boost 1.7.0 (mod ID `pashaboost`, formerly Pasha Boost)

- Packaged as `META-INF/jarjar/adapt-boost-1.20.1-forge-1.7.0.jar`.
- SHA-256: `b3e943ffdc9eda7cff194fe6c44871be2537a8fdc638aa2d0d2da20c141fd733`.
- Licence: **MIT** for Adapt Boost's original code. Two classes derived from
  ModernFix (`CompactGlyphRows`, `UnihexGlyphMixin`) remain **LGPL-3.0-or-later**;
  the FerriteCore-derived memory classes remain **MIT** (copyright malte0811).
  The full texts, the upstream notices and the exact upstream commits are kept
  inside that JAR under `LICENSE` and `META-INF/licenses/` and are explained in its
  `META-INF/THIRD-PARTY.md`.
- Ideas (no code copied) are credited there to particle_core, Clumps, Dynamic FPS
  (MIT) and to ImmediatelyFast, Embeddium, Sodium, Lithium (LGPL-3.0) and
  MoreCulling (GPL-3.0).
- Corresponding source: `embedded-src/adapt-boost-1.20.1-forge-1.7.0-sources.zip`
  (Adapt Boost sources, build scripts, third-party licence texts and the full
  ModernFix source archive it derives from).
- All optimisation stays switched off until the fourth stage of the wheel's
  «Low FPS» adaptation is earned with an enabled, equipped wheel. It contains no
  further embedded engines. Replace the JAR in `embedded/` and rebuild the wheel to
  use a modified build; keep its version consistent with
  `src/main/resources/META-INF/jarjar/metadata.json` and `mods.toml`.

## Knowledge about other mods

- The compatibility tables (`src/main/resources/compat/*.json`, the damage-type
  tags under `data/adaptationwheel/tags/damage_type/adaptation/` and the prepared
  knowledge base `knowledge/pretrained-v1.json` of the `trained` build) contain
  only identifiers, names and categories observed in the public data and
  registries of other mods. No code or assets of those mods are included.
- The coremods under `src/main/resources/coremods/` patch methods of Minecraft
  and of specific mods at load time; they contain no copied code.
- The compatibility QA harness under `compat-tests/` needs the other mods' JARs
  as local inputs; those JARs are not part of this repository or of any release.

## Names and references

- The wheel looks «Malevolent Shrine» and «Infinite Void» are original box
  models made for this mod. Their names reference the *Jujutsu Kaisen* manga and
  anime by Gege Akutami; no artwork, audio or text of that work, or of the
  Jujutsu Craft mod, is included.
- Minecraft is a trademark of Mojang AB / Microsoft. This is an unofficial mod
  and is not affiliated with or endorsed by Mojang or Microsoft.
- Forge is licensed under LGPL-2.1 and is not bundled; the mod is built against
  Forge 1.20.1-47.4.0 and runs on 47.4.0 or newer.

## Verifying the bundled components

`package-release.ps1` refuses to package a release if the bundled Adapt Boost
JAR differs from the hash above, or if any of its licence files is missing. The
hashes of the released wheel JARs themselves are listed in
`VALIDATION-<version>.md` and in `dist/adaptation-wheel-1.20.1-<version>-SHA256SUMS.txt`.
