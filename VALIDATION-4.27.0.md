# Проверка релиза 4.27.0 — закрытые обходы NuclearCraft, Dragon Survival и Mana and Artifice

Дата: 26 октября 2026. Minecraft Java 1.20.1, Forge 47.4.0+ / Java 17.
Основа — весь выпущенный 4.26.0, включая 4.23.0, адаптацию к промахам 4.24, обучение 4.25 и защиту родных HP/состояний 4.26.

## Что изменено

1. **NuclearCraft 1.2.36.** `coremods/corpus_states.js` перехватывает `PlayerRadiation.updateContaminationStage(Player)`: четыре чтения поля `radiation:J` идут через мост. Полная адаптация очищает настоящую дозу до порога, при котором NC наносит 1 000 000 HP. Нативная ветка stage-zero при этом снимает прежние эффекты заражения. Частичная защита уменьшает только эффективную дозу для симптомов. Сохранённая доза и decay не меняются. Большие long, вплоть до `Long.MAX_VALUE`, не теряют точность через double.
2. **Dragon Survival (23.07.2026).** В `SeaDragonType.onPlayerUpdate` четыре записи `timeWithoutWater:D` идут через мост. Частичная защита масштабирует только рост счётчика обезвоживания. Уменьшение в воде и обнуление сохраняются. Полная адаптация сбрасывает накопленный счётчик до обоих родных hurt. Дыхание, вид дракона, mana и синхронизация `SyncDragonTypeData` выполняются как прежде.
3. **Mana and Artifice 3.1.11.** `coremods/extra_effects.js` и `ExtraEffects.skipInstant` ставят guard на вход мгновенных `EffectInstantChill` и `EffectManaBurn`. Полный иммунитет отменяет вредное действие до заморозки или сжигания маны. Родные no-op не создают ложного опыта: `canFreeze()==false`, пустая обычная мана (`emptyOrdinaryMana`) и amplifier 0. Brimstone намеренно не считается безвредным, потому что его собственный `consume` при нехватке ресурса наносит настоящий урон.
4. **Periodic guard.** Общий guard периодических эффектов из 4.26 теперь получает весь `MobEffectInstance` и начинает исследование, только если длительность положительная или бесконечная. Истёкший экземпляр больше не создаёт ложное воздействие. Оригинальный виртуальный `applyEffectTick` для required mixins других модов сохранён.
5. Если у другой версии NC/DS число полей или вызовов в целевом методе отличается, трансформер оставляет метод нативным. Поддержки любой версии не заявляется.

## Исправления независимого review

`CORPUS-REVIEW-4.27.md` нашёл P2: первоначальный `skipInstant` передавал в `skipNativeAction` мгновенные callbacks, которые ничего не меняют, и это создавало ложное исследование. Исправлено проверкой `canFreeze`, `emptyOrdinaryMana` (только точно известные простые классы ресурса) и amplifier 0. Повторный review закрыл P2. По NC и DS доказанных P1/P2 нет.

## Проверки именно конечного кандидата

Основной trained JAR SHA-256:
`a62f50146e557a824939f0cb41f4db62d9d3adcd7f27b96dce7b36d468891724`.

- Четыре чистые production-сборки (`release-4.27.0.ps1`, логи `build-4.27.0-{variant}.log`) и все coreTest: **BUILD SUCCESSFUL**.
- Финальный настоящий Forge GameTest server: **63/63 PASS**, `gametest-4.27.0-final.log` («All 63 required tests passed»).

### Обход был реальным: контроль на 4.26.0 падает

Те же native-проверки на прежнем trained 4.26.0:

| Каталог во внешнем probe | Ошибка 4.26 |
|---|---|
| `run-427-baseline426-nc-20261026-210439` | «Partial NC adaptation did not reduce the real native contamination stage/amplifier» |
| `run-427-baseline426-dragon-20261026-210530` | «Partial native sea dehydration delta: 1.0 != 0.8» |
| `baseline426-extraeffects-mna-20261026-210500` | «Fully adapted instant effect still changed actual cold BEFORE tick maintenance: mna:instant_chill frozen=307» |

### 4.27.0 trained — все PASS

| Группа / каталог `run-final427-trained-…` | Реально проверено |
|---|---|
| `corpus-states-nuclearcraft-20261026-204449` | Без защиты — настоящий stage3 и потеря HP; частичная защита снижает до stage2; полная блокирует дозу выше максимума и `Long.MAX_VALUE`. |
| `corpus-states-dragon-20261026-204454` (`corpus-states-sea-dragon`) | Частичная адаптация — прирост обезвоживания −20 % (0.8 вместо 1.0); полная сбрасывает счётчик до родного урона. |
| `corpus-effects-mna-20261026-204500` | Instant Chill на IV этапе не замораживает; Mana Burn на этапе 0 тратит 25 маны/HP, на IV — 0; boots/canFreeze=false, пустая мана и amplifier 0 не дают опыта. |
| `corpus-effects-yungs-20261026-204515` | Мгновенные эффекты YUNG, peaceful/spectator без ложного опыта. |
| `corpus-health-trio-20261026-204556`, `-hexcasting-…204607`, `-avaritia-…204614`, `-mna-…204723` | Защита родных HP-записей корпусных модов. |
| `corpus-health-enigmaticlegacy-20261026-210140` | PASS при последовательном повторе (см. «Среда»). |
| `autonomous-health-20261026-204732` (`native-sinks`), `-biomancy-…204738` (`required-mixins`), `-firstaid-…210105` | Прежняя защита 4.26: setHealth/DATA_HEALTH, First Aid, required mixins Biomancy. |
| `autonomous-states-mekanism-…204802`, `-sanity-…205008`, `-tan-…205134` | Радиация, безумие и жажда 4.26 без регрессий. |
| `learning-optifine-20261026-205004`, `screens-wide-…205024` | Обучение trained и интерфейс. |
| `aim-guns-optifine-20261026-205158`, `all24-…205214` (`combined-24-mods-cached`) | Промахи/наведение с оружием и общая сборка из 24 интеграций. |

### 4.27.0 untrained — все PASS

`run-final427-untrained-learning-untrained-20261026-205922` (`learning-untrained-wide`), `…-corpus-states-nuclearcraft-…205927`, `…-corpus-states-dragon-…205934`, `…-corpus-effects-mna-…205939`.

Полные игровые логи: `<lab>/multi-mod-compat-1.20.1/probe/<run>/logs/latest.log`.

### Среда: параллельный запуск

При четырёх клиентах одновременно два прогона не прошли:

- `corpus-health-enigmaticlegacy-…204625`: таймаут окна, exit 1, результат не записан.
- `autonomous-health-firstaid-…204858`: «Second native instant potion did not add exactly one x0.30 acceleration».

Оба прошли при последовательном повторе. Для First Aid дополнительно проведён стресс-тест: 2 раунда по 4 клиента одновременно, каталоги `stress-firstaid-r{1,2}-{427a,427b,426a,426b}-…`. Результат 4.27 — **4/4 PASS**. 4.26 — 4/4 FAIL ещё раньше, на «An expired native instance created a false exposure without running its callback». Эту ошибку исправляет periodic guard 4.27. Единичный сбой на втором Harming воспроизвести не удалось, он считается нестабильностью перегруженной машины. Сравнить этот шаг с 4.26 нельзя, потому что 4.26 до него не доходит.

## Сохранность и упаковка

- `CORPUS-REVIEW-4.27.md`: по сравнению с ZIP 4.25 и 4.26 сохранены все 90 и 93 Java-файла. Намеренно изменены только `DamageCategories`, `ModAttackCompatibility`, `NegativeEffects` и `SmartAttackAnalyzer`. Data, assets, предметы, сеть, configs и таблицы прежние. 12 из 13 coremods 4.26 не изменились, расширен только periodic guard. Ordinal Evidence сохранены. Снимок — `reverse427/preservation427.json`.
- Встроенные Adapt Boost 1.6.1 (220 754 байт, `7bd5caf1…5524d`) и Distant Horizons 3.3.3 (27 700 542 байт, `a537a711…a14e3`) побайтово прежние, `release-4.27.0.ps1` проверяет это после сборки.
- SHA-сводка: `dist/adaptation-wheel-1.20.1-4.27.0-SHA256SUMS.txt`, все пять файлов сверены после GameTest:

| Файл | Байт | SHA-256 |
|---|---|---|
| trained | 28 313 347 | `a62f50146e557a824939f0cb41f4db62d9d3adcd7f27b96dce7b36d468891724` |
| untrained | 28 309 301 | `7255db367ddb9e7aa91bc32e774258b49a9c6dae001cbf1ac0798a2cbc10e6d4` |
| trained-online | 28 313 356 | `a60fa79b963c52a6fa4a2fdc92631b47c85aa35035fc20a5a6850e26580e2bde` |
| untrained-online | 28 309 311 | `b5eed8e0a4d617f12f93f2c8e3b3aaa68d99c93dcf8ca6e14f474162f53a2171` |
| sources.zip | 52 975 900 | `23927dca1b303b08d0763c89044c8cc899b80d092476ac513f9dce1539031772` |

## Честные границы

Закрыты конкретные пути в проверенных версиях NC 1.2.36, Dragon Survival 23.07.2026 и MnA 3.1.11. Другие версии оставляются нативными, если байткод не совпадает. Вызовы Brimstone.consume из заклинаний не переписаны. Корпус из 616 JAR проанализирован статически, в игре запускались только перечисленные группы, а не все 616 модов вместе. Установленные моды, миры и настройки пользователя не изменялись. Git-коммитов не создавалось.
