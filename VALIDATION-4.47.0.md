# Проверка 4.47.0 — что такое «определения» в окне «Обучение»

Дизайн AdaptationHub `learning-definitions-help-2026-11-12`, ревизия 1 (утверждён), задача t-20261112-125747.
Adapt Boost 1.7.0 и Distant Horizons 3.3.3 не менялись, `WheelNetwork.PROTOCOL` не менялся.

## Что сделано

- `client/LearningScreen.studyPanel`: под «N из M определений» строка `learning.adaptationwheel.study.what`, рядом с числом
  золотой квадрат «?» (9 px); при наведении на него — подсказка `help()`: заголовок `study.what.title` (золотой),
  `.intro`, `.damage`, `.effects`, `.mobs`, `.pace` (число из `LearningConfig.definitionsPerSecond()`), `.new` (серый), перенос по 260 px.
- `LearningLayout`: `STUDY_H` 86 → 96, `TILE_H` 40 → 45 (две строки плиток чисел по-прежнему равны панели изучения).
- Компактное окно: значка нет, та же подсказка при наведении на строку изучения (дополнение к макету).
- Отличие от макета: в макете определение — «тип урона, эффект или моб», но `WorldStudy` считает только типы урона и эффекты;
  текст сказан по коду, мобы вынесены в строку «Мобов анализатор узнаёт в бою, а не здесь».
- Ключ журнала `meta.adaptationwheel.changelog.4.47.0`; `package-release.ps1` проверяет его и новые ключи в обоих языках.

## Результаты

| Проверка | Итог |
| --- | --- |
| `coreTest` (`LearningLayoutTest` на 17 633 размерах GUI и все прежние) | PASS во всех четырёх вариантах |
| GameTests (`runGameTestServer`, без встроенных jar) | **191/191** |
| `release-4.47.0.ps1` | код выхода 0; 4 jar, исходники, `SHA256SUMS` в `dist/`; Boost 1.7.0 и DH 3.3.3 побайтово те же |

SHA-256:

```
608303169eb5384ea68275d1bdd635adb94576d1be68a3e29e3e8a59d152ff71  adaptation-wheel-1.20.1-4.47.0-trained.jar
ab20ebca03dd54208a0f5348e88c7ef063ea125d831c2289eaeaa5ed0fde61fe  adaptation-wheel-1.20.1-4.47.0-untrained.jar
cf94deae38c348f0dcb91dc69ede38441f29a8fc64ffe364a3753bb5f75df8bd  adaptation-wheel-1.20.1-4.47.0-trained-online.jar
30a6358dd1e13ad7530174ecc7178244cc00514456fb591bc3809ef181d75801  adaptation-wheel-1.20.1-4.47.0-untrained-online.jar
9ec4403046ae8bdc6f4b668275d422d8b4084589b4a6d56480c0b7031655519a  adaptation-wheel-1.20.1-4.47.0-sources.zip
```

## Что не проверено

- Живой клиент: окно не снималось на экран (probe-группы `learning-*` не запускались), сравнения с screen-1/screen-2 в игре нет.
