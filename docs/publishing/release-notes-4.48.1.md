# Release notes — Adaptation Wheel 4.48.1

Use the English part as the body of the GitHub release (tag `v4.48.1`) and as the
changelog of the Modrinth / CurseForge files. The Russian part is for Russian sites.

---

## Adaptation Wheel 4.48.1 — Minecraft 1.20.1 / Forge 47.4.0+

First public release. One jar contains the wheel, the Sword of Extermination, the
embedded Adapt Boost 1.7.0 optimizer and Distant Horizons 3.3.3.

### What's new in 4.48.1

- The wheel's code is now open source under the **MIT licence**. The licence text ships inside every jar (`META-INF/LICENSE`).
- `mods.toml` states the new licence and links to this repository and its issue tracker.
- Updated notices for the bundled Adapt Boost 1.7.0 and Distant Horizons 3.3.3 (`META-INF/THIRD-PARTY.md`).
- No gameplay changes: 4.48.1 plays exactly like 4.48.0 and uses the same network protocol 22, so 4.48.0 and 4.48.1 clients and servers work together.

### Highlights of 4.48.0, included here

- All times in the J menu and the HUD are **seconds of adaptation at ×1 speed**: a repeat hit no longer shrinks the number, it makes the countdown run faster.
- The speed is visible as a gold chip («×1.30 · 30% faster»), and a repeat hit flashes «+30% speed».
- **Element experience** (+5 % per mastered source of the same category, at most +35 %) is added to the speed instead of shortening stages.

The full history since 4.1.0 is in [CHANGELOG.md](https://github.com/pashapasha94237-stack/adaptation-wheel/blob/main/CHANGELOG.md) (Russian).

### Files

Install **one** jar. All variants share the mod ID `adaptationwheel`; remove any older wheel jar.

| File | What it is | SHA-256 |
| --- | --- | --- |
| `adaptation-wheel-1.20.1-4.48.1-trained.jar` | Recommended. Prepared knowledge base (506 hints) for modded mobs and attacks. | `f03e254f96d1c614271ebc852e97c98ca36b827d9eaf7a9dd0ae41428310ee4f` |
| `adaptation-wheel-1.20.1-4.48.1-untrained.jar` | Same mod without the starting base. | `97c1f73f969db41ed1bd7da6493ce7c877beb454e791313fdd885653bc609278` |
| `adaptation-wheel-1.20.1-4.48.1-trained-online.jar` | `trained` plus the experimental API advisor (off until configured). | `14060ef83c3eb24169ab9e845ca1d52ab9d36e36c66ef7b4a70c06912c4b70fd` |
| `adaptation-wheel-1.20.1-4.48.1-untrained-online.jar` | `untrained` plus the same advisor. | `772fc4f35078077004ae413d99ceb097b94078102204a7998c56feed5ead1de5` |
| `adaptation-wheel-1.20.1-4.48.1-sources.zip` | Sources of the wheel and of the bundled components. | `89c982e86a0b43f9e914dc1f9e9fc5cc798265accb6301d3eca1e77a10398650` |

### Requirements

Minecraft Java 1.20.1 · Forge 47.4.0 or newer · Java 17. Works with OptiFine 1.20.1 HD U I6_pre6. Do not install a second copy of Distant Horizons. Configured 2.2.3 is optional (graphical settings).

### Checks

Unit tests of the pure core (`coreTest`) in all four variants, 191 Forge GameTests, embedded Adapt Boost 1.7.0 and Distant Horizons 3.3.3 verified byte-identical through the build, licence and notices checked inside every jar. Details: `VALIDATION-4.48.1.md`.

---

## Колесо адаптации 4.48.1 — Minecraft 1.20.1 / Forge 47.4.0+

Первый публичный выпуск. В одном jar — колесо, Меч истребления, встроенный оптимизатор Adapt Boost 1.7.0 и Distant Horizons 3.3.3.

### Что нового в 4.48.1

- Код колеса открыт под **лицензией MIT**. Текст лицензии лежит в каждом jar (`META-INF/LICENSE`).
- В `mods.toml` указана новая лицензия и ссылки на репозиторий и список задач.
- Обновлены сведения о встроенных Adapt Boost 1.7.0 и Distant Horizons 3.3.3 (`META-INF/THIRD-PARTY.md`).
- Игра не изменилась: 4.48.1 играется так же, как 4.48.0, протокол сети тот же (22), поэтому клиенты и серверы 4.48.0 и 4.48.1 работают вместе.

### Главное из 4.48.0 (входит в этот выпуск)

- Всё время в меню J и HUD — **в секундах адаптации при скорости ×1**: повторный удар не уменьшает число, а ускоряет отсчёт.
- Скорость видна золотым значком («×1.30 · быстрее на 30%»), после повторного удара строка показывает «+30% скорости».
- **Опыт стихии** (+5 % за каждый освоенный источник того же вида, не больше +35 %) прибавляется к скорости, а не укорачивает этапы.

Полная история с 4.1.0 — в `CHANGELOG.md`.

### Файлы

Ставится **один** jar. У всех вариантов один mod ID `adaptationwheel`; старый jar колеса нужно удалить.

| Файл | Что это |
| --- | --- |
| `adaptation-wheel-1.20.1-4.48.1-trained.jar` | Рекомендуется. Подготовленная база (506 подсказок) по мобам и атакам из модов. |
| `adaptation-wheel-1.20.1-4.48.1-untrained.jar` | Тот же мод без стартовой базы. |
| `adaptation-wheel-1.20.1-4.48.1-trained-online.jar` | `trained` плюс экспериментальный советник через API (выключен, пока не настроен). |
| `adaptation-wheel-1.20.1-4.48.1-untrained-online.jar` | `untrained` плюс тот же советник. |
| `adaptation-wheel-1.20.1-4.48.1-sources.zip` | Исходники колеса и встроенных компонентов. |

Контрольные суммы SHA-256 — в английской части выше.

### Требования

Minecraft Java 1.20.1 · Forge 47.4.0 или новее · Java 17. Работает с OptiFine 1.20.1 HD U I6_pre6. Вторую копию Distant Horizons ставить не нужно. Configured 2.2.3 — по желанию (графические настройки).

---

## Template for the next release

```
## Adaptation Wheel X.Y.Z — Minecraft 1.20.1 / Forge 47.4.0+

### What's new
- …

### Files
| File | What it is | SHA-256 |
| --- | --- | --- |
| adaptation-wheel-1.20.1-X.Y.Z-trained.jar | Recommended … | … |
| adaptation-wheel-1.20.1-X.Y.Z-untrained.jar | … | … |
| adaptation-wheel-1.20.1-X.Y.Z-sources.zip | … | … |

### Requirements
Minecraft Java 1.20.1 · Forge 47.4.0+ · Java 17. Client and server must run the same version.

### Checks
coreTest, N GameTests, embedded components byte-identical. Details: VALIDATION-X.Y.Z.md.
```

The hashes come from `dist/adaptation-wheel-1.20.1-X.Y.Z-SHA256SUMS.txt`, the «What's new» list from the top section of `CHANGELOG.md`.
