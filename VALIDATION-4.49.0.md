# Проверка 4.49.0 — без Distant Horizons и без кругов «Безграничной пустоты»

Distant Horizons 3.3.3 больше не встроен в мод, вместе с ним убрана небоевая адаптация «Память ландшафта».
У облика «Безграничная пустота» убраны синий и красный круги у ног носителя. Adapt Boost 1.7.0 тот же (байт в байт).
`WheelNetwork.PROTOCOL` поднят до "23": настройки сервера (`adaptationwheel-utility-server.toml`), которые
получает клиент, потеряли ключ `landscape_memory`, поэтому клиент и сервер должны стоять на 4.49.0 вместе.

## Что сделано

- `build.gradle`, `META-INF/jarjar/metadata.json`: в jar встроен только Adapt Boost 1.7.0. Jar и исходники Distant
  Horizons убраны из git (`embedded/`, `embedded-src/`) и внесены в `.gitignore`; в коммите 4.48.1 они остались.
- `mods.toml`: строка лицензии без Distant Horizons. `THIRD-PARTY.md` без раздела Distant Horizons.
- Удалены `client/LandscapeMemory`, `server/LandscapeServerPolicy`, `MetaMechanic.LANDSCAPE`, настройки
  `landscapeViewChunks` и `landscapeArchiveDirectory` (`MetaConfig`), счётчик чанков в `MetaClient`, строка в меню J
  и все тексты «Памяти ландшафта» в обоих языках. Подсказка «Нехватки памяти» больше не обещает снижение
  детализации LOD: на IV этапе остаётся только снижение дальности прорисовки (`OutsideClient`, не менялся).
- `AdaptationEvents.RETIRED`: строка `environment:landscape_memory` в сохранении игрока отбрасывается при загрузке
  (форматы Version ≥ 2 и самый старый `Completed`/`Pending`); GameTest `retiredLandscapeMemoryRowIsDropped`.
- Круги: убраны `VoidWorld.rings` и расчёт в `VoidWorldPlan`, настройка `voidRings`, проверки в `VoidWorldPlanTest`.
- `package-release.ps1`: проверяет, что в jar нет Distant Horizons (ни в `jarjar/metadata.json`, ни среди файлов),
  нет классов `LandscapeMemory`/`LandscapeServerPolicy` и текстов «Памяти ландшафта»; ключ журнала
  `meta.adaptationwheel.changelog.4.49.0` в обоих языках; архив исходников больше не добавляет файлы Distant Horizons.
- Собственная оптимизация Adapt Boost для Distant Horizons (`DistantLightmapMixin`) осталась в Boost 1.7.0 без
  изменений: `BoostMixinPlugin` включает её, только если Distant Horizons установлен отдельно.

## Результаты

| Проверка | Итог |
| --- | --- |
| `coreTest` | PASS (отдельный прогон и во всех четырёх вариантах сборки) |
| GameTests (`runGameTestServer`, без встроенных jar) | **192/192** (191 прежний + `retiredLandscapeMemoryRowIsDropped`), прогон на итоговом коде |
| Независимое ревью кода и сборки (3 направления: сохранения, упаковка, работа клиента/сервера) | замечаний нет |
| `release-4.49.0.ps1` | PASS: чистая сборка четырёх вариантов (`coreTest` в каждом), встроенный Adapt Boost 1.7.0 байт в байт, проверки `package-release.ps1` пройдены |
| Содержимое jar | во всех четырёх: `META-INF/LICENSE` и `META-INF/THIRD-PARTY.md` совпадают с репозиторием, MIT в `mods.toml`; в `jarjar/` только `adapt-boost-1.20.1-forge-1.7.0.jar` (байт в байт); нет файлов Distant Horizons и `Landscape*`, нет ключей `landscape_memory`/`voidRings` в языках; строка `environment:landscape_memory` есть только в `AdaptationEvents` (список `RETIRED`) |
| Архив исходников | 613 файлов, нет файлов Distant Horizons и личных заметок, есть этот файл, `release-4.49.0.ps1`, примечания к выпуску, jar и исходники Adapt Boost |

После выпуска (2026-11-14) проверен опубликованный `4.49.0-trained.jar` в двух изолированных клиентах.
Группы `screens` (Forge) и `screens-optifine` завершились с кодом 0 и отметкой `MULTIMOD_QA PASS`.
Каждая проверила меню J, клавиатурное управление, карточки анализа, HUD и экраны Adapt Boost
при размере GUI 480×320 и сохранила 25 снимков. SHA-256 обоих загруженных JAR совпал с указанным ниже.
Логи и снимки сохранены локально в `build/qa449-20261114-221815`.
Группа `void440-optifine` прошла 28 проверок. На просмотренном снимке от третьего лица
видны ядро и частицы над игроком, синих и красных кругов у ног нет.
Обновлённые группы `ingame-vanilla` и `ingame-optifine` из probe в репозитории прошли
на том же JAR. В обоих прогонах мир отрисовался, 12 собственных шагов дали 0 меток,
контрольный звук шага зомби дал метку. Старые ветки `landscape` и `landscape-reload`
удалены из probe в репозитории вместе с обращениями к удалённому движку в проверке `utility`.

SHA-256:

```
b71809e61496a5f7dec2501daaad7e50db5f96eabbe1f7fc8ce33c5811c0124d  adaptation-wheel-1.20.1-4.49.0-trained.jar
96c1dfaa0449c2b36fef530dddd1e8e21b4f3572b0a4fa6d9f90b2dd7bd17f03  adaptation-wheel-1.20.1-4.49.0-untrained.jar
a8a1fe76c28e131662ad5492e95f66bd95c46bce62cb20e4423f2304fc47aa8d  adaptation-wheel-1.20.1-4.49.0-trained-online.jar
4b39de4634e0b3c7b4be3e1c2d4b617260ad3c5cfdc120d719aadfb17a8c15d6  adaptation-wheel-1.20.1-4.49.0-untrained-online.jar
```

Архив исходников содержит версию этого отчёта на момент выпуска. SHA-256 архива записан только в
`adaptation-wheel-1.20.1-4.49.0-SHA256SUMS.txt` и в `docs/publishing/release-notes-4.49.0.md`.
