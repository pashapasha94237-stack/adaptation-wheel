# Adaptation Wheel 4.51.2

Resource-only update: the wheel recipe uses eight gold ingots around one Nether Star. The recipe-book unlock criterion now accepts a Nether Star instead of an Ender Pearl. The advancement hint and player guides describe the new ingredients. Main download: trained-online.

Release checks: four clean builds with `--offline`, each running core tests. Packaging validates the exact 3x3 pattern, ingredient IDs, recipe result, recipe-book criterion, variant flags, prepared knowledge, licensing and unchanged embedded Adapt Boost 1.7.0 in every JAR. Gameplay class hashes are compared against the corresponding published 4.51.1 build.

The last full GameTest run was 191/191 on 4.51.1. No new GameTest or live-client run is claimed for this resource-only patch. The 4.51.1 physical projectile parry and network protocol 26 are retained.

Results: all four builds and core test runs passed. Recipe and package checks passed in all four JARs. Every gameplay class is byte-identical to the corresponding 4.51.1 release.
