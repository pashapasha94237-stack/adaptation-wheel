# Проверка 4.48.0 — скорость от повторных ударов видна, время в секундах адаптации

Дизайн AdaptationHub `hit-acceleration-speed-2026-11-12`, ревизия 1 (утверждён), задача t-20261113-113307.
Adapt Boost 1.7.0 и Distant Horizons 3.3.3 не менялись. `WheelNetwork.PROTOCOL` "21" → "22" — отличие от дизайна
(«протокол не меняется»): клиенту нужен процент опыта стихии каждой строки (`WheelEntry.experiencePercent`), поэтому
клиент и сервер должны быть 4.48.0 вместе.

## Что сделано

- Время в меню J и HUD — работа, оставшаяся при ×1 (`ClientWheelState.remaining`); повторный удар его не уменьшает,
  а ускоряет отсчёт. `realRemaining` (работа ÷ скорость) — только порядок строк «Сводки» и «закончится первой»;
  `AdaptationScreen.shownSeconds` показывает и там время при ×1.
- Скорость — золотой значок: карточка «Сейчас» «×1.30 · быстрее на 30%» (`gui.adaptationwheel.card.speed_chip_full`,
  короче «×1.30», если не помещается), строка HUD «×1.30» справа от времени.
- Повторный удар на том же этапе (рост `hits` между пакетами) подсвечивает строку на 30 тиков надписью «+30% скорости»
  (`gui.adaptationwheel.hud.hit_speed`, цвет `WheelUi.LIT`); первый пакет вспышки не даёт.
- Опыт стихии (`ElementExperience.percent`: 5 % за освоенный источник, не больше 35, 0 для ENVIRONMENT) больше не
  сокращает длительность этапов, а входит в скорость с начала: `AdaptationState.speedFor(hits, percent, exp)` =
  1 + percent/100 × hits + min(exp, 35)/100, одна формула на сервере и в `WheelEntry.speed()`. Выключатель
  `analyzer.elementExperience` (и выключенный анализатор) убирает её.
- Тексты «Сейчас», абзац «Время» в «Подробностях для настройки», `/adaptation why` и `status` говорят об опыте как о скорости.
- Ключ журнала `meta.adaptationwheel.changelog.4.48.0`; `package-release.ps1` проверяет его и новые ключи в обоих языках.

## Результаты

| Проверка | Итог |
| --- | --- |
| `coreTest` (`AnalyzerRulesTest.experience` — проценты и `speedFor`; `UiClarityTest.realTime` — скорость с опытом, время ×1, вспышка после повторного удара и её конец через `HIT_FLASH_TICKS`) | PASS во всех четырёх вариантах |
| GameTests (`runGameTestServer`, без встроенных jar; `ClassifierGameTests.paceAndExperienceShapeTheSchedule` — длительности с опытом равны обычным, опыт 10 %, 0 у спокойного игрока и при выключенном переключателе) | **191/191** |
| `release-4.48.0.ps1` | код выхода 0; 4 jar, исходники, SHA256SUMS в `dist/`; Boost 1.7.0 и DH 3.3.3 побайтово те же |

SHA-256:

```
e08b88a063915226d2d1af1b015ecc48a653bd2f7297909108e87af8645a6fc3  adaptation-wheel-1.20.1-4.48.0-trained.jar
3d48de3f72d9f20cbdfa1a4538a7a84e242cf37394e4fcfabe270ecee3ef7c1e  adaptation-wheel-1.20.1-4.48.0-untrained.jar
ef89ab50e2725e5cf797a0b1d97f729499c2e074a8b5b7e8657326ad6eac952b  adaptation-wheel-1.20.1-4.48.0-trained-online.jar
79b8ecd8faf8927b2ce18160c15f7b9698b8a029d1e2222ec3c1421cf0c647a1  adaptation-wheel-1.20.1-4.48.0-untrained-online.jar
f582845fa468e3117c80ba1b5897fcd92968de5de7d9f9def2c8d6e85a04e932  adaptation-wheel-1.20.1-4.48.0-sources.zip
```

## Что не проверено

- Живой клиент: меню и HUD не снимались (probe-группы `screens-*` не запускались), сравнения со screen-1..4 в игре нет.
- Вариант баланса из макета «+8 % за источник» не взят: оставлены прежние 5 % и предел 35 %.
