# Проверка 4.45.1

Задание AdaptationHub t-20261107-233314 (доделка 4.45.0: «сделай то чего нет в этой версии»). Интерфейс не менялся, дизайн не нужен.

## Урон в обход колеса: 7 мест из аудита 4.26
Классы из jar корпуса разобраны `javap -c` (копии во временной папке, после разбора удалены).

| Мод | Место | Что делает | Итог |
|---|---|---|---|
| Hex Casting | `Mishap$Companion.trulyHurt` | если `hurt` вернул false — `setHealth(health-amount)`; `die` только у умирающего | ловит `AutonomousHealthGuard` (свой ключ `health_write`) |
| Goety | `HysteriaEffect` | `setHealth` | ловит guard |
| Mana and Artifice | `EffectLacerate` | `setHealth` | ловит guard |
| Enigmatic Legacy | `GrowingBloodlustEffect` | `setHealth` | ловит guard (добровольность не подтверждена — защита остаётся) |
| Re-Avaritia | `InfinitySwordItem.hurt` / `onLeftClickEntity` | `setHealth(getHealth()-x)`, свой `die` (запись `f_20890_`) только после `isDeadOrDying` | guard держит HP > 0, до `die` не доходит |
| Re-Avaritia | `InfinityHandler.onEntityHurting` | `setHealth` внутри `actuallyHurt` (первичный кадр — avaritia) | ловит guard; `onDeath` — лечение |
| Goety: Revelation | `LivingEntityMixin.protect` | воскрешение на `checkTotemDeathProtection` | уже `lifecycle` |
| Simply Swords | `BerserkPower.postHit` | снижает HP цели | ловит guard |
| Simply Swords | `WardPower.use` | `player.setHealth(getHealth()/2)` — цена способности | **исправлено**: добавлено в `lifecycle` |
| Simply Swords | `tryRevive` (на `m_21262_`) | воскрешение | уже `lifecycle` |

Coremod-патчи не нужны: всё идёт через `LivingEntity.setHealth` или `SynchedEntityData.set(DATA_HEALTH)`.

## Тесты
- `release-4.45.1.ps1`: четыре варианта, `coreTest` в каждом; `package-release.ps1` (Boost 1.7.0 и DH 3.3.3 байт-в-байт, ключ changelog 4.45.1).
- GameTests 191/191 (`runGameTestServer`, без встроенных jar).

## Не проверено
- Ни одно из семи мест не запускалось в живом клиенте с этими модами; вывод сделан по байткоду. Исключение для Ward нельзя проверить юнит-тестом: `owner()` не находит мод у поддельного класса.
- «Откуда знание» по-прежнему не снята в живом клиенте и не покрыта GameTest: в мире без модов нет типов урона из таблицы.
