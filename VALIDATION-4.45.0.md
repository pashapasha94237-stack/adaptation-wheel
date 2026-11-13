# Проверка 4.45.0

Дизайн AdaptationHub `mod-compat-616-2026-11-07` rev 1 (approved), задание t-20261107-233314.

## Что сделано
- 13 тегов `data/adaptationwheel/tags/damage_type/adaptation/*.json`: 428 записей из корпуса 616 jar, все `required: false`, без `minecraft:` и без дублей (генератор и разбор — `compat-tests/corpus445/Gen.java`, `Classify.java`).
- Новые слова в `AdaptationProfile.fromName`; 5 эффектов в `ExtraEffects.PROFILES`; 134 мобиных семени `seed:compat/<mod>/mob-name` (49 модов) в `knowledge/pretrained-v1.json`, всего 506 фактов (только `trained`).
- `DamageCategories.tableProfile/profileWithoutTable`, `AnalysisInfo.fromTable/originMod/originWithout`, `SmartAttackAnalyzer.withOrigin`; `WheelNetwork.PROTOCOL` "21".
- Меню J: строки «Откуда знание» в рамке (`AdaptationScreen.originLines`, узкий вариант при ширине < 260), ключи `gui.adaptationwheel.origin.*` в обоих языках, changelog 4.45.0.

## Тесты
- `coreTest` PASS, включая `AdaptationAnalysisTest.compatTable445` (валидный JSON тегов, `required:false`, ≥400 записей, ≥130 семян `seed:compat/`, новые слова).
- GameTests 191/191 (`runGameTestServer`, без встроенных jar).
- `release-4.45.0.ps1`: чистая сборка четырёх вариантов, проверка Boost 1.7.0 и DH 3.3.3 байт-в-байт, `package-release.ps1` (ключи changelog/origin, 506 фактов).

## Не проверено / не сделано
- Строка «Откуда знание» не снята в живом клиенте (probe `ScreensQa` не обновлялся): в мире без модов нет типов урона из таблицы.
- GameTest на происхождение из таблицы не добавлен по той же причине.
- 7 мест, где моды наносят урон в обход колеса, не закрыты coremod-патчами.
