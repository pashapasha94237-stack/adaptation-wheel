# Adaptation Wheel

**Wear the wheel. Get hurt. Never be hurt the same way again.**

The Adaptation Wheel is a golden ring worn in the helmet slot. Everything that damages you starts its own adaptation: a zombie's bite, a blaze's fireball, a boss ability from any mod, poison, hunger, freezing, cave darkness. Each adaptation runs through four stages, **20 % → 40 % → 70 % → 100 % protection**, and every repeat hit of the same source makes the current stage tick faster (+30 % speed per hit). When a stage ends, the wheel clicks one turn, plays a sound and heals you to full.

Against bosses (100 HP or more) there is a fifth stage, **Adaptation to Existence**: the wheel spins without stopping for 200 seconds, and then you are immune to that boss and kill it in one hit, even with a bare fist.

## Highlights

- **Adapts to anything.** Every mob species, damage type, harmful effect and condition, vanilla or modded, gets its own adaptation with its own timer. The catalogue lists 191 entries in a plain game and over 1,100 with a large modpack.
- **Local analyzer, no internet.** Registries, damage-type tags, names, observed hits and a table of 428 damage types from 616 mods tell the wheel whether an attack is fire, magic, a projectile, poison, light, electricity and so on: 14 categories, each with its own stage times. If the analyzer is unsure, press **K** in the menu and correct the category yourself. `/adaptation why` explains every decision.
- **Three learning modes.** *Base only*, *Learns in combat* (remembers real hits and how protection worked) and *Combat + world study* (reads the damage types and effects of installed mods ahead of time). The `trained` build ships 506 reviewed hints for modded mobs and attacks.
- **44 non-combat adaptations.** The wheel also learns the small annoyances: sleeping by day, phantoms, «Too expensive!» anvils, broken tools (fully restored at stage IV), an empty quiver, waiting at the furnace or in a Nether portal, water in the Nether, enderman gaze, piglins, villager prices, creeper-wrecked buildings, murky water, sight through lava, a laptop on battery, the world border, an overfull inventory (unlimited stacks at stage IV), crashes, network loss, AFK and more. Every one has its own switch.
- **Low FPS adaptation.** When your FPS stays under 60 the wheel learns that too. Stage IV unlocks the embedded **Adapt Boost** optimizer (F8): glyph, particle, entity, memory and render-path optimisations for Forge, tested with and without OptiFine.
- **Sword of Extermination.** 10 damage, ×2 to undead, ×4 to mobs you pick (each with its own multiplier), parry and projectile deflection against attackers you have adapted to, and **World Slash**: after adapting to the existence of any boss, a 64-block cut through walls that deals 300 HP (150 hearts) through armor and cuts blocks (45 s cooldown).
- **Nine looks.** Golden Wheel, Clockwork Gear, Ice Crown, Armillary Sphere, Event Horizon, Ouroboros, Storm Ring, Malevolent Shrine (blades flash around you and cut the scenery, purely visual) and Infinite Void (an information stream over nearby mobs and star dust). Other players with the mod see your look.
- **A proper menu.** **J** opens the Wheel Summary: what finishes next, protection per category, weak spots and a session journal; a catalogue with search and filters; details with analysis, history and a settings fold; a HUD with the running adaptations; 8 advancements; `/adaptation` commands; full Configured support. Keyboard friendly, scales from a small window to ultrawide.

## Screenshots

- `menu-summary.jpg` — The adaptation menu (J): wheel summary, running adaptations, protection by category and the session journal.
- `menu-details.jpg` — Details of one adaptation: current protection, stage track, what it gives, history and the analyzer's verdict.
- `menu-boss-existence.jpg` — A boss after all five stages: immunity and a one-hit kill.
- `hud.jpg` — The HUD: running adaptations under the effect icons; a completed stage lights up.
- `learning.jpg` — The learning screen: three modes, world study progress and the knowledge counters.
- `looks-screen.jpg` — The looks screen with a live preview on your character.
- `look-malevolent-shrine.jpg`, `look-ouroboros.jpg`, `look-infinite-void.png` — Three of the nine looks in the world.

## Installation

1. Minecraft Java **1.20.1**, **Forge 47.4.0 or newer**, **Java 17**.
2. Download **one** jar and put it into `mods`:
   - `adaptation-wheel-1.20.1-4.49.0-trained.jar` — recommended; includes the prepared knowledge base for modded mobs and attacks.
   - `adaptation-wheel-1.20.1-4.49.0-untrained.jar` — the same mod without the starting base; the analyzer relies on game data and your own fights.
3. Remove any older wheel jar: all variants share one mod ID.
4. Craft the wheel from 8 gold ingots around a Nether Star (`/give @s adaptationwheel:wheel`), right-click to wear it, press **J** for the menu.

Adapt Boost is already inside the jar. Distant Horizons is no longer bundled and the wheel does not use it; you can still install it as a separate mod. Terrain it already recorded (`DistantHorizons.sqlite` files) stays on disk and can be deleted by hand. No other mod is required; **Configured** (2.2.3 tested) gives a graphical settings screen for everything.

**Servers:** install the same jar on the server and on every client; versions must match. The optimizer is client-side and does nothing on a dedicated server.

**OptiFine:** works with OptiFine 1.20.1 HD U I6_pre6 (the author's own setup). Looks and world effects were checked with 17 shader packs.

The `-online` variants add an experimental advisor that can call an external OpenAI-compatible API if you configure an endpoint and provide a key in an environment variable; by default they make no requests and behave exactly like the offline variants.

## Compatibility

Any mod's damage types, mobs and effects are picked up automatically through Forge events and registries. Extra handling exists for Jujutsu Craft v51 (techniques, Infinity), Try 2 Survive, Ice and Fire, Advent of Ascension 3, Twilight Forest, First Aid, Alex's Caves, Tough As Nails, SanityDim, Mekanism, NuclearCraft, Dragon Survival, Mana and Artifice, Blood Magic, Simply Swords, Supplementaries, Curios, Sophisticated Backpacks, MrCrayfish's Guns and Scorched Guns, and mods that write health directly instead of dealing damage. Releases are also run in a real client with a 24-mod test pack and with the author's 87-mod pack; each `VALIDATION-*.md` says what was run for that version.

Mod and datapack authors can set an exact category for an opaque damage type with a tag: `data/adaptationwheel/tags/damage_type/adaptation/<fire|physical|magic|effect|hunger|cold|darkness|environment|projectile|explosion|light|electric|poison|suffocation>.json`.

Languages: English and Russian.

## What's bundled (licences)

- The wheel itself: **MIT**.
- **Adapt Boost 1.7.0** (mod ID `pashaboost`): MIT, with two ModernFix-derived classes under LGPL-3.0-or-later and FerriteCore-derived memory code under MIT. Its sources are in the repository.

Full details, hashes and the source archive: `THIRD-PARTY.md` in the repository.

## Source and issues

- Source code and documentation: https://github.com/pashapasha94237-stack/adaptation-wheel
- Bug reports and requests: https://github.com/pashapasha94237-stack/adaptation-wheel/issues
- Author: Pasha. Every release is checked with unit tests and 192 Forge GameTests, most also with scripted real-client runs; the records are the `VALIDATION-*.md` files in the repository.
