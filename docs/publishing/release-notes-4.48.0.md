# Release notes — Adaptation Wheel 4.48.0

Use the English part as the body of the GitHub release (tag `v4.48.0`) and as the
changelog of the Modrinth / CurseForge files. The Russian part is for Russian sites.
Replace `pashapasha94237-stack` with the real account.

---

## Adaptation Wheel 4.48.0 — Minecraft 1.20.1 / Forge 47.4.0+

First public release. One jar contains the wheel, the Sword of Extermination, the
embedded Adapt Boost 1.7.0 optimizer and Distant Horizons 3.3.3.

### What's new in 4.48.0

- All times in the J menu and the HUD are now **seconds of adaptation at ×1 speed**: a repeat hit no longer shrinks the number, it makes the countdown run faster. Real time (work ÷ speed) only orders the summary and picks the row that «finishes first».
- The speed is visible as a gold chip: «×1.30 · 30% faster» on the «Now» card (or just «×1.30» where space is short) and «×1.30» in the HUD. After a repeat hit the row flashes «+30% speed» for 1.5 s.
- **Element experience** (+5 % per mastered source of the same category, at most +35 %, not for «Environment») no longer shortens stage times: it is added to the speed from the start of a stage. A waiting row with experience shows «×1.15 · element experience +15%». Protection and stages still never carry over; the `analyzer.elementExperience` switch removes the speed too.
- One formula on the server and the client: speed = 1 + acceleration × hits + experience ÷ 100.
- The «Now» hints, the «Time» paragraph of the details, `/adaptation why` and `/adaptation status` describe experience as speed.
- Network protocol 22: client and server must both be 4.48.0.

The full history since 4.1.0 is in [CHANGELOG.md](https://github.com/pashapasha94237-stack/adaptation-wheel/blob/main/CHANGELOG.md) (Russian).

### Files

Install **one** jar. All variants share the mod ID `adaptationwheel`; remove any older wheel jar.

| File | What it is | SHA-256 |
| --- | --- | --- |
| `adaptation-wheel-1.20.1-4.48.0-trained.jar` | Recommended. Prepared knowledge base (506 hints) for modded mobs and attacks. | `e08b88a063915226d2d1af1b015ecc48a653bd2f7297909108e87af8645a6fc3` |
| `adaptation-wheel-1.20.1-4.48.0-untrained.jar` | Same mod without the starting base. | `3d48de3f72d9f20cbdfa1a4538a7a84e242cf37394e4fcfabe270ecee3ef7c1e` |
| `adaptation-wheel-1.20.1-4.48.0-trained-online.jar` | `trained` plus the experimental API advisor (off until configured). | `ef89ab50e2725e5cf797a0b1d97f729499c2e074a8b5b7e8657326ad6eac952b` |
| `adaptation-wheel-1.20.1-4.48.0-untrained-online.jar` | `untrained` plus the same advisor. | `79b8ecd8faf8927b2ce18160c15f7b9698b8a029d1e2222ec3c1421cf0c647a1` |
| `adaptation-wheel-1.20.1-4.48.0-sources.zip` | Sources of the wheel and of the bundled components. | `f582845fa468e3117c80ba1b5897fcd92968de5de7d9f9def2c8d6e85a04e932` |

### Requirements

Minecraft Java 1.20.1 · Forge 47.4.0 or newer · Java 17. Works with OptiFine 1.20.1 HD U I6_pre6. Do not install a second copy of Distant Horizons. Configured 2.2.3 is optional (graphical settings).

### Checks

Unit tests of the pure core (`coreTest`) in all four variants, 191 Forge GameTests, embedded Adapt Boost 1.7.0 and Distant Horizons 3.3.3 verified byte-identical through the build. Details: `VALIDATION-4.48.0.md`.

---

## Колесо адаптации 4.48.0 — Minecraft 1.20.1 / Forge 47.4.0+

Первый публичный выпуск. В одном jar — колесо, Меч истребления, встроенный оптимизатор Adapt Boost 1.7.0 и Distant Horizons 3.3.3.

### Что нового в 4.48.0

- Всё время в меню J и HUD — **в секундах адаптации при скорости ×1**: повторный удар больше не уменьшает число, а ускоряет отсчёт. Настоящее время (работа ÷ скорость) используется только для порядка строк в «Сводке» и для «закончится первой».
- Скорость видна золотым значком: «×1.30 · быстрее на 30%» в карточке «Сейчас» (или «×1.30», если места мало) и «×1.30» в HUD. После повторного удара строка 1,5 с показывает «+30% скорости».
- **Опыт стихии** (+5 % за каждый освоенный источник того же вида, не больше +35 %, не для «Среды») больше не укорачивает этапы: он прибавляется к скорости с начала этапа. Ждущая строка с опытом показывает «×1.15 · опыт стихии +15%». Защита и этапы по-прежнему не переносятся; выключатель `analyzer.elementExperience` убирает и скорость.
- Одна формула на сервере и в клиенте: скорость = 1 + ускорение × удары + опыт ÷ 100.
- Подсказки «Сейчас», абзац «Время» в подробностях, `/adaptation why` и `/adaptation status` говорят об опыте как о скорости.
- Протокол сети 22: клиент и сервер должны быть 4.48.0.

Полная история с 4.1.0 — в `CHANGELOG.md`.

### Файлы

Ставится **один** jar. У всех вариантов один mod ID `adaptationwheel`; старый jar колеса нужно удалить.

| Файл | Что это |
| --- | --- |
| `adaptation-wheel-1.20.1-4.48.0-trained.jar` | Рекомендуется. Подготовленная база (506 подсказок) по мобам и атакам из модов. |
| `adaptation-wheel-1.20.1-4.48.0-untrained.jar` | Тот же мод без стартовой базы. |
| `adaptation-wheel-1.20.1-4.48.0-trained-online.jar` | `trained` плюс экспериментальный советник через API (выключен, пока не настроен). |
| `adaptation-wheel-1.20.1-4.48.0-untrained-online.jar` | `untrained` плюс тот же советник. |
| `adaptation-wheel-1.20.1-4.48.0-sources.zip` | Исходники колеса и встроенных компонентов. |

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
