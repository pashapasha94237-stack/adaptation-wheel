# Validation 4.23.0 (Forge 1.20.1) — J menu and HUD

Date: 2026-10-25. Adapt Boost unchanged (1.6.1).

## Request
Pasha: «улучши интерфейс».

## What was wrong
Measured on the 4.21.1/4.22.0 screenshots (`screens*` probe groups) and Pasha's own settings (`options.txt`:
`guiScale:3`, `lang:ru_ru`, menu key Caps Lock; his screenshots are 3440×1417, the launcher opens 925×530):

1. **Wide windows wasted the screen.** Maximised at GUI scale 3 the GUI is 1146×472, but the menu stayed 620 wide
   and the details opened as a separate screen with half of it empty.
2. **The launcher's window was cramped.** 925×530 gives GUI 462×265 (scale 3 is capped to 2): five list rows, the
   footer hint cut to «Строка — подробности, пере…».
3. **The HUD showed through the details** (panel alpha 0xF2), and in the corner panel a running first stage read
   «защита 0%» — the current protection, not the one being learned.
4. **Analysis was a wall of text**: the verdict and confidence were lines 9–10 of 14, explanations in the same
   colour as facts; the speed line repeated the progress line.
5. Category picker: names cut («Отрицательные эфф…»), no idea how many entries a category has.
6. Untranslated: `damage:minecraft:sting`, `fireworks`, `outside_border` showed «Sting» / «Fireworks»; effect
   analyses showed the raw key `gui.adaptationwheel.analysis.effect_severity` (no translation existed).
7. No keyboard use beyond Esc and the menu key.

## Change
- `client/MenuLayout` (pure arithmetic): GUI width ≥ 720 → list and a `DetailView` side by side (list 330–520,
  details the rest, panel up to 1100); height < 300 → compact header (title and summary on one line, rows 22 px).
  `DetailView` is shared by the split pane and the narrow `DetailScreen` (back chevron, Esc/Backspace back).
- Header: close cross instead of the «Готово» button; status line at the bottom: what a click does where the mouse
  is, otherwise the keys; the «not equipped» warning keeps priority.
- Keyboard: ↑↓, PageUp/PageDown, Home/End select (selection outlined, scrolled into view, kept across reopen);
  Enter opens details (narrow); Space toggles the selected row; letters go to the search, Ctrl+F focuses it;
  ↑↓/Enter from the search return to the list; Esc clears the search, then leaves it, then closes. The menu key
  still closes the menu (also over the category picker); as a letter key it is not typed into the search.
- Rows: a running protection stage reads «этап 1/4 → 20% · 5.0 с». Category picker: count per category for the
  current tab and search, empty ones faint, 2 or 3 columns of 180, drawn after `flush()` at z+300 so batched text
  (search hint, category label) no longer shows through.
- Details: «Состояние» without the speed line; analysis starts with «Категория: … · уверенность N%» and a bar (green
  ≥ 85, gold ≥ 60, orange below), then reason and source; leaks in orange; explanations faint; recommended timings
  moved to «Время этапов» with the source ID for Configured. Toggle label shortened to «Включена».
- HUD: «Этап 1 → защита 20%»; hidden while the menu or its details are open (it peeked out beside the wide menu).
- Opaque `PANEL`; search hint cut to the field; names for sting/fireworks/outside_border; the missing
  `analysis.effect_severity` text.

## Review
A Workflow review (4 dimensions: API use, regressions, interaction, layout; every finding re-checked by an
adversarial verifier) confirmed 11 findings = 7 distinct issues, all fixed before release:
1. Up from the search fell through to vanilla arrow navigation and focused the global «Адаптация» switch; arrows
   still moved the list, so the next Space turned the **whole wheel** off. Left/Right did the same.
2. A letter menu key (default J, Russian «о») closed the menu instead of starting a search — documented as the
   menu key's job, Ctrl+F covers that letter.
3. The menu key did nothing while the category picker was open.
4. The hover hint promised Space toggles the hovered row; Space acts on the selected one — hint now says «Клик».
5. Down/Enter from the search could keep a selection scrolled out of view, so Enter opened a hidden row.
6. The search hint ran over the category button in narrow columns (e.g. GUI 853×480).
7. The `smart-analysis` probe reflected `DetailScreen.maxScroll`, now in `DetailView` (fixed in both probe copies).
The first review run hung (agents stopped answering after 7 minutes); it was stopped and rerun with a narrower brief.

## Checks run
- Core tests (`coreTest`): new `MenuLayoutTest` — 17 633 GUI sizes from 320×240 to 1920×1080: panel on screen,
  header rows, tabs, search, category button, list, status line and details pane apart; ≥ 4 rows; Pasha's
  1146×472 splits with ≥ 13 rows and a ≥ 500×300 details pane; 462×265 is compact with 8 rows (5 in 4.22).
- Real client, new groups (the hidden QA window cannot be resized, so each size is its own launch):
  `screens` (480×320, plus Adapt Boost screens), `screens-compact` (924×530 → 462×265), `screens-wide`
  (2292×944 at scale 2 → 1146×472, same GUI as Pasha's 3440×1417 at 3). Each checks the layout mode, Home/↓/↑
  selection, type-to-search, ↑ from the search back to the list with no widget focused, → not moving focus, Esc
  clearing then leaving the search, details opened (narrow) or selected (split) for a running stage, a boss with
  existence, a bee analysed from a real sting and a meta mechanic; screenshots reviewed by eye. **PASS** (all three).
- Regression on the same jar: `ingame-vanilla`, `smart-analysis-base`, `smart-analysis-native` (its details screen
  screenshots, through the new `DetailView`), `deep-native-analysis` (details of a measured bypass) and
  `analyzer-pasha` (Pasha's 11 mods, all 7 rows as in 4.22) **PASS**.
- Release script: Adapt Boost 1.6.1 clean build + tests, wheel offline + online `build` with core tests.

## Release files
- `dist/adaptation-wheel-1.20.1-4.23.0-offline.jar` — sha256 `ac164efc604ebabd8068193121c92e5a73bc2a9dd20a93ba205e998402a4d17c`
- `dist/adaptation-wheel-1.20.1-4.23.0-online.jar` — sha256 `7a6564ecb8acd12d56fa5e67f12460b8f23efabef2dc38642125c8e0718f1ec8`
- `dist/adaptation-wheel-1.20.1-4.23.0-sources.zip` contains this file, so its hash is only in
  `dist/adaptation-wheel-1.20.1-4.23.0-SHA256SUMS.txt`.
- The QA runs used the same code built before the last changelog wording change (lang text only).
- A pre-review build (sha256 `bb18034b…86ae0af`) was sent to Pasha on 2026-10-24; it lacks the 7 review fixes.

## Not verified
- Pasha's own game: the screenshots use a QA world at the same GUI sizes, not his window or resource pack.
- OptiFine: Pasha removed it; `screens-optifine` was not rerun.
- Mouse-only paths in the real client beyond what the probe calls (`openRow`, buttons); clicks were reviewed in code.
