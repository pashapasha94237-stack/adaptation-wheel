# Проверка 4.48.1 — лицензия MIT внутри jar

Выпуск только упаковки, код игры не менялся. Adapt Boost 1.7.0 и Distant Horizons 3.3.3 не менялись,
`WheelNetwork.PROTOCOL` остаётся "22": клиент и сервер 4.48.0 и 4.48.1 совместимы.

## Что сделано

- `LICENSE` (MIT) кладётся в jar как `META-INF/LICENSE` (`build.gradle`, рядом с `META-INF/THIRD-PARTY.md`).
- `mods.toml`: строка лицензии — MIT вместе с уведомлениями встроенных компонентов, `displayURL` и `issueTrackerURL`
  ведут на репозиторий GitHub.
- `THIRD-PARTY.md` переписан под Adapt Boost 1.7.0 (SHA-256 `b3e943ff…d733`) и Distant Horizons 3.3.3
  (`a537a711…14e3`); в jar 4.48.0 оставался текст 4.14.
- `package-release.ps1`: в каждом jar проверяет `META-INF/LICENSE` (начинается с «MIT License»),
  `META-INF/THIRD-PARTY.md` (версия 4.48.1) и MIT в `mods.toml`; ключ журнала `meta.adaptationwheel.changelog.4.48.1`
  в обоих языках. Архив исходников собирается из `git ls-files` плюс встроенные jar и их исходники, поэтому в него
  не попадают локальные заметки, журналы и чужие классы из `compat-tests/`.
- Перед публикацией из отслеживаемых файлов убраны абсолютные пути этого компьютера: данные проверочных групп
  (`compat-tests/multimod/audit/`, `probe/*.json`, `probe/gradle.properties`, `corpus/coverage.json`) больше не в git
  (`.gitignore`), скрипты в `compat-tests/` находят папку проекта от своего расположения, старые `release-4.16…4.23.ps1`
  берут папку Adapt Boost из `ADAPT_BOOST_DIR`, в отчётах пути заменены на `<lab>`/`<mods>`. В `event_horizon.json`
  поправлена служебная заметка `javaNotes` (её никто не читает, модель та же).

## Результаты

| Проверка | Итог |
| --- | --- |
| `coreTest` | PASS во всех четырёх вариантах |
| GameTests (`runGameTestServer`, без встроенных jar) | **191/191** |
| `release-4.48.1.ps1` | код выхода 0; 4 jar, исходники, SHA256SUMS в `dist/`; Boost 1.7.0 и DH 3.3.3 побайтово те же (и в `embedded/`, и внутри каждого jar) |
| Содержимое jar | `META-INF/LICENSE` есть во всех четырёх; `META-INF/THIRD-PARTY.md` совпадает с файлом репозитория; `mods.toml` — MIT, без «All Rights Reserved» |
| Архив исходников | 615 файлов; есть `LICENSE`, `THIRD-PARTY.md`, `release-4.48.1.ps1`, встроенные jar; нет локальных заметок, журналов, `artifacts/`, `reports/`, jar и class из `compat-tests/` |

В живом клиенте 4.48.1 не запускался: игровой код тот же, что в 4.48.0.

SHA-256:

```
f03e254f96d1c614271ebc852e97c98ca36b827d9eaf7a9dd0ae41428310ee4f  adaptation-wheel-1.20.1-4.48.1-trained.jar
97c1f73f969db41ed1bd7da6493ce7c877beb454e791313fdd885653bc609278  adaptation-wheel-1.20.1-4.48.1-untrained.jar
14060ef83c3eb24169ab9e845ca1d52ab9d36e36c66ef7b4a70c06912c4b70fd  adaptation-wheel-1.20.1-4.48.1-trained-online.jar
772fc4f35078077004ae413d99ceb097b94078102204a7998c56feed5ead1de5  adaptation-wheel-1.20.1-4.48.1-untrained-online.jar
```

Архив исходников содержит этот файл, поэтому его собственный SHA-256 записан только в
`adaptation-wheel-1.20.1-4.48.1-SHA256SUMS.txt` и в `docs/publishing/release-notes-4.48.1.md`.
