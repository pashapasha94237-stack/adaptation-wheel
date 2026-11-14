# Release notes — Adaptation Wheel 4.49.0

Use the English part as the body of the GitHub release (tag `v4.49.0`) and as the
changelog of the Modrinth / CurseForge files. The Russian part is for Russian sites.

---

## Adaptation Wheel 4.49.0 — Minecraft 1.20.1 / Forge 47.4.0+

One jar contains the wheel, the Sword of Extermination and the embedded Adapt Boost 1.7.0
optimizer.

### What's new in 4.49.0

- **Distant Horizons was removed.** Distant Horizons 3.3.3 is no longer bundled in the jar; the wheel no longer uses it. You can still install Distant Horizons as a separate mod if you want it. Adapt Boost's optional Distant Horizons lightmap optimisation only applies when DH is installed separately.
- The **Landscape memory** utility adaptation is gone, together with its two client settings `landscapeViewChunks` and `landscapeArchiveDirectory` in `config/adaptationwheel-utility-client.toml`. Saved progress of it in old worlds is dropped when the player loads. There are now 44 non-combat adaptations (was 45).
- Terrain data Distant Horizons already recorded (`DistantHorizons.sqlite` files) stays on disk; delete it by hand if you no longer need it.
- **Memory pressure** at stage IV no longer touches LOD detail. It only lowers the render distance by 2 chunks at a time (not below 6) while memory is short and returns it after 30 calm seconds. Garbage collection is never forced.
- The **Infinite Void** look no longer draws the blue and red rings at the wearer's feet. Their appearance setting `voidRings` («Blue and red rings (Infinite Void)») is gone from `adaptationwheel-appearance-client.toml` and Configured; an old config file that still has the line is corrected by Forge on the first start. The look's model, the stream of glyphs over nearby mobs and the star dust are unchanged; the «Blue» and «Red» orbs of the model itself stay.
- The network protocol is now 23: client and server must both run 4.49.0. 4.48.x and 4.49.0 do not connect to each other.

The full history is in [CHANGELOG.md](https://github.com/pashapasha94237-stack/adaptation-wheel/blob/main/CHANGELOG.md) (Russian).

### Files

Install **one** jar. All variants share the mod ID `adaptationwheel`; remove any older wheel jar.

| File | What it is | SHA-256 |
| --- | --- | --- |
| `adaptation-wheel-1.20.1-4.49.0-trained.jar` | Recommended. Prepared knowledge base (506 hints) for modded mobs and attacks. | `b71809e61496a5f7dec2501daaad7e50db5f96eabbe1f7fc8ce33c5811c0124d` |
| `adaptation-wheel-1.20.1-4.49.0-untrained.jar` | Same mod without the starting base. | `96c1dfaa0449c2b36fef530dddd1e8e21b4f3572b0a4fa6d9f90b2dd7bd17f03` |
| `adaptation-wheel-1.20.1-4.49.0-trained-online.jar` | `trained` plus the experimental API advisor (off until configured). | `a8a1fe76c28e131662ad5492e95f66bd95c46bce62cb20e4423f2304fc47aa8d` |
| `adaptation-wheel-1.20.1-4.49.0-untrained-online.jar` | `untrained` plus the same advisor. | `4b39de4634e0b3c7b4be3e1c2d4b617260ad3c5cfdc120d719aadfb17a8c15d6` |
| `adaptation-wheel-1.20.1-4.49.0-sources.zip` | Sources of the wheel and of the bundled Adapt Boost. | `cadc44d858ab42b7c8e30f743329b9b069dde4cf2623be95abbe7d872792ba1d` |

### Requirements

Minecraft Java 1.20.1 · Forge 47.4.0 or newer · Java 17. Works with OptiFine 1.20.1 HD U I6_pre6. Distant Horizons is no longer included; it can be installed separately. Configured 2.2.3 is optional (graphical settings).

### Checks

Unit tests of the pure core (`coreTest`) in all four variants, 192 Forge GameTests, embedded Adapt Boost 1.7.0 verified byte-identical through the build, no Distant Horizons or Landscape memory texts in any jar, licence and notices checked inside every jar. Details: `VALIDATION-4.49.0.md`.

---

## Колесо адаптации 4.49.0 — Minecraft 1.20.1 / Forge 47.4.0+

В одном jar — колесо, Меч истребления и встроенный оптимизатор Adapt Boost 1.7.0.

### Что нового в 4.49.0

- **Distant Horizons убран.** Distant Horizons 3.3.3 больше не встроен в jar, и колесо им больше не пользуется. Если он нужен, его можно поставить отдельным модом. Необязательная оптимизация карты освещения Distant Horizons в Adapt Boost работает, только если DH установлен отдельно.
- Небоевая адаптация **«Память ландшафта»** убрана вместе с двумя клиентскими настройками `landscapeViewChunks` и `landscapeArchiveDirectory` в `config/adaptationwheel-utility-client.toml`. Сохранённый прогресс по ней в старых мирах отбрасывается при входе. Небоевых адаптаций теперь 44 (было 45).
- Данные ландшафта, которые Distant Horizons уже записал (файлы `DistantHorizons.sqlite`), остаются на диске; если они не нужны, их можно удалить вручную.
- **«Нехватка памяти»** на IV этапе больше не трогает детализацию LOD: она только снижает дальность прорисовки по 2 чанка (не ниже 6), пока памяти не хватает, и возвращает её после 30 спокойных секунд. Сборщик мусора принудительно не вызывается.
- Облик **«Безграничная пустота»** больше не рисует синий и красный круги у ног носителя. Настройка облика `voidRings` («Синий и красный круги («Пустота»)») убрана из `adaptationwheel-appearance-client.toml` и Configured; старый файл настроек с этой строкой Forge исправит при первом запуске. Модель облика, поток знаков над мобами и звёздная пыль не изменились; шары «Синий» и «Красный» в самой модели остались.
- Протокол сети теперь 23: и клиент, и сервер должны стоять на 4.49.0. 4.48.x и 4.49.0 друг к другу не подключаются.

Полная история — в `CHANGELOG.md`.

### Файлы

Ставится **один** jar. У всех вариантов один mod ID `adaptationwheel`; старый jar колеса нужно удалить.

| Файл | Что это |
| --- | --- |
| `adaptation-wheel-1.20.1-4.49.0-trained.jar` | Рекомендуется. Подготовленная база (506 подсказок) по мобам и атакам из модов. |
| `adaptation-wheel-1.20.1-4.49.0-untrained.jar` | Тот же мод без стартовой базы. |
| `adaptation-wheel-1.20.1-4.49.0-trained-online.jar` | `trained` плюс экспериментальный советник через API (выключен, пока не настроен). |
| `adaptation-wheel-1.20.1-4.49.0-untrained-online.jar` | `untrained` плюс тот же советник. |
| `adaptation-wheel-1.20.1-4.49.0-sources.zip` | Исходники колеса и встроенного Adapt Boost. |

Контрольные суммы SHA-256 — в английской части выше.

### Требования

Minecraft Java 1.20.1 · Forge 47.4.0 или новее · Java 17. Работает с OptiFine 1.20.1 HD U I6_pre6. Distant Horizons больше не входит в мод; его можно поставить отдельно. Configured 2.2.3 — по желанию (графические настройки).
