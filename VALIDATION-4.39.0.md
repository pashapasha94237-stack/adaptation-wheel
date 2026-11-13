# Проверка 4.39.0 — облик «Безграничная пустота»

Дизайн `infinite-void-2026-11-02` ревизия 1 (утверждён 2026-11-02), модель Hub `m18` скопирована без изменений в
`src/main/resources/wheel_skins/infinite_void.json`; `WheelSkin.INFINITE_VOID`, тексты `skin.adaptationwheel.infinite_void*`
и `meta.adaptationwheel.changelog.4.39.0` в обоих языках; `SkinLayout.CARDS = 9`.

## Что проверено
- `coreTest`: `SkinRigTest` — 8 обликов из моделей, 1005 деталей, атласы без наложений, m18: 122 детали, 102 светятся,
  нет мигающих групп, высоты −21,4..−11,9, в цилиндр головы ничего не заходит; `SkinLayoutTest` — 17 633 размера GUI,
  9 карточек, 462×265 компактный с карточками 23 px и строкой описания.
- `runGameTestServer`: All 171 required tests passed.
- `release-4.39.0.ps1`: 4 варианта собраны чисто, встроенные Adapt Boost 1.7.0 и DH 3.3.3 байт-в-байт,
  `package-release.ps1` проверил версию, все 8 файлов обликов и что `infinite_void.json` — m18.
  SHA-256 trained `ae59862a…c95e`, untrained `d917a0dc…c7f45`.

## Не проверено
- Живой клиент (probe `Skins433Qa` ждёт 8 обликов — во внешнем проекте его нужно поднять до 9), вид облика в игре
  и с OptiFine, низкие окна с карточками без описания.
