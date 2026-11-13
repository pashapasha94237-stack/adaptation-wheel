# Локальный корпус модов — аудит 4.26

Дата: 25 октября 2026. Источник: `<mods>/mods`.
Категории взяты из пользовательского `Список модов.txt` как описательные данные. Инструкции из архивов или списка не исполнялись. Исходные JAR не изменялись, Java-классы модов не загружались в JVM.

## Что реально проверено

- 616 верхнеуровневых JAR, 3 147 227 187 байт. Каждый файл сопоставлен со списком пользователя и имеет SHA-256 в JSON/CSV.
- 643 объявления в metadata, после объединения повторов одного ID внутри универсального JAR — 622 разных mod ID. Дубликатов ID между разными файлами нет. 615 JAR имеют mod metadata; `kotlinforforge-4.12.0-all.jar` — библиотека/загрузчик без собственного mod ID.
- 1 855 объявлений зависимостей сохранены в JSON/CSV; это инвентаризация, а не подтверждение совместимости всей сборки. По категориям пользовательского списка: 503 контентных файла и 113 библиотек/зависимостей.
- У Croptopia отсутствует `Implementation-Version`: `${file.jarVersion}` честно оставлен неразрешённым. `4.0.1` есть в имени файла, но не выдан за прочитанную runtime-версию.
- ASM 9.7.1 прочитал 200 235 верхнеуровневых и 42 086 вложенных class entries, включая private-методы: 1 745 842 методов. Вложенных JAR entries на первом уровне — 230. Число классов учитывает повторно упакованные библиотеки, поэтому не равно числу уникальных классов.
- Для проверки наследования отдельно прочитаны только заголовки 7 110 классов локального mapped Minecraft/Forge 1.20.1; они не включены в corpus-счётчики. Ошибок чтения классов и ASM dataflow-анализа — 0.

Это **статический аудит**, а не 616 игровых проверок. Наличие sink не доказывает урон игроку, достижимость ветки, вредность состояния или возможность безусловной отмены.

## Результаты байткода

| Категория | Мест в байткоде |
|---|---:|
| `setHealth(float)` / SRG / intermediary alias | 584 |
| `SynchedEntityData.set` с доказанным аргументом `DATA_HEALTH_ID` | 4 |
| Запись float-поля, возвращаемого непосредственно health-getter | 18 |
| Другой `setHealth`, вне подтверждённой LivingEntity-иерархии | 19 |
| Поле с health-подобным именем, без структурного доказательства | 515 |
| Наложение vanilla MobEffectInstance | 3 590 |
| Изменение AttributeInstance base/modifier | 1 034 |
| Числовая запись поля в capability/state-классе (эвристика) | 4 179 |
| Вызов mutator capability/state-класса (эвристика) | 5 545 |
| NBT write в методе, обращающемся к persistent data/capability | 1 083 |
| Другой NBT write | 27 711 |
| Другой synced data write | 6 768 |
| Reflection/Unsafe write с неразрешённой целью | 1 149 |

Всего 52 199 sink-записей в 476 JAR, включая 1 705 записей из вложенных архивов. 588 setter/accessor-позиций найдены в 126 JAR. Они включают лечение, создание, загрузку, здоровье NPC и другие нормальные действия. 18 backing-field записей считаются отдельно: среди них есть сетевые пакеты, цена ритуала, сохранённые данные миньона и здоровье не-LivingEntity объектов.

Dataflow нашёл 44 выражения `getHealth() - значение` в 21 JAR. Это очередь для проверки; знак вычитаемого, адресат и смысл действия ещё требуют runtime-контекста. Среди них есть добровольная цена Blood Magic/Hex Casting и потеря здоровья предметов/созданий.

Четыре прямые записи `DATA_HEALTH_ID`: Collective `EntityFunctions.forceSetHealth` и методы загрузки `FreakagerEntity`, `RagnoEntity`, `SpiritcallerEntity` из Illage and Spillage. Общая защита должна отличать реальную атаку от загрузки сохранённого существа.

## Приоритеты относительно прежней совместимости

Существующие JSON manifests содержат 747 классов и 20 явно записанных mod ID; это не тождественно прежней игровой матрице из 24 модов. 557 из 588 setter/accessor-позиций лежат вне этих классов. Это **сравнение покрытия имён классов**, а не доказательство, что прежние общие hooks пропускают каждый такой вызов.

Полезные новые тестовые цели, найденные по фактическим инструкциям вычитания здоровья:

| Мод | Класс / метод |
|---|---|
| Hex Casting | `at/petrak/hexcasting/api/casting/mishaps/Mishap$Companion.trulyHurt` |
| Goety | `com/Polarice3/Goety/common/effects/HysteriaEffect.m_6742_` |
| Mana and Artifice | `com/mna/effects/harmful/EffectLacerate.m_6742_` |
| Enigmatic Legacy | `com/aizistral/enigmaticlegacy/effects/GrowingBloodlustEffect.m_6742_` |
| Re-Avaritia | `committee/nova/mods/avaritia/common/item/tools/infinity/InfinitySwordItem.hurt`, `InfinityHandler.onEntityHurting` |
| Goety: Revelation | `com/mega/revelationfix/util/entity/EntityActuallyHurt.actuallyHurt0` |
| Simply Swords | методы `BattleStandardEntity`, `BattleStandardDarkEntity` и оружия — 11 subtraction sites |

Capability-кандидаты включают Dragon Survival, NuclearCraft, MNA, Iron's Spellbooks, Mekanism и многие другие. Классификация по имени контейнера/интерфейсу специально обозначена как эвристика: в таких контейнерах также лежат энергия машин, опыт, расходники и полезные состояния. Нельзя автоматически возвращать все поля назад или считать их защитой от негативных эффектов.

## Машинный контракт и ограничения

`callsites.jsonl`/CSV хранят `jar`, `entry`, `modOwner`, `class`, `method`, `descriptor`, `access`, `instruction`, `line`, `sinkCategory`, `targetOwner`, `targetName`, `targetDescriptor`, `targetLiving`, `evidence`, `baselineClass`, `classSha256`.

- `modOwner` — ID верхнеуровневого контейнера, а не заявление об авторстве вложенной библиотеки; область явно записана в `modOwnerScope`.
- `instruction` — индекс ASM node при `SKIP_FRAMES` с сохранёнными debug nodes. SHA-256 класса и точный descriptor обязательны перед использованием индекса; номер строки может отсутствовать.
- `DATA_HEALTH_ID` доказан через SourceInterpreter, включая перенос accessor через локальную переменную. Обработаны обе перегрузки `SynchedEntityData.set`, включая `m_276349_` с `force`.
- Backing-field распознаётся по непосредственному `GETFIELD; FRETURN` в health-getter, а не только по имени поля. Это не доказывает, что поле принадлежит игроку.
- Capability/state-поля и mutator — эвристика по типу/интерфейсам/имени. Persistent NBT — совпадение обращений в одном методе, не доказанный alias одного и того же CompoundTag.
- Reflection/Unsafe, динамически сгенерированные классы, межпроцедурные aliases, opaque/обфусцированные контейнеры и эффект только через UI/сеть могут требовать отдельного наблюдения. Сканер не обещает полноты семантического анализа.

`transformation-candidates.json` содержит повторяемый список кандидатов и обязательные runtime-проверки. Он не исполняется как готовый патч. Безопасный генератор должен проверить SHA и подписи, установить игрока-владельца, сохранить исходный вызов и исключения, зарегистрировать реальный отрицательный результат и только затем применять уже заработанную адаптацию к этому источнику. Присутствие мода в корпусе не выдаёт этапы или иммунитет.

## Артефакты и воспроизведение

Полный внешний lab: `<lab>/autonomous-compat-1.20.1`.

- `inventory.json`, `inventory.csv`, `INVENTORY.md`, `inventory-summary.json`: имена, ID, версии, dependencies, категории и SHA каждого JAR.
- `callsites.jsonl`, `callsites.csv`, `definitions.jsonl`: точные позиции и сигнатуры; vendor-исходников/байткода нет.
- `BYTECODE-AUDIT.md`, `audit-summary.json`, `bytecode-stats.json`: полный отчёт, в том числе все 616 файлов и все 44 subtraction sites.
- `transformation-candidates.json`: вход для генератора, с явным `advisoryOnly: true`.
- `bytecode-errors.jsonl`: пустой; `scanner-selftest.txt`: PASS.

Собственные инструменты: `tools/inventory-corpus426.py` (Python 3.11+ stdlib) и `tools/CorpusBytecode426.java` (JDK17 + ASM/ASM-tree/ASM-analysis 9.7.1 из локального cache). Ничего скачивать и запускать Gradle не требуется.

```powershell
$lab = '<lab>/autonomous-compat-1.20.1'
$mods = '<mods>/mods'
$python = 'python'
& $python tools/inventory-corpus426.py --mods $mods --list '<mods>/Список модов.txt' --out $lab
$asmCp = (Get-ChildItem '.gradle-user-home/caches/modules-2/files-2.1/org.ow2.asm' -Recurse -Filter '*9.7.1.jar' | Where-Object { $_.Name -match '^asm(-tree|-analysis)?-9' } | Select-Object -ExpandProperty FullName) -join ';'
New-Item -ItemType Directory -Force -Path "$lab/classes" | Out-Null
& '.tools/java/jdk-17.0.20.1+1/bin/javac.exe' -encoding UTF-8 -cp $asmCp -d "$lab/classes" tools/CorpusBytecode426.java
& '.tools/java/jdk-17.0.20.1+1/bin/java.exe' -cp "$lab/classes;$asmCp" CorpusBytecode426 --self-test
& '.tools/java/jdk-17.0.20.1+1/bin/java.exe' -Xmx3g -cp "$lab/classes;$asmCp" CorpusBytecode426 $mods $lab '.gradle-user-home/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.0_mapped_official_1.20.1/forge-1.20.1-47.4.0_mapped_official_1.20.1.jar'
& $python tools/inventory-corpus426.py --out $lab --summarize
```

Synthetic self-test проверяет private setter, передачу health accessor через local, запись opaque backing field, ключ NBT и происхождение `getHealth()-value`. Основная сборка и игровые прогоны принадлежат отдельному workflow; сканер их не подменяет.

## Полный каталог metadata

| File | ID | Human name | Version | Category |
|---|---|---|---|---|
| [1.20.1] SecurityCraft v1.10.2.1.jar | securitycraft | SecurityCraft | 1.10.2.1 | Прочее |
| [forge]ctov-3.4.14.jar | ctov | ChoiceTheorem's Overhauled Village | 3.4.14 | Структуры / Данжи |
| ad_astra-forge-1.20.1-1.15.21.jar | ad_astra | Ad Astra | 1.15.21 | Индустрия / Техника |
| AdditionalStructures-1.20.x-(v.4.2.2).jar | additionalstructures | Additional Structures | 4.2.2 | Структуры / Данжи |
| AdvancedAE-1.3.6-1.20.1.jar | advanced_ae | Advanced AE | 1.3.6-1.20.1 | Хранение / Логистика |
| advancednetherite-forge-2.1.3-1.20.1.jar | advancednetherite | Advanced Netherite | 2.1.3 | РПГ / Приключения / Бой |
| AdvancedPeripherals-1.20.1-0.7.48r.jar | advancedperipherals | Advanced Peripherals | 0.7.48r | Индустрия / Техника |
| AdvancementPlaques-1.20.1-forge-1.6.9.jar | advancementplaques | Advancement Plaques | 1.6.9 | Интерфейс / Удобство (QoL) |
| AE2-Things-1.2.1.jar | ae2things | AE2 Things | 1.2.1 | Хранение / Логистика |
| ae2wtlib-15.3.3-forge.jar | ae2wtlib | AE2WTLib | 15.3.3-forge | Хранение / Логистика |
| aeroblender-1.20.1-1.0.1-neoforge.jar | aeroblender | AeroBlender | 1.20.1-1.0.1-neoforge | Библиотеки и зависимости (нужны другим модам) |
| aether-1.20.1-1.5.2-neoforge.jar | aether | The Aether | 1.20.1-1.5.2-neoforge | РПГ / Приключения / Бой |
| aether-redux-2.0.19-1.20.1-neoforge.jar | aether_redux | The Aether: Redux | 2.0.19 | РПГ / Приключения / Бой |
| AetherVillages-1.20.1-1.0.7-forge.jar | aether_villages | Aether Villages | 1.0.7 | Структуры / Данжи |
| alexscaves-2.0.2.jar | alexscaves | Alex's Caves | 2.0.2 | Мир / Биомы |
| alexsdelight-1.5.jar | alexsdelight | Alex's Delight | 1.5 | Еда / Фермерство |
| alexsmobs-1.22.9.jar | alexsmobs | Alex's Mobs | 1.22.9 | Мобы / Существа |
| allthemodium-1.20.1-47.1.25-2.5.8.jar | allthemodium | Allthemodium | 2.5.8 | Индустрия / Техника |
| alltheores-1.20.1-47.1.3-2.2.4.jar | alltheores | AllTheOres | 2.2.4 | Библиотеки и зависимости (нужны другим модам) |
| AmbientEnvironment-forge-1.20.1-11.0.0.1.jar | ambientenvironment | Ambient Environment | 11.0.0.1 | Реализм / Выживание |
| AmbientSounds_FORGE_v6.3.11_mc1.20.1.jar | ambientsounds | AmbientSounds | 6.3.11 | Реализм / Выживание |
| amendments-1.20-2.2.6.jar | amendments | Amendments | 1.20-2.2.6 | Декор / Строительство |
| Amplified_Nether_26.2_v1.2.15.jar | amplified_nether | Amplified Nether | 1.2.15 | Мир / Биомы |
| animal_feeding_trough-1.1.0+1.20.1-forge.jar | animal_feeding_trough | Animal Feeding Trough | 1.1.0+1.20.1-forge | Еда / Фермерство |
| another_furniture-forge-1.20.1-3.0.4.jar | another_furniture | Another Furniture | 1.20.1-3.0.4 | Декор / Строительство |
| Apotheosis-1.20.1-7.4.8.jar | apotheosis | Apotheosis | 7.4.8 | РПГ / Приключения / Бой |
| ApothicAttributes-1.20.1-1.3.7.jar | attributeslib | Apothic Attributes | 1.3.7 | Библиотеки и зависимости (нужны другим модам) |
| appleskin-forge-mc1.20.1-2.5.1.jar | appleskin | AppleSkin | 2.5.1+mc1.20.1 | Интерфейс / Удобство (QoL) |
| applied-botanics-forge-1.5.2.jar | appbot | Applied Botanics | 1.5.2 | Хранение / Логистика |
| Applied-Mekanistics-1.4.3.jar | appmek | Applied Mekanistics | 1.4.3 | Хранение / Логистика |
| appliedenergistics2-forge-15.4.11.jar | ae2 | Applied Energistics 2 | 15.4.11 | Хранение / Логистика |
| AppliedFlux-1.20-1.3.7-forge.jar | appflux | AppliedFlux | 1.20-1.3.7-forge | Хранение / Логистика |
| Aquaculture-1.20.1-2.5.7.jar | aquaculture | Aquaculture 2 | 2.5.7 | Еда / Фермерство |
| aquamirae-forge-1.20.1-6.4.0.jar | aquamirae | Aquamirae | 6.4.0 | РПГ / Приключения / Бой |
| archers-forge-3.1.4+1.20.1.jar | archers | Archers (RPG Series) | 3.1.4 | РПГ / Приключения / Бой |
| architectury-9.2.14-forge.jar | architectury | Architectury | 9.2.14 | Библиотеки и зависимости (нужны другим модам) |
| armor_model_api-forge-1.0.0+1.20.1.jar | armor_model_api | Armor Model API | 1.0.0+1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| armourersworkshop-forge-1.20.1-2.1.4.jar | armourers_workshop | Armourer's Workshop | 2.1.4 | Декор / Строительство |
| ars_additions-1.20.1-1.6.7.jar | ars_additions | Ars Additions | 1.20.1-1.6.7 | Фэнтези / Магия |
| ars_creo-1.20.1-4.3.0.jar | ars_creo | Ars Creo | 4.3.0 | Create и аддоны |
| ars_elemental-1.20.1-0.6.8.0.jar | ars_elemental | Ars Elemental | 0.6.8.0 | Фэнтези / Магия |
| ars_nouveau-1.20.1-4.12.7-all.jar | ars_nouveau | Ars Nouveau | 4.12.7 | Фэнтези / Магия |
| arsdelight-1.2.2.jar | arsdelight | Ars Nouveau's Flavors & Delight | 1.2.2 | Еда / Фермерство |
| artifacts-forge-9.5.19.jar | artifacts | Artifacts | 9.5.19 | РПГ / Приключения / Бой |
| astikorcartsredux-1.2.5.jar | astikorcartsredux | AstikorCarts Redux | 1.2.5 | Транспорт |
| athena-forge-1.20.1-3.1.2.jar | athena | Athena | 3.1.2 | Библиотеки и зависимости (нужны другим модам) |
| atmospheric-1.20.1-6.1.1.jar | atmospheric | Atmospheric | 6.1.1 | Мир / Биомы |
| AttributeFix-Forge-1.20.1-21.0.5.jar | attributefix | AttributeFix | 21.0.5 | Библиотеки и зависимости (нужны другим модам) |
| autumnity-1.20.1-5.0.2.jar | autumnity | Autumnity | 5.0.2 | Мир / Биомы |
| azurelib-neo-1.20.1-3.2.0.jar | azurelib | AzureLib | 3.2.0 | Библиотеки и зависимости (нужны другим модам) |
| balm-forge-1.20.1-7.3.44.jar | balm | Balm | 7.3.44 | Библиотеки и зависимости (нужны другим модам) |
| BarteringStation-v8.0.0-1.20.1-Forge.jar | barteringstation | Bartering Station | 8.0.0 | Интерфейс / Удобство (QoL) |
| bbb-1.20.1-forge-1.1.1.jar | bbb | Building But Better | 1.20.1-1.1.1 | Декор / Строительство |
| bellsandwhistles-0.4.5-1.20.x-Create6.0+.jar | bellsandwhistles | Create: Bells & Whistles | 0.4.3-1.20.x | Create и аддоны |
| BetterAdvancements-Forge-1.20.1-0.6.0.73.jar | betteradvancements | Better Advancements | 0.6.0.73 | Интерфейс / Удобство (QoL) |
| BetterAnimationsCollection-v8.0.1-1.20.1-Forge.jar | betteranimationscollection | Better Animations Collection | 8.0.1 | Реализм / Выживание |
| betterarcheology-1.2.1-1.20.1.jar | betterarcheology | Better Archeology | 1.2.1-1.20.1 | Структуры / Данжи |
| bettercombat-forge-1.9.0+1.20.1.jar | bettercombat | Better Combat | 1.9.0+1.20.1 | РПГ / Приключения / Бой |
| BetterF3-7.0.2-Forge-1.20.1.jar | betterf3 | BetterF3 | 7.0.2 | Интерфейс / Удобство (QoL) |
| bettervillage-forge-1.20.1-3.3.1-all.jar | bettervillage | Better village | 3.3.1 | Структуры / Данжи |
| biomancy-forge-1.20.1-2.9.0.1-alpha.0.jar | biomancy | Biomancy | 2.9.0.1-alpha.0 | Фэнтези / Магия |
| BiomesOPlenty-1.20.1-18.0.0.592.jar | biomesoplenty | Biomes O' Plenty | 18.0.0.592 | Мир / Биомы |
| bits_n_bobs-0.0.41.jar | bits_n_bobs | Create Bits 'n' Bobs | 0.0.41 | Create и аддоны |
| blockui-1.20.1-1.0.193.jar | blockui | UI Library Mod | 1.20.1-1.0.193 | Библиотеки и зависимости (нужны другим модам) |
| BloodMagic-1.18-1.20.1-3.3.8-50.jar | bloodmagic | Blood Magic | 3.3.8-50 | Фэнтези / Магия |
| blue_skies-1.20.1-1.3.31.jar | blue_skies | Blue Skies | 1.3.31 | РПГ / Приключения / Бой |
| blueprint-1.20.1-7.1.5.jar | blueprint | Blueprint | 7.1.5 | Библиотеки и зависимости (нужны другим модам) |
| boatload-1.20.1-5.0.1.jar | boatload | Boatload | 5.0.1 | Транспорт |
| BOMD-Forge-1.20.1-1.1.2.jar | bosses_of_mass_destruction | Bosses of Mass Destruction | 1.1.2 | РПГ / Приключения / Бой |
| Bookshelf-Forge-1.20.1-20.2.15.jar | bookshelf | Bookshelf | 20.2.15 | Библиотеки и зависимости (нужны другим модам) |
| born_in_chaos_[Forge]1.20.1_1.7.5.jar | born_in_chaos_v1 | Born in Chaos  | 1.7.5 | Мобы / Существа |
| Botania-1.20.1-456-FORGE.jar | botania | Botania | 1.20.1-456-FORGE | Фэнтези / Магия |
| BotanyPots-Forge-1.20.1-13.0.43.jar | botanypots | BotanyPots | 13.0.43 | Еда / Фермерство |
| botarium-forge-1.20.1-2.3.4.jar | botarium | Botarium | 2.3.4 | Библиотеки и зависимости (нужны другим модам) |
| Bountiful-6.0.4+1.20.1-forge.jar | bountiful | Bountiful | 6.0.4+1.20.1 | РПГ / Приключения / Бой |
| BrandonsCore-1.20.1-3.2.1.302-universal.jar | brandonscore | Brandon's Core | 3.2.1.302 | Библиотеки и зависимости (нужны другим модам) |
| BrewinAndChewin-1.20.1-3.2.1.jar | brewinandchewin | Brewin' And Chewin' | 1.20.1-3.2.1 | Еда / Фермерство |
| buildinggadgets2-1.0.8.jar | buildinggadgets2 | Building Gadgets 2 | 1.0.8 | Декор / Строительство |
| buzzier_bees-1.20.1-6.0.1.jar | buzzier_bees | Buzzier Bees | 6.0.1 | Еда / Фермерство |
| bygonenether-1.3.2-1.20.x.jar | bygonenether | Bygone Nether | 1.3.2 | Структуры / Данжи |
| cabletiers-1.20.1-1.2.2.jar | cabletiers | Cable Tiers | 1.20.1-1.2.2 | Хранение / Логистика |
| caelus-forge-3.2.0+1.20.1.jar | caelus | Caelus API | 3.2.0+1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| camping-forge-1.20.1-2.0.0.jar | camping | Camping | 2.0.0 | Декор / Строительство |
| car-forge-1.20.1-1.0.45.jar | car | Ultimate Car Mod | 1.20.1-1.0.45 | Транспорт |
| carryon-forge-1.20.1-2.1.2.7.jar | carryon | Carry On | 2.1.2.7 | Интерфейс / Удобство (QoL) |
| caverns_and_chasms-1.20.1-2.1.3.jar | caverns_and_chasms | Caverns & Chasms | 2.1.3 | Мир / Биомы |
| cc-tweaked-1.20.1-forge-1.120.2.jar | computercraft | CC: Tweaked | 1.120.2 | Индустрия / Техника |
| Ceramics-1.20.1-1.8.0.jar | ceramics | Ceramics | 1.8.0 | Декор / Строительство |
| CerbonsAPI-Forge-1.20.1-1.1.0.jar | cerbons_api | Cerbons API | 1.1.0 | Библиотеки и зависимости (нужны другим модам) |
| charmofundying-forge-6.5.0+1.20.1.jar | charmofundying | Charm of Undying | 6.5.0+1.20.1 | Интерфейс / Удобство (QoL) |
| chat_heads-0.15.7-forge-1.20.jar | chat_heads | Chat Heads | 0.15.7 | Интерфейс / Удобство (QoL) |
| chefs-delight-1.0.5-forge-1.20.1.jar | chefsdelight | Chefs Delight | 1.0.5-forge-1.20.1 | Еда / Фермерство |
| chipped-forge-1.20.1-3.0.7.jar | chipped | Chipped | 3.0.7 | Декор / Строительство |
| chisels-and-bits-forge-20.1.20.jar | chiselsandbits | chisels-and-bits | 20.1.20 | Декор / Строительство |
| citadel-2.6.3-1.20.1.jar | citadel | Citadel | 2.6.3 | Библиотеки и зависимости (нужны другим модам) |
| clayworks-1.20.1-3.0.4.jar | clayworks | Clayworks | 3.0.4 | Декор / Строительство |
| clickadv-1.20.1-3.9.jar | clickadv | clickadv mod | 1.20.1-3.9 | Интерфейс / Удобство (QoL) |
| clienttweaks-forge-1.20.1-11.1.11.jar | clienttweaks | Client Tweaks | 11.1.11 | Интерфейс / Удобство (QoL) |
| ClimateRivers-v20.1.0-1.20.1-Forge.jar | climaterivers | Climate Rivers | 20.1.0 | Мир / Биомы |
| cloth-config-11.1.136-forge.jar | cloth_config | Cloth Config v10 API | 11.1.136 | Библиотеки и зависимости (нужны другим модам) |
| Cobblemon-forge-1.5.2+1.20.1.jar | cobblemon | Cobblemon | 1.5.2+1.20.1 | Мобы / Существа |
| CodeChickenLib-1.20.1-4.4.0.528-universal.jar | codechickenlib | CodeChicken Lib | 4.4.0.528 | Библиотеки и зависимости (нужны другим модам) |
| cofh_core-1.20.1-11.0.2.56.jar | cofh_core | CoFH Core | 11.0.2 | Библиотеки и зависимости (нужны другим модам) |
| collective-1.20.1-8.65.jar | collective | Collective | 8.65 | Библиотеки и зависимости (нужны другим модам) |
| colorfulhearts-forge-1.20.1-4.3.16.jar | colorfulhearts | Colorful Hearts | 4.3.16 | Интерфейс / Удобство (QoL) |
| ColossalChests-1.20.1-1.8.7.jar | colossalchests | ColossalChests | 1.8.7 | Хранение / Логистика |
| combatroll-forge-1.3.3+1.20.1.jar | combatroll | Combat Roll | 1.3.3+1.20.1 | РПГ / Приключения / Бой |
| comforts-forge-6.4.0+1.20.1.jar | comforts | Comforts | 6.4.0+1.20.1 | Прочее |
| common-networking-forge-1.0.6-1.20.1.jar | commonnetworking | Common Networking | 1.0.6-1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| CommonCapabilities-1.20.1-2.9.11.jar | commoncapabilities | CommonCapabilities | 2.9.11 | Библиотеки и зависимости (нужны другим модам) |
| compactmachines-forge-6.0.3.jar | compactmachines | Compact Machines | 6.0.3 | Индустрия / Техника |
| connectedglass-1.1.14-forge-mc1.20.1.jar | connectedglass | Connected Glass | 1.1.14 | Декор / Строительство |
| constructionwand-1.20.1-2.11.jar | constructionwand | Construction Wand | 1.20.1-2.11 | Декор / Строительство |
| Controlling-forge-1.20.1-12.0.2.jar | controlling | Controlling | 12.0.2 | Интерфейс / Удобство (QoL) |
| cookingforblockheads-forge-1.20.1-16.0.15.jar | cookingforblockheads | CookingForBlockheads | 16.0.15 | Еда / Фермерство |
| copycats-3.0.10+mc.1.20.1-forge.jar | copycats | Create: Copycats+ | 3.0.10+mc.1.20.1-forge | Create и аддоны |
| Corgilib-Forge-1.20.1-4.0.3.6.jar | corgilib | CorgiLib | 4.0.3.6 | Библиотеки и зависимости (нужны другим модам) |
| coroutil-forge-1.20.1-1.3.7.jar | coroutil | CoroUtil | 1.20.1-1.3.7 | Библиотеки и зависимости (нужны другим модам) |
| corpse-forge-1.20.1-1.0.23.jar | corpse | Corpse | 1.20.1-1.0.23 | Интерфейс / Удобство (QoL) |
| cosmeticarmorreworked-1.20.1-v1a.jar | cosmeticarmorreworked | CosmeticArmorReworked | 1.20.1-v1a | Прочее |
| craftingtweaks-forge-1.20.1-18.2.9.jar | craftingtweaks | CraftingTweaks | 18.2.9 | Интерфейс / Удобство (QoL) |
| Create Encased-1.20.1-1.8-ht2.jar | createcasing | Create Encased | 1.8-ht2 | Create и аддоны |
| create-1.20.1-6.0.8.jar | create | Create | 6.0.8 | Create и аддоны |
| create-confectionery1.20.1_v1.1.3.jar | create_confectionery | Create Confectionery | 1.1.3 | Create и аддоны |
| create-new-age-1.2.0+forge-mc1.20.1.jar | create_new_age | Create: New Age | 1.2.0+mc1.20.1 | Create и аддоны |
| create-stuff-additions1.20.1_v2.1.2.jar | create_sa | Create Stuff 'N Additions | 2.1.2 | Create и аддоны |
| create_connected-1.2.3-mc1.20.1-all.jar | create_connected | Create: Connected | 1.2.3-mc1.20.1 | Create и аддоны |
| create_enchantment_industry-1.4.1-for-create-6.0.8.jar | create_enchantment_industry | Create Enchantment Industry | 1.4.1-for-create-6.0.8 | Create и аддоны |
| create_jetpack-forge-4.4.6.jar | create_jetpack | Create Jetpack | 4.4.6 | Create и аддоны |
| create_power_loader-2.0.3-mc1.20.1.jar | create_power_loader | Create: Power Loader | 2.0.3-mc1.20.1 | Create и аддоны |
| create_radar-0.4.6+mc1.20.1.jar | create_radar | Create: Radars | 0.4.5 | Create и аддоны |
| create_tweaked_controllers-1.20.1-1.2.7.jar | create_tweaked_controllers | Create: Tweaked Controllers | 1.20.1-1.2.7 | Create и аддоны |
| createaddition-1.20.1-1.3.3.jar | createaddition | Create Crafts & Additions | 1.20.1-1.3.3 | Create и аддоны |
| createbigcannons-5.11.4-mc.1.20.1-forge.jar | createbigcannons | Create Big Cannons | 5.11.4 | Create и аддоны |
| createcontraptionterminals-1.20-1.2.0.jar | createcontraptionterminals | Create Contraption Terminals | 1.2.0 | Create и аддоны |
| createdeco-2.0.3-1.20.1-forge.jar | createdeco | Create Deco | 2.0.3-1.20.1-forge | Create и аддоны |
| createdieselgenerators-1.20.1-1.3.12.jar | createdieselgenerators | Create: Diesel Generators | 1.20.1-1.3.12 | Create и аддоны |
| creategoggles-1.20.1-6.1.1-[FORGE].jar | creategoggles | Create Goggles | 6.1.1 | Create и аддоны |
| CreateNumismatics-1.1.0+forge-mc1.20.1.jar | numismatics | Create: Numismatics | 1.1.0+forge-mc1.20.1 | Create и аддоны |
| createoreexcavation-1.20-1.6.6.jar | createoreexcavation | Create Ore Excavation | 1.6.6 | Create и аддоны |
| createutilities-0.3.2+1.20.1.jar | createutilities | Create Utilities | 0.3.2+1.20.1 | Create и аддоны |
| CreativeCore_FORGE_v2.12.40_mc1.20.1.jar | creativecore | CreativeCore | 2.12.40 | Библиотеки и зависимости (нужны другим модам) |
| creeperoverhaul-3.0.2-forge.jar | creeperoverhaul | Creeper Overhaul | 3.0.2 | Мобы / Существа |
| cristellib-1.1.6-forge.jar | cristellib | Cristel Lib | 1.1.6 | Библиотеки и зависимости (нужны другим модам) |
| crittersandcompanions-forge-1.20.1-2.7.1.jar | crittersandcompanions | Critters and Companions | 2.7.1 | Мобы / Существа |
| Croptopia-1.20.1-FORGE-4.0.1.jar | croptopia | Croptopia | ${file.jarVersion} | Еда / Фермерство |
| Cucumber-1.20.1-7.0.16.jar | cucumber | Cucumber Library | 7.0.16 | Библиотеки и зависимости (нужны другим модам) |
| cupboard-1.20.1-4.2.jar | cupboard | Cupboard utilities | 1.20.1-4.2 | Библиотеки и зависимости (нужны другим модам) |
| curios-forge-5.14.1+1.20.1.jar | curios | Curios API | 5.14.1+1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| Cyclic-1.20.1-1.13.7.jar | cyclic | Cyclic | 1.13.7 | Прочее |
| CyclopsCore-1.20.1-1.22.3.jar | cyclopscore | Cyclops Core | 1.22.3 | Библиотеки и зависимости (нужны другим модам) |
| damagenumbers-1.4.0-forge.jar | damagenumbers | Damage Numbers | 1.4.0-forge | Интерфейс / Удобство (QoL) |
| dankstorage-forge-1.20.1-16.jar | dankstorage | Dank Storage | 16 | Хранение / Логистика |
| DarkPaintings-Forge-1.20.1-17.0.6.jar | darkpaintings | DarkPaintings | 17.0.6 | Декор / Строительство |
| DarkUtilities-Forge-1.20.1-17.0.6.jar | darkutils | DarkUtilities | 17.0.6 | Прочее |
| Data_Anchor-forge-1.20.1-1.0.0.23.jar | dataanchor | Data Anchor | 1.0.0.23 | Библиотеки и зависимости (нужны другим модам) |
| decocraft-3.0.4-1.20.1-slim.jar | decocraft | Decocraft | 3.0.4-1.20.1 | Декор / Строительство |
| decorative_blocks-forge-1.20.1-4.1.3.jar | decorative_blocks | Decorative Blocks | 4.1.3 | Декор / Строительство |
| deep_aether-1.20.1-1.1.7.jar | deep_aether | Deep Aether | 1.20.1-1.1.7 | Мир / Биомы |
| deeperdarker-forge-1.20.1-1.3.3.jar | deeperdarker | Deeper and Darker | 1.3.3 | Мир / Биомы |
| Delightful-1.20.1-3.8.1.jar | delightful | Delightful | 3.8.1 | Еда / Фермерство |
| delightlib-26.05.18-1.20-forge.jar | delightlib | Delight Lib | 26.05.18-1.20-forge | Библиотеки и зависимости (нужны другим модам) |
| DiagonalFences-v8.1.5-1.20.1-Forge.jar | diagonalfences | Diagonal Fences | 8.1.5 | Декор / Строительство |
| diet-forge-2.1.1+1.20.1.jar | diet | Diet | 2.1.1+1.20.1 | Реализм / Выживание |
| Ding-1.20.1-Forge-1.5.0.jar | ding | Ding | 1.5.0 | Интерфейс / Удобство (QoL) |
| dldungeonsjbg-2.2.1-MC1.20.1.jar | dldungeonsjbg | Doomlike Dungeons | 2.2.1-MC1.20.1 | Структуры / Данжи |
| domesticationinnovation-1.7.1-1.20.1.jar | domesticationinnovation | Domestication Innovation | 1.7.1 | Мобы / Существа |
| domum_ornamentum-1.20.1-1.0.296-universal.jar | domum_ornamentum | Domum Ornamentum | 1.20.1-1.0.296 | Декор / Строительство |
| doubledoors-1.20.1-7.5.jar | doubledoors | Double Doors | 7.5 | Интерфейс / Удобство (QoL) |
| Draconic-Evolution-1.20.1-3.1.2.621-universal.jar | draconicevolution | Draconic Evolution | 3.1.2.621 | Индустрия / Техника |
| dragonmounts-1.20.1-1.2.3-beta.jar | dragonmounts | Dragon Mounts: Legacy | 1.2.3-beta | Мобы / Существа |
| dragonsurvival-1.20.1-23.07.2026-all.jar | dragonsurvival | Dragon Survival | 1.5.70 | РПГ / Приключения / Бой |
| DramaticDoors-QuiFabrge-1.20.1-3.3.3.jar | dramaticdoors | Dramatic Doors | 1.20.1-3.3.3 | Декор / Строительство |
| dummmmmmy-1.20-2.0.12-forge.jar | dummmmmmy | MmmMmmMmmMmm | 1.20-2.0.11 | Прочее |
| Dungeon Crawl-1.20.1-2.3.15.jar | dungeoncrawl | Dungeon Crawl | 2.3.15 | Структуры / Данжи |
| dungeons-and-taverns-3.0.3.f.jar | mr_dungeons_andtaverns | Dungeons and Taverns | 3.0.3.f | Структуры / Данжи |
| dungeons-and-taverns-ancient-city-overhaul-1.jar | mr_dungeons_andtavernsancientcityoverhaul | Dungeons and Taverns Ancient City Overhaul | 1 | Структуры / Данжи |
| dungeons-and-taverns-pillager-outpost-rework-1.1.jar | mr_dungeons_andtavernspillageroutpostrework | Dungeons and Taverns Pillager Outpost Rework | 1.1 | Структуры / Данжи |
| dungeons_enhanced-1.20.1-5.4.3.jar | dungeons_enhanced | Dungeons Enhanced | 5.4.3 | Структуры / Данжи |
| DungeonsArise-1.20.x-2.1.58-release.jar | dungeons_arise | When Dungeons Arise | 2.1.58-1.20.x | Структуры / Данжи |
| DungeonsAriseSevenSeas-1.19.2-1.0.2-forge.jar | dungeons_arise_seven_seas | When Dungeons Arise: Seven Seas | 1.0.2 | Структуры / Данжи |
| durabilitytooltip-1.2.0-forge-mc1.20.jar | durabilitytooltip | Durability Tooltip | 1.2.0 | Интерфейс / Удобство (QoL) |
| dynamiclights-v1.9-mc1.17-1.21.9-mod.jar | dynamiclights | Dynamic Lights | 1.9+mod | Реализм / Выживание |
| easy-villagers-forge-1.20.1-1.1.23.jar | easy_villagers | Easy Villagers | 1.20.1-1.1.23 | Прочее |
| EasyAnvils-v8.0.2-1.20.1-Forge.jar | easyanvils | Easy Anvils | 8.0.2 | Интерфейс / Удобство (QoL) |
| EasyMagic-v8.0.1-1.20.1-Forge.jar | easymagic | Easy Magic | 8.0.1 | Интерфейс / Удобство (QoL) |
| EasyShulkerBoxes-v8.0.2-1.20.1-Forge.jar | easyshulkerboxes | Easy Shulker Boxes | 8.0.2 | Хранение / Логистика |
| ecologics-forge-1.20.1-2.2.7.jar | ecologics | Ecologics | 2.2.7 | Мир / Биомы |
| EdivadLib-1.20.1-2.0.1.jar | edivadlib | EdivadLib | 2.0.1 | Библиотеки и зависимости (нужны другим модам) |
| eeeabsmobs-1.20.1-0.98.1.jar | eeeabsmobs | EEEAB's Mobs | 1.20.1-0.98.1 | Мобы / Существа |
| effortlessbuilding-1.20.1-3.11.jar | effortlessbuilding | Effortless Building | 3.11 | Декор / Строительство |
| eidolon_repraised-1.20.1-0.3.13.jar | eidolon | Eidolon:Repraised | 0.3.13 | Фэнтези / Магия |
| elevatorid-1.20.1-1.9.1-forge.jar | elevatorid | Elevator Mod | 1.20.1-1.9.1-forge | Транспорт |
| ElysiumAPI-1.20.1-1.1.3.jar | elysium_api | Elysium | 1.1.3 | Библиотеки и зависимости (нужны другим модам) |
| elytraslot-forge-6.4.4+1.20.1.jar | elytraslot | Elytra Slot | 6.4.4+1.20.1 | Интерфейс / Удобство (QoL) |
| embersrekindled-1.20.1-1.4.7.jar | embers | Embers Rekindled | 1.20.1-1.4.7 | Фэнтези / Магия |
| emotecraft-for-MC1.20.1-2.2.7-b.build.50-forge.jar | emotecraft | Emotecraft | 2.2.7-b.build.50 | Прочее |
| EnchantingInfuser-v8.0.3-1.20.1-Forge.jar | enchantinginfuser | Enchanting Infuser | 8.0.3 | Интерфейс / Удобство (QoL) |
| EnchantmentDescriptions-Forge-1.20.1-17.1.21.jar | enchdesc | EnchantmentDescriptions | 17.1.21 | Интерфейс / Удобство (QoL) |
| endergetic-1.20.1-5.0.1.jar | endergetic | The Endergetic Expansion | 5.0.1 | Мир / Биомы |
| EnderIO-1.20.1-6.2.18-beta-all.jar | enderio | Ender IO | 6.2.18-beta | Индустрия / Техника |
| endermanoverhaul-forge-1.20.1-1.0.4.jar | endermanoverhaul | Enderman Overhaul | 1.0.4 | Мобы / Существа |
| endersdelight-1.1.4.jar | endersdelight | Ender's Delight | 1.1.4 | Еда / Фермерство |
| EnderStorage-1.20.1-2.11.0.188-universal.jar | enderstorage | EnderStorage | 2.11.0.188 | Хранение / Логистика |
| EndingLibrary-1.20.1-2.2.2-all.jar | ending_library | EndingLibrary | 2.2.2 | Библиотеки и зависимости (нужны другим модам) |
| endrem_forge-5.3.3-R-1.20.1.jar | endrem | End Remastered | 5.3.3-R-1.20.1 | РПГ / Приключения / Бой |
| ends_delight-2.6.1+forge.1.20.1.jar | ends_delight | End's Delight | 2.6.1+forge.1.20.1 | Еда / Фермерство |
| Enhanced-Celestials-forge-1.20.1-5.0.3.3.jar | enhancedcelestials | Enhanced Celestials | 1.20.1-5.0.3.3 | РПГ / Приключения / Бой |
| EnhancedVisuals_FORGE_v1.8.34_mc1.20.1.jar | enhancedvisuals | EnhancedVisuals | 1.8.34 | Реализм / Выживание |
| EnigmaticLegacy-2.30.1.jar | enigmaticlegacy | Enigmatic Legacy | 2.30.1 | Фэнтези / Магия |
| entangled-1.3.21-forge-mc1.20.4.jar | entangled | Entangled | 1.3.21 | Хранение / Логистика |
| entity_model_features-3.3.12-1.20.1-forge.jar | entity_model_features | Entity Model Features | 3.3.12 | Прочее |
| entity_texture_features-7.2.6-1.20.1-forge.jar | entity_texture_features | Entity Texture Features | 7.2.6 | Прочее |
| environmental-1.20.1-4.1.2.jar | environmental | Environmental | 4.1.2 | Мир / Биомы |
| EpheroLib-1.20.1-FORGE-1.2.0.jar | epherolib | BOZOID | 0.1.2 | Библиотеки и зависимости (нужны другим модам) |
| epic-knights-1.20.1-forge-10.15.jar | magistuarmory | Epic-Knights: Shields, Armor and Weapons | 10.15 | РПГ / Приключения / Бой |
| EquipmentCompare-1.20.1-forge-1.3.7.jar | equipmentcompare | Equipment Compare | 1.3.7 | Интерфейс / Удобство (QoL) |
| Etcetera-1.20.1-1.1.3-Forge.jar | etcetera | Etcetera | 1.20.1-1.1.3 | Декор / Строительство |
| EvilCraft-1.20.1-1.3.4.jar | evilcraft | EvilCraft | 1.3.4 | Фэнтези / Магия |
| EvilCraft-1.20.1-1.3.4.jar | evilcraftcompat | EvilCraft-Compat | 1.3.4 | Фэнтези / Магия |
| expandability-9.0.4.jar | expandability | ExpandAbility | 9.0.4 | Библиотеки и зависимости (нужны другим модам) |
| explorations-forge-1.20.1-1.6.5.jar | explorations | Explorations+ | 1.20.1-1.6.5 | Структуры / Данжи |
| ExplorersCompass-1.20.1-1.4.0-forge.jar | explorerscompass | Explorer's Compass | 1.20.1-1.4.0-forge | Интерфейс / Удобство (QoL) |
| Explorify v1.6.5.mod.jar | explorify | Explorify | 1.6.5 | Структуры / Данжи |
| exposure-1.20.1-1.7.16-forge.jar | exposure | Exposure | 1.7.16 | Прочее |
| ExtendedAE-1.20-1.4.18-forge.jar | expatternprovider | ExtendedAE | 1.20-1.4.18-forge | Хранение / Логистика |
| ExtendedCrafting-1.20.1-6.0.10.jar | extendedcrafting | Extended Crafting | 6.0.10 | Индустрия / Техника |
| ExtraDisks-1.20.1-3.0.3.jar | extradisks | Extra Disks | 1.20.1-3.0.3 | Хранение / Логистика |
| ExtraStorage-1.20.1-4.0.7.jar | extrastorage | ExtraStorage | 4.0.7 | Хранение / Логистика |
| ExtremeReactors2-1.20.1-2.0.97.jar | bigreactors | Extreme Reactors | 1.20.1-2.0.97 | Индустрия / Техника |
| ExtremeSoundMuffler-3.51-forge-1.20.1.jar | extremesoundmuffler | Extreme Sound Muffler | 3.51 | Интерфейс / Удобство (QoL) |
| fallingleaves-1.20.1-2.1.2.jar | fallingleaves | Fallingleaves | 2.1.2 | Реализм / Выживание |
| FallingTree-1.20.1-4.3.4.jar | fallingtree | FallingTree | 4.3.4 | Интерфейс / Удобство (QoL) |
| FarmersDelight-1.20.1-1.3.4.jar | farmersdelight | Farmer's Delight | 1.20.1-1.3.4 | Еда / Фермерство |
| farmersrespite-1.20.1-2.1.2.jar | farmersrespite | Farmer's Respite | 1.20.1-2.1 | Еда / Фермерство |
| farmingforblockheads-forge-1.20.1-14.0.4.jar | farmingforblockheads | Farming for Blockheads | 14.0.4 | Еда / Фермерство |
| FastLeafDecay-32.jar | fastleafdecay | Fast Leaf Decay | 32 | Интерфейс / Удобство (QoL) |
| findme-3.2.1-forge.jar | findme | FindMe | 3.2.1 | Интерфейс / Удобство (QoL) |
| firstperson-forge-2.7.3-mc1.20.1.jar | firstperson | FirstPerson | 2.7.3 | Интерфейс / Удобство (QoL) |
| flib-1.20.1-0.0.16.jar | flib | flib | 0.0.16 | Библиотеки и зависимости (нужны другим модам) |
| FluxNetworks-1.20.1-7.2.1.15.jar | fluxnetworks | Flux Networks | 7.2.1.15 | Индустрия / Техника |
| forbidden_arcanus-1.20.1-2.2.6.jar | forbidden_arcanus | Forbidden & Arcanus | 1.20.1-2.2.6 | Фэнтези / Магия |
| forgivingvoid-forge-1.20.1-10.0.3.jar | forgivingvoid | Forgiving Void | 10.0.3 | Интерфейс / Удобство (QoL) |
| formations-1.0.4-forge-mc1.20.2.jar | formations | Formations | 1.0.4 | Библиотеки и зависимости (нужны другим модам) |
| formationsnether-1.0.5.jar | formationsnether | Formations Nether | 1.0.5 | Структуры / Данжи |
| formationsoverworld-1.0.5-mc1.20.jar | formationsoverworld | Formations Overworld | 1.0.5 | Структуры / Данжи |
| fossil-forge-1.20.1-9.3.4.0.jar | fossil | Fossils and Archeology: Revival | 9.3.4.0 | Мобы / Существа |
| FramedBlocks-9.4.3.jar | framedblocks | FramedBlocks | 9.4.3 | Декор / Строительство |
| framework-forge-1.20.1-0.8.0.jar | framework | Framework | 0.8.0 | Библиотеки и зависимости (нужны другим модам) |
| friendsandfoes-forge-5.0.3+mc1.20.1.jar | friendsandfoes | Friends&Foes | 5.0.3 | Мобы / Существа |
| From-The-Fog-1.20-v1.9.2-Forge-Fabric.jar | watching | From The Fog | 1.9.2 | Мобы / Существа |
| ftb-library-forge-2001.2.13.jar | ftblibrary | FTB Library | 2001.2.13 | Библиотеки и зависимости (нужны другим модам) |
| ftb-ultimine-forge-2001.1.8.jar | ftbultimine | FTB Ultimine | 2001.1.8 | Интерфейс / Удобство (QoL) |
| fusion-1.3.16-forge-mc1.20.1.jar | fusion | Fusion | 1.3.16 | Библиотеки и зависимости (нужны другим модам) |
| fzzy_config-0.7.7+1.20.1+forge.jar | fzzy_config | Fzzy Config | 0.7.7+1.20.1+forge | Библиотеки и зависимости (нужны другим модам) |
| Galosphere-1.20.1-1.5.2-Forge.jar | galosphere | Galosphere | 1.5.2 | Мир / Биомы |
| garnished-2.1.7.c+1.20.1-neoforged.jar | garnished | Create: Garnished | 2.1.7.c | Create и аддоны |
| GatewaysToEternity-1.20.1-4.2.6.jar | gateways | Gateways To Eternity | 4.2.6 | РПГ / Приключения / Бой |
| geckolib-forge-1.20.1-4.8.4.jar | geckolib | GeckoLib 4 | 4.8.4 | Библиотеки и зависимости (нужны другим модам) |
| Geophilic v3.7.mod.jar | geophilic | Geophilic | 3.7 | Мир / Биомы |
| GlitchCore-forge-1.20.1-0.0.1.1.jar | glitchcore | GlitchCore | 0.0.1.1 | Библиотеки и зависимости (нужны другим модам) |
| Glodium-1.20-1.5-forge.jar | glodium | Glodium | 1.20-1.5-forge | Библиотеки и зависимости (нужны другим модам) |
| goblintraders-forge-1.20.1-1.11.5.jar | goblintraders | Goblin Traders | 1.11.5 | Мобы / Существа |
| goety-2.5.59.2.jar | goety | Goety | 2.5.59.2 | Фэнтези / Магия |
| GoetyRevelation-2.3.5.jar | goety_revelation | Goety: Revelation | 2.3.5 | Фэнтези / Магия |
| graveyard-FORGE-4.0.0+mc1.20.1.jar | graveyard | The Graveyard | 4.0.0+mc1.20.1 | Структуры / Данжи |
| guardvillagers-1.20.1-1.6.19.jar | guardvillagers | Guard Villagers | 1.20.1-1.6.19 | Мобы / Существа |
| guideme-20.1.15.jar | guideme | GuideME | 20.1.15 | Библиотеки и зависимости (нужны другим модам) |
| handcrafted-forge-1.20.1-3.0.6.jar | handcrafted | Handcrafted | 3.0.6 | Декор / Строительство |
| hapi-forge-1.20.1-1.1.3.jar | hapi | Hybrid API | 1.1.3 | Библиотеки и зависимости (нужны другим модам) |
| hardcorerevival-forge-1.20.1-20.1.19.jar | hardcorerevival | Hardcore Revival | 20.1.19 | Интерфейс / Удобство (QoL) |
| hearth_and_home-forge-1.20.1-2.0.3.jar | hearth_and_home | Hearth & Home | 1.20.1-2.0.3 | Декор / Строительство |
| hexcasting-forge-1.20.1-0.11.4.jar | hexcasting | Hex Casting | 0.11.4 | Фэнтези / Магия |
| hexerei-0.4.2.3.jar | hexerei | Hexerei | 1.20.1-0.4.2.3 | Фэнтези / Магия |
| Highlighter-1.20.1-forge-1.1.9.jar | highlighter | Highlighter | 1.1.9 | Интерфейс / Удобство (QoL) |
| HostileNeuralNetworks-1.20.1-5.3.3.jar | hostilenetworks | Hostile Neural Networks | 5.3.3 | Индустрия / Техника |
| hybrid_aquatic-forge-1.20.1-1.7.4.jar | hybrid_aquatic | Hybrid Aquatic | 1.7.4 | Мобы / Существа |
| Icarus-Forge-2.14.1.jar | icarus | Icarus | 2.14.1 | Транспорт |
| iceandfire-2.1.13-1.20.1-beta-5.jar | iceandfire | Ice and Fire | 2.1.13-1.20.1 | Мобы / Существа |
| Iceberg-1.20.1-forge-1.1.25.jar | iceberg | Iceberg | 1.1.25 | Библиотеки и зависимости (нужны другим модам) |
| iChunUtil-1.20.1-Forge-1.0.3.jar | ichunutil | iChunUtil | 1.0.3 | Библиотеки и зависимости (нужны другим модам) |
| illageandspillagerespillaged-1.2.8.jar | illageandspillage | Illage and Spillage: Respillaged | 1.2.8 | Мобы / Существа |
| IllagerInvasion-v8.0.7-1.20.1-Forge.jar | illagerinvasion | Illager Invasion | 8.0.7 | Мобы / Существа |
| immersive_aircraft-1.5.0+1.20.1-forge.jar | immersive_aircraft | Immersive Aircraft | 1.5.0+1.20.1 | Транспорт |
| immersive_armors-1.7.2+1.20.1-forge.jar | immersive_armors | Immersive Armors | 1.7.2+1.20.1 | РПГ / Приключения / Бой |
| immersive_melodies-0.8.0+1.20.1-forge.jar | immersive_melodies | Immersive Melodies | 0.8.0+1.20.1 | Прочее |
| immersive_paintings-0.6.13+1.20.1-forge.jar | immersive_paintings | Immersive Paintings | 0.6.13+1.20.1 | Декор / Строительство |
| immersive_weathering-1.20.1-2.0.5-forge.jar | immersive_weathering | Immersive Weathering | 1.20.1-2.0.5 | Реализм / Выживание |
| ImmersiveEngineering-1.20.1-10.2.0-183.jar | immersiveengineering | Immersive Engineering | 1.20.1-10.2.0-183 | Индустрия / Техника |
| ImmersivePetroleum-1.20.1-4.3.1-36b.jar | immersivepetroleum | Immersive Petroleum | 4.3.0-36 | Индустрия / Техника |
| Incendium_1.20.x_v5.3.5.jar | incendium | Incendium | 5.3.5 | Структуры / Данжи |
| industrial-foregoing-1.20.1-3.5.22.jar | industrialforegoing | Industrial Foregoing | 3.5.22 | Индустрия / Техника |
| infernalmobs-1.20.1.11.jar | infernalmobs | Infernal Mobs | 1.20.1.11 | Мобы / Существа |
| inline-forge-1.20.1-1.2.2.jar | inline | Inline | 1.20.1-1.2.2 | Библиотеки и зависимости (нужны другим модам) |
| IntegratedCrafting-1.20.1-1.4.9.jar | integratedcrafting | IntegratedCrafting | 1.4.9 | Индустрия / Техника |
| IntegratedDynamics-1.20.1-1.31.3.jar | integrateddynamics | IntegratedDynamics | 1.31.3 | Индустрия / Техника |
| IntegratedDynamics-1.20.1-1.31.3.jar | integrateddynamicscompat | IntegratedDynamics-Compat | 1.31.3 | Индустрия / Техника |
| IntegratedTerminals-1.20.1-1.7.0.jar | integratedterminals | IntegratedTerminals | 1.7.0 | Индустрия / Техника |
| IntegratedTerminals-1.20.1-1.7.0.jar | integratedterminalscompat | IntegratedTerminalsCompat | 1.7.0 | Индустрия / Техника |
| IntegratedTunnels-1.20.1-1.10.1.jar | integratedtunnels | IntegratedTunnels | 1.10.1 | Индустрия / Техника |
| IntegratedTunnels-1.20.1-1.10.1.jar | integratedtunnelscompat | IntegratedTunnels-Compat | 1.10.1 | Индустрия / Техника |
| interiors-1.20.1-forge-0.6.0.jar | interiors | Create: Interiors | 0.6.0 | Create и аддоны |
| inventoryessentials-forge-1.20.1-8.2.19.jar | inventoryessentials | Inventory Essentials | 8.2.19 | Интерфейс / Удобство (QoL) |
| inventoryhud.forge.1.20.1-3.4.26.jar | inventoryhud | Inventory HUD+ | 3.4.26 | Интерфейс / Удобство (QoL) |
| inventorysorter-1.20.1-23.0.11.jar | inventorysorter | Simple Inventory Sorter | 23.0.11 | Интерфейс / Удобство (QoL) |
| ironchests-5.0.2-forge.jar | ironchests | Iron Chests: Restocked | 5.0.2 | Хранение / Логистика |
| ironfurnaces-1.20.1-4.1.6.jar | ironfurnaces | Iron Furnaces | 4.1.6 | Индустрия / Техника |
| IronJetpacks-1.20.1-7.0.9.jar | ironjetpacks | Iron Jetpacks | 7.0.9 | Транспорт |
| irons_lib-1.20.1-2.2.0.jar | irons_lib | Iron's Lib | 1.20.1-2.2.0 | Библиотеки и зависимости (нужны другим модам) |
| irons_spellbooks-1.20.1-3.16.3.jar | irons_spellbooks | Iron's Spells 'n Spellbooks | 1.20.1-3.16.3 | Фэнтези / Магия |
| itemcollectors-1.1.12-forge-mc1.20.2.jar | itemcollectors | Item Collectors | 1.1.12 | Хранение / Логистика |
| ItemPhysic_FORGE_v1.8.17_mc1.20.1.jar | itemphysic | ItemPhysic | 1.8.17 | Реализм / Выживание |
| Jade-1.20.1-Forge-11.13.3.jar | jade | Jade | 11.13.3+forge | Интерфейс / Удобство (QoL) |
| JadeAddons-1.20.1-Forge-5.5.1.jar | jadeaddons | Jade Addons | 5.5.1+forge | Интерфейс / Удобство (QoL) |
| Jadens-Nether-Expansion-2.3.5.jar | netherexp | Jaden's Nether Expansion | 2.3.5 | Мир / Биомы |
| jamlib-forge-1.3.6+1.20.1-patch.1.jar | jamlib | JamLib | 1.3.6+1.20.1-patch.1 | Библиотеки и зависимости (нужны другим модам) |
| jei-1.20.1-forge-15.62.0.219.jar | jei | Just Enough Items | 15.62.0.219 | Интерфейс / Удобство (QoL) |
| jewelry-forge-2.4.0+1.20.1.jar | jewelry | Jewelry (RPG Series) | 2.4.0 | РПГ / Приключения / Бой |
| jumpboat-1.20.0-1.0.5.jar | jumpboat | Jumpy Boats | 1.20.0-1.0.5 | Транспорт |
| justenoughbreeding-forge-1.20.1-3.1.2.jar | justenoughbreeding | Just Enough Breeding | 3.1.2 | Интерфейс / Удобство (QoL) |
| JustEnoughProfessions-forge-1.20.1-3.0.1.jar | justenoughprofessions | Just Enough Professions (JEP) | 3.0.1 | Интерфейс / Удобство (QoL) |
| JustEnoughResources-1.20.1-1.4.0.247.jar | jeresources | Just Enough Resources | 1.4.0.247 | Интерфейс / Удобство (QoL) |
| justzoom_forge_3.0.1_MC_1.20.1.jar | justzoom | Just Zoom | 3.0.1 | Интерфейс / Удобство (QoL) |
| kaleidoscopecookery-1.6.0-forge+mc1.20.1.jar | kaleidoscope_cookery | Kaleidoscope Cookery | 1.6.0-forge+mc1.20.1 | Еда / Фермерство |
| Kambrik-6.1.1+1.20.1-forge.jar | kambrik | Kambrik | 6.1.1+1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| Kiwi-1.20.1-Forge-11.10.3.jar | kiwi | Kiwi Library | 11.10.3+forge | Библиотеки и зависимости (нужны другим модам) |
| kleeslabs-forge-1.20.1-15.0.12.jar | kleeslabs | KleeSlabs | 15.0.12 | Интерфейс / Удобство (QoL) |
| konkrete_forge_1.8.0_MC_1.20-1.20.1.jar | konkrete | Konkrete | 1.8.0 | Библиотеки и зависимости (нужны другим модам) |
| kotlinforforge-4.12.0-all.jar | (manifest/library) | Kotlin for Forge | unknown | Библиотеки и зависимости (нужны другим модам) |
| l2complements-2.6.1.jar | l2complements | L2 Complements | 2.6.1 | РПГ / Приключения / Бой |
| l2library-2.5.5.jar | l2library | L2 Library | 2.5.5 | Библиотеки и зависимости (нужны другим модам) |
| L_Enders_Cataclysm-3.31.jar | cataclysm | cataclysm | 3.31 | РПГ / Приключения / Бой |
| laserio-1.6.8.jar | laserio | LaserIO | 1.6.8 | Хранение / Логистика |
| legendarymonsters-2.2.4 MC 1.20.1.jar | legendary_monsters | LegendaryMonsters | 1.20.1 | Мобы / Существа |
| LegendaryTooltips-1.20.1-forge-1.4.5.jar | legendarytooltips | Legendary Tooltips | 1.4.5 | Интерфейс / Удобство (QoL) |
| lendersdelight-1.20.1-1.0.10b.jar | lendersdelight | L_Ender 's Delight | 1.20.1-1.0.10b | Еда / Фермерство |
| letsdo-beachparty-forge-2.0.3.jar | beachparty | [Let's Do] Beachparty | 2.0.3 | Еда / Фермерство |
| letsdo-bloomingnature-forge-1.0.12.jar | bloomingnature | [Let's Do] BloomingNature | 1.0.12 | Мир / Биомы |
| letsdo-farm_and_charm-forge-1.0.14.jar | farm_and_charm | [Let's Do] Farm & Charm | 1.0.14 | Еда / Фермерство |
| letsdo-furniture-forge-1.0.4.jar | furniture | [Let's Do] Furniture | 1.0.4 | Декор / Строительство |
| letsdo-herbalbrews-forge-1.0.12.jar | herbalbrews | [Let's Do] HerbalBrews | 1.0.12 | Еда / Фермерство |
| letsdo-meadow-forge-1.3.25.jar | meadow | [Let's Do] Meadow | 1.3.25 | Еда / Фермерство |
| letsdo-nethervinery-forge-1.2.19.jar | nethervinery | [Let's Do] NetherVinery | 1.2.19 | Еда / Фермерство |
| letsdo-vinery-forge-1.4.41.jar | vinery | [Let's Do] Vinery | 1.4.41 | Еда / Фермерство |
| letsdo-wildernature-forge-1.0.6.jar | wildernature | [Let's Do] Wilder Nature | 1.0.6 | Мобы / Существа |
| libraryferret-forge-1.20.1-4.0.0.jar | libraryferret | Library ferret | 4.0.0 | Библиотеки и зависимости (нужны другим модам) |
| lightmanscurrency-1.20.1-2.3.0.6.jar | lightmanscurrency | Lightman's Currency | 1.20.1-2.3.0.6 | Прочее |
| lionfishapi-3.0.jar | lionfishapi | LionfishAPI | 3.0 | Библиотеки и зависимости (нужны другим модам) |
| lithostitched-forge-1.20.1-1.4.11.jar | lithostitched | Lithostitched | 1.4.11 | Библиотеки и зависимости (нужны другим модам) |
| lodestone-1.20.1-1.6.4.1.jar | lodestone | Lodestone | 1.20.1-1.6.4.1 | Библиотеки и зависимости (нужны другим модам) |
| lootr-forge-1.20-0.7.35.94.jar | lootr | Lootr | 0.7.35.94 | Структуры / Данжи |
| lost_aether_content-1.20.1-1.2.3.jar | lost_aether_content | Aether: Lost Content | 1.2.3 | РПГ / Приключения / Бой |
| lostcities-1.20-7.5.7.jar | lostcities | LostCities | 1.20-7.5.7 | Мир / Биомы |
| lukis-grand-capitals-1.1.3.jar | mr_lukis_grandcapitals | Luki's Grand Capitals | 1.1.3 | Структуры / Данжи |
| mahoutsukai-1.20.1-v1.34.81.jar | mahoutsukai | Mahou Tsukai | 1.20.1-v1.34.81 | Фэнтези / Магия |
| malum-1.20.1-1.6.7.jar | malum | Malum | 1.20.1-1.6.7 | Фэнтези / Магия |
| Mantle-1.20.1-1.11.104.jar | mantle | Mantle | 1.11.104 | Библиотеки и зависимости (нужны другим модам) |
| mcjtylib-1.20-8.0.8.jar | mcjtylib | McJtyLib | 1.20-8.0.8 | Библиотеки и зависимости (нужны другим модам) |
| mcw-bridges-3.1.2-mc1.20.1forge.jar | mcwbridges | Macaw's Bridges | 3.1.2 | Декор / Строительство |
| mcw-doors-1.1.5-mc1.20.1forge.jar | mcwdoors | Macaw's Doors | 1.1.5 | Декор / Строительство |
| mcw-furniture-3.4.1-mc1.20.1forge.jar | mcwfurnitures | Macaw's Furniture | 3.4.1 | Декор / Строительство |
| mcw-lights-1.1.5-mc1.20.1forge.jar | mcwlights | Macaw's Lights and Lamps | 1.1.5 | Декор / Строительство |
| mcw-mcwfences-1.2.1-mc1.20.1forge.jar | mcwfences | Macaw's Fences and Walls | 1.2.1 | Декор / Строительство |
| mcw-mcwpaths-1.1.1-mc1.20.1forge.jar | mcwpaths | Macaw's Paths and Pavings | 1.1.1 | Декор / Строительство |
| mcw-mcwstairs-1.0.2-mc1.20.1forge.jar | mcwstairs | Macaw's Stairs and Balconies | 1.0.2 | Декор / Строительство |
| mcw-mcwwindows-2.4.2-mc1.20.1forge.jar | mcwwindows | Macaw's Windows | 2.4.2 | Декор / Строительство |
| mcw-paintings-1.1.0-mc1.20.1forge.jar | mcwpaintings | Macaw's Paintings | 1.1.0 | Декор / Строительство |
| mcw-roofs-2.3.2-mc1.20.1forge.jar | mcwroofs | Macaw's Roofs | 2.3.2 | Декор / Строительство |
| mcw-trapdoors-1.1.5-mc1.20.1forge.jar | mcwtrpdoors | Macaw's Trapdoors | 1.1.5 | Декор / Строительство |
| medieval_buildings-1.20.1-1.1.3-forge.jar | medieval_buildings | Medieval Buildings | 1.1.3 | Структуры / Данжи |
| meetyourfight-1.20.1-1.6.2.jar | meetyourfight | Meet Your Fight | 1.20.1-1.6.2 | РПГ / Приключения / Бой |
| megacells-forge-2.4.6-1.20.1.jar | megacells | MEGA Cells | 2.4.6-1.20.1 | Хранение / Логистика |
| Mekanism-1.20.1-10.4.16.80.jar | mekanism | Mekanism | 10.4.16 | Индустрия / Техника |
| MekanismAdditions-1.20.1-10.4.16.80.jar | mekanismadditions | Mekanism: Additions | 10.4.16 | Индустрия / Техника |
| MekanismGenerators-1.20.1-10.4.16.80.jar | mekanismgenerators | Mekanism: Generators | 10.4.16 | Индустрия / Техника |
| MekanismTools-1.20.1-10.4.16.80.jar | mekanismtools | Mekanism: Tools | 10.4.16 | Индустрия / Техника |
| merequester-forge-1.20.1-1.2.1.jar | merequester | ME Requester | 1.20.1-1.2.1 | Хранение / Логистика |
| midnightlib-forge-1.9.2+1.20.1.jar | midnightlib | MidnightLib | 1.9.2 | Библиотеки и зависимости (нужны другим модам) |
| minecolonies-1.20.1-1.1.1301.jar | minecolonies | MineColonies | 1.20.1-1.1.1301 | Прочее |
| minecraft-comes-alive-7.6.26+1.20.1-universal.jar | mca | Minecraft Comes Alive | 7.6.26+1.20.1 | Мобы / Существа |
| miners_delight-1.20.1-1.4.5-backport.jar | miners_delight | Miner's Delight | 1.20.1-1.4.5-backport | Еда / Фермерство |
| mininggadgets-1.15.6.jar | mininggadgets | Mining Gadgets | 1.15.6 | Индустрия / Техника |
| mischief_illagers-1.2-forge-1.20.1.jar | mischief_illagers | Mischief Illagers | 1.2 | Мобы / Существа |
| mna-forge-1.20.1-3.1.11-all.jar | mna | Mana and Artifice | 3.1.11 | Фэнтези / Магия |
| mob_grinding_utils-1.20.1-1.1.0.jar | mob_grinding_utils | Mob Grinding Utils | 1.20.1-1.1.0 | Индустрия / Техника |
| mobs_of_mythology-forge-3.0.0.jar | mobs_of_mythology | Mobs of Mythology | 3.0.0 | Мобы / Существа |
| modonomicon-1.20.1-forge-1.79.3.jar | modonomicon | Modonomicon | 1.79.3 | Библиотеки и зависимости (нужны другим модам) |
| modular-routers-12.1.1+mc1.20.1.jar | modularrouters | Modular Routers | 12.1.1+mc1.20.1 | Хранение / Логистика |
| modulargolems-2.7.5.jar | modulargolems | Modular Golems | 2.7.5 | Мобы / Существа |
| MoogsEndStructures-universal-1.20-2.1.0.jar | mes | MoogsEndStructures | 2.1.0 | Структуры / Данжи |
| MoogsNetherStructures-universal-1.20-3.1.0.jar | mns | MoogsNetherStructures | 3.1.0 | Структуры / Данжи |
| MoogsSoaringStructures-universal-1.20-2.2.0.jar | mss | MoogsSoaringStructures | 2.2.0 | Структуры / Данжи |
| MoogsStructureLib-forge-1.20-3.4.2.jar | moogs_structures | Moog's Structure Lib | 3.4.2 | Библиотеки и зависимости (нужны другим модам) |
| MoogsVoyagerStructures-universal-1.20-5.1.3.jar | mvs | MoogsVoyagerStructures | 5.1.3 | Структуры / Данжи |
| moonlight-1.20-2.16.37-forge.jar | moonlight | Moonlight Library | 1.20-2.16.37 | Библиотеки и зависимости (нужны другим модам) |
| moredelight-26.05.20-1.20-forge.jar | moredelight | More Delight | 26.05.20-1.20-forge | Еда / Фермерство |
| morehitboxes-forge-1.20.1-1.9.2.jar | morehitboxes | morehitboxes | 1.9.2 | Библиотеки и зависимости (нужны другим модам) |
| moremobvariants-forge+1.20.1-1.3.0.1.jar | moremobvariants | More Mob Variants | 1.3.0.1 | Мобы / Существа |
| moreoverlays-1.22.7-mc1.20.2.jar | moreoverlays | More Overlays Updated | 1.22.7-mc1.20.2 | Интерфейс / Удобство (QoL) |
| MouseTweaks-forge-mc1.20.1-2.25.1.jar | mousetweaks | Mouse Tweaks | 2.25.1 | Интерфейс / Удобство (QoL) |
| mowziesmobs-1.8.2.jar | mowziesmobs | Mowzie's Mobs | 1.8.2 | Мобы / Существа |
| MRU-1.0.4+1.20.1+forge.jar | mru | Mineblock's Repeated Utilities | 1.0.4+1.20.1+forge | Библиотеки и зависимости (нужны другим модам) |
| MTR-forge-4.0.5+1.20.1.jar | mtr | Minecraft Transit Railway | 4.0.5 | Транспорт |
| multipiston-1.20-1.2.43-RELEASE.jar | multipiston | Multi-Piston | 1.20-1.2.43-RELEASE | Библиотеки и зависимости (нужны другим модам) |
| MutantMonsters-v8.0.8-1.20.1-Forge.jar | mutantmonsters | Mutant Monsters | 8.0.8 | Мобы / Существа |
| mutil-1.20.1-6.3.0.jar | mutil | mutil | 6.3.0 | Библиотеки и зависимости (нужны другим модам) |
| MysticalAgradditions-1.20.1-7.0.12.jar | mysticalagradditions | Mystical Agradditions | 7.0.12 | Еда / Фермерство |
| MysticalAgriculture-1.20.1-7.0.24.jar | mysticalagriculture | Mystical Agriculture | 7.0.24 | Еда / Фермерство |
| mythicmounts-20.1-7.4.2-forge.jar | mythicmounts | MythicMounts | 20.1-7.4.2-forge | Мобы / Существа |
| mythicupgrades-forge-1.20.1-5.1.1.jar | mythicupgrades | Mythic Upgrades | 5.1.1 | РПГ / Приключения / Бой |
| naturalist-5.0pre2+forge-1.20.1.jar | naturalist | Naturalist | 5.0pre2 | Мобы / Существа |
| natures_spirit-2.2.5-1.20.1.jar | natures_spirit | Nature's Spirit | 2.2.5-1.20.1 | Мир / Биомы |
| NaturesAura-39.4.jar | naturesaura | NaturesAura | 39.4 | Фэнтези / Магия |
| NaturesCompass-1.20.1-1.12.0-forge.jar | naturescompass | Nature's Compass | 1.20.1-1.12.0-forge | Интерфейс / Удобство (QoL) |
| neapolitan-1.20.1-5.1.0.jar | neapolitan | Neapolitan | 5.1.0 | Еда / Фермерство |
| Neat-1.20.1-41-FORGE.jar | neat | Neat | 1.20.1-41-FORGE | Интерфейс / Удобство (QoL) |
| Necronomicon-Forge-1.6.0+1.20.1.jar | necronomicon | Necronomicon | 1.6.0 | Библиотеки и зависимости (нужны другим модам) |
| netherdepthsupgrade-3.1.5-1.20.jar | netherdepthsupgrade | Nether Depths Upgrade | 3.1.5-1.20 | Мир / Биомы |
| nethersdelight-1.20.1-4.0.jar | nethersdelight | Nether's Delight | 1.20.1-4.0 | Еда / Фермерство |
| notenoughanimations-forge-1.12.6-mc1.20.1.jar | notenoughanimations | NotEnoughAnimations | 1.12.6 | Реализм / Выживание |
| NuclearCraft-1.20.1-1.2.36.jar | nuclearcraft | Nuclearcraft Neoteric | 1.2.36 | Индустрия / Техника |
| Nullscape_1.20.x_v1.2.8.jar | nullscape | Nullscape | 1.2.8 | Мир / Биомы |
| nyfsspiders-Forge-1.20.1-3.0.2.jar | nyfsspiders | Nyf's Spiders | 3.0.2 | Мобы / Существа |
| obscure_api-18.jar | obscure_api | Obscure API | 18 | Библиотеки и зависимости (нужны другим модам) |
| occultism-1.20.1-1.158.0.jar | occultism | Occultism | 1.158.0 | Фэнтези / Магия |
| oceansdelight-1.0.2-1.20.jar | oceansdelight | Ocean's Delight | 1.0.2-1.20 | Еда / Фермерство |
| OctoLib-FORGE-0.5.0.1+1.20.1.jar | octolib | OctoLib | 0.5.0.1 | Библиотеки и зависимости (нужны другим модам) |
| Oh-The-Biomes-Weve-Gone-Forge-1.8.2.jar | biomeswevegone | Oh The Biomes We've Gone | 1.8.2 | Мир / Биомы |
| Oh-The-Trees-Youll-Grow-forge-1.20.1-1.7.3.jar | ohthetreesyoullgrow | Oh The Trees You'll Grow | 1.7.3 | Библиотеки и зависимости (нужны другим модам) |
| origins-forge-1.20.1-1.10.0.9-all.jar | origins | Origins | 1.20.1-1.10.0.9 | РПГ / Приключения / Бой |
| overloadedarmorbar-1.20.1-1.jar | overloadedarmorbar | Overloaded Armor Bar | 1.20.1-1 | Интерфейс / Удобство (QoL) |
| paladins-forge-3.1.3+1.20.1.jar | paladins | Paladins & Priests (RPG Series) | 3.1.3 | Фэнтези / Магия |
| pamhc2foodcore-1.20.4-1.0.5.jar | pamhc2foodcore | Pam's HarvestCraft 2 - Food Core | 1.0.5 | Еда / Фермерство |
| Paraglider-forge-20.1.3.jar | paraglider | Paraglider | 20.1.3 | Транспорт |
| ParCool-1.20.1-4.0.1.5.jar | parcool | ParCool! | 4.0.1.5 | Реализм / Выживание |
| particlerain-4.0.2+1.20.1-forge.jar | particlerain | Particle Rain | 4.0.2 | Реализм / Выживание |
| particular-1.20.1-Forge-1.5.7.jar | particular | Particular Reforged | 1.5.7 | Реализм / Выживание |
| Patchouli-1.20.1-85-FORGE.jar | patchouli | Patchouli | 1.20.1-85-FORGE | Библиотеки и зависимости (нужны другим модам) |
| paucal-0.6.0+1.20.1-forge.jar | paucal | PAUCAL | 0.6.0+1.20.1-forge | Библиотеки и зависимости (нужны другим модам) |
| PhilipsRuins1.20.1-5.7.jar | philipsruins | Philips Ruins | 5.6 | Структуры / Данжи |
| PickUpNotifier-v8.0.0-1.20.1-Forge.jar | pickupnotifier | Pick Up Notifier | 8.0.0 | Интерфейс / Удобство (QoL) |
| PigPen-Forge-1.20.1-15.0.3.jar | pigpen | PigPen | 15.0.3 | Библиотеки и зависимости (нужны другим модам) |
| pipez-1.20.1-1.2.5.jar | pipez | Pipez | 1.20.1-1.2.5 | Хранение / Логистика |
| Placebo-1.20.1-8.6.3.jar | placebo | Placebo | 8.6.3 | Библиотеки и зависимости (нужны другим модам) |
| Platform-forge-1.20.1-1.4.1.jar | platform | Platform | 1.4.1 | Библиотеки и зависимости (нужны другим модам) |
| player-animation-lib-forge-1.0.2-rc1+1.20.jar | playeranimator | Player Animator | 1.0.2-rc1+1.20 | Библиотеки и зависимости (нужны другим модам) |
| pneumaticcraft-repressurized-6.0.23+mc1.20.1.jar | pneumaticcraft | PneumaticCraft: Repressurized | 6.0.23+mc1.20.1 | Индустрия / Техника |
| polymorph-forge-0.49.11+1.20.1.jar | polymorph | Polymorph | 0.49.11+1.20.1 | Интерфейс / Удобство (QoL) |
| Powah-5.0.11.jar | powah | Powah | 5.0.11 | Индустрия / Техника |
| prefab-1.10.0.1.jar | prefab | Prefab | 1.10.0.1 | Декор / Строительство |
| Prism-1.20.1-forge-1.0.5.jar | prism | Prism | 1.0.5 | Библиотеки и зависимости (нужны другим модам) |
| productivebees-1.20.1-12.6.0.jar | productivebees | Productive Bees | 1.20.1-12.6.0 | Еда / Фермерство |
| ProjectE-1.20.1-PE1.0.1.jar | projecte | ProjectE | 1.0.1 | Фэнтези / Магия |
| Psi-1.20.1-102.jar | psi | Psi | 2.0.0 | Фэнтези / Магия |
| puffish_skills-0.19.2-1.20-forge.jar | puffish_skills | Pufferfish's Skills | 0.19.2 | РПГ / Приключения / Бой |
| PuzzlesLib-v8.1.36-1.20.1-Forge.jar | puzzleslib | Puzzles Lib | 8.1.36 | Библиотеки и зависимости (нужны другим модам) |
| Quark-4.0-462.jar | quark | Quark | 4.0-462 | Декор / Строительство |
| ranged_weapon_api-forge-2.3.4+1.20.1.jar | ranged_weapon_api | RangedWeaponAPI | 2.3.4+1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| Re-Avaritia-forge-1.20.1-1.4.2-release.jar | avaritia | Re-Avaritia | 1.4.2 | РПГ / Приключения / Бой |
| rechiseled-1.2.6-forge-mc1.20.1.jar | rechiseled | Rechiseled | 1.2.6 | Декор / Строительство |
| refinedstorage-1.12.4.jar | refinedstorage | Refined Storage | 1.12.4 | Хранение / Логистика |
| refinedstorageaddons-0.10.0.jar | refinedstorageaddons | Refined Storage Addons | 0.10.0 | Хранение / Логистика |
| refurbished_furniture-forge-1.20.1-1.0.20.jar | refurbished_furniture | MrCrayfish's Furniture Mod: Refurbished | 1.0.20 | Декор / Строительство |
| RegionsUnexploredForge-0.5.6+1.20.1.jar | regions_unexplored | Regions Unexplored | 0.5.6 | Мир / Биомы |
| relics-1.20.1-0.8.0.13.jar | relics | Relics | 0.8.0.13 | РПГ / Приключения / Бой |
| reliquary-1.20.1-2.0.66.1577.jar | reliquary | Reliquary | 2.0.66.1577 | Фэнтези / Магия |
| repurposed_structures-7.1.25+1.20.1-forge.jar | repurposed_structures | Repurposed Structures | 7.1.25+1.20.1-forge | Структуры / Данжи |
| resourcefulconfig-forge-1.20.1-2.1.3.jar | resourcefulconfig | Resourcefulconfig | 2.1.3 | Библиотеки и зависимости (нужны другим модам) |
| resourcefullib-forge-1.20.1-2.1.29.jar | resourcefullib | Resourceful Lib | 2.1.29 | Библиотеки и зависимости (нужны другим модам) |
| rftoolsbase-1.20-5.0.7.jar | rftoolsbase | RFToolsBase | 1.20-5.0.7 | Библиотеки и зависимости (нужны другим модам) |
| rftoolsbuilder-1.20-6.0.10.jar | rftoolsbuilder | RFToolsBuilder | 1.20-6.0.10 | Индустрия / Техника |
| rftoolscontrol-1.20-7.0.4.jar | rftoolscontrol | RFToolsControl | 1.20-7.0.4 | Индустрия / Техника |
| rftoolsdim-1.20-11.0.11.jar | rftoolsdim | RFToolsDimensions | 1.20-11.0.11 | Мир / Биомы |
| rftoolspower-1.20-6.0.3.jar | rftoolspower | RFToolsPower | 1.20-6.0.3 | Индустрия / Техника |
| rftoolsstorage-1.20-5.0.4.jar | rftoolsstorage | RFToolsStorage | 1.20-5.0.4 | Хранение / Логистика |
| rftoolsutility-1.20-6.0.7.jar | rftoolsutility | RFToolsUtility | 1.20-6.0.7 | Индустрия / Техника |
| Ribbits-1.20.1-Forge-3.0.5.jar | ribbits | Ribbits | 1.20.1-Forge-3.0.5 | Структуры / Данжи |
| rightclickharvest-forge-4.6.1+1.20.1.jar | rightclickharvest | Right Click Harvest | 4.6.1+1.20.1 | Еда / Фермерство |
| ritchiesprojectilelib-2.1.1+mc.1.20.1-forge.jar | ritchiesprojectilelib | Ritchie's Projectile Library | 2.1.1 | Библиотеки и зависимости (нужны другим модам) |
| rogues-forge-3.1.3+1.20.1.jar | rogues | Rogues & Warriors (RPG Series) | 3.1.3 | РПГ / Приключения / Бой |
| rottencreatures-forge-1.20.1-1.1.0.jar | rottencreatures | Rotten Creatures | 1.1.0 | Мобы / Существа |
| rsrequestify-1.20.1-2.3.3.jar | rsrequestify | RSRequestify | 2.3.3 | Хранение / Логистика |
| Runelic-Forge-1.20.1-18.0.4.jar | runelic | Runelic | 18.0.4 | Библиотеки и зависимости (нужны другим модам) |
| sanitydim-mc1.20-1.1.0.jar | sanitydim | Sanity: Descent Into Madness | 1.1.0 | Реализм / Выживание |
| savage_and_ravage-1.20.1-6.0.1.jar | savage_and_ravage | Savage & Ravage | 6.0.1 | Мобы / Существа |
| sawmill-1.20-1.4.11.jar | sawmill | Universal Sawmill | 1.20-1.4.11 | Интерфейс / Удобство (QoL) |
| Searchables-forge-1.20.1-1.0.3.jar | searchables | Searchables | 1.0.3 | Библиотеки и зависимости (нужны другим модам) |
| SereneSeasons-forge-1.20.1-9.1.0.3.jar | sereneseasons | Serene Seasons | 9.1.0.3 | Реализм / Выживание |
| shield_api-forge-2.2.0+1.20.1.jar | shield_api | ShieldAPI | 2.2.0+1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| ShoulderSurfing-Forge-1.20.1-5.2.0.jar | shouldersurfing | Shoulder Surfing Reloaded | 1.20.1-5.2.0 | Интерфейс / Удобство (QoL) |
| shulkerboxtooltip-forge-4.0.4+1.20.1.jar | shulkerboxtooltip | ShulkerBoxTooltip | 4.0.4+1.20.1 | Интерфейс / Удобство (QoL) |
| silent-gear-1.20.1-3.6.7.jar | silentgear | Silent Gear | 3.6.7 | РПГ / Приключения / Бой |
| silent-lib-1.20.1-8.0.0.jar | silentlib | Silent Lib | 8.0.0 | Библиотеки и зависимости (нужны другим модам) |
| simplemagnets-1.1.12-forge-mc1.20.1.jar | simplemagnets | Simple Magnets | 1.1.12 | Интерфейс / Удобство (QoL) |
| simpleplanes-1.20.1-5.3.3.jar | simpleplanes | Simple Planes | 1.20.1-5.3.3 | Транспорт |
| SimpleStorageNetwork-1.20.1-1.13.2.jar | storagenetwork | Simple Storage Network | 1.13.2 | Хранение / Логистика |
| simplyswords-forge-neoforge-1.70.2-1.20.1.jar | simplyswords | Simply Swords | 1.70.2-1.20.1 | РПГ / Приключения / Бой |
| SimplyTooltips-forge-0.1.5-1.20.1.jar | simplytooltips | Simply Tooltips | 0.1.5-1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| sit-1.20.1-1.3.5.jar | sit | Sit | 1.3.5 | Интерфейс / Удобство (QoL) |
| skinlayers3d-forge-1.11.4-mc1.20.1.jar | skinlayers3d | 3d-Skin-Layers | 1.11.4 | Прочее |
| SlashBladeResharped-1.20.1-1.9.65.jar | slashblade | Slash Blade:Resharped | 1.9.65 | РПГ / Приключения / Бой |
| sliceanddice-forge-3.6.0.jar | sliceanddice | Create Slice & Dice | 3.6.0 | Create и аддоны |
| smallships-2.0.2-forge-1.20.1.jar | smallships | Small Ships | 2.0.2 | Транспорт |
| SmartBrainLib-forge-1.20.1-1.15.jar | smartbrainlib | SmartBrainLib | 1.15 | Библиотеки и зависимости (нужны другим модам) |
| smarterfarmers-1.20-2.1.2.jar | smarterfarmers | Smarter Farmers | 1.20-2.1.2 | Еда / Фермерство |
| SnowRealMagic-1.20.1-Forge-10.7.0.jar | snowrealmagic | Snow! Real Magic! | 10.7.0 | Реализм / Выживание |
| snowundertrees-1.20.1-1.4.12.jar | snowundertrees | Snow Under Trees | 1.4.12 | Реализм / Выживание |
| solcarrot-1.20.1-1.15.1.jar | solcarrot | Spice of Life: Carrot Edition | 1.15.1 | Еда / Фермерство |
| sophisticatedbackpacks-1.20.1-3.26.14.2226.jar | sophisticatedbackpacks | Sophisticated Backpacks | 3.26.14.2226 | Хранение / Логистика |
| sophisticatedcore-1.20.1-1.5.15.2427.jar | sophisticatedcore | Sophisticated Core | 1.5.15.2427 | Библиотеки и зависимости (нужны другим модам) |
| sophisticatedstorage-1.20.1-1.5.9.2197.jar | sophisticatedstorage | Sophisticated Storage | 1.5.9.2197 | Хранение / Логистика |
| soulslike-weaponry-1.4.10-1.20.1-forge.jar | soulsweapons | Marium's Soulslike Weaponry | 1.4.10-1.20.1-forge} | РПГ / Приключения / Бой |
| sound-physics-remastered-forge-1.20.1-1.5.1.jar | sound_physics_remastered | Sound Physics Remastered | 1.20.1-1.5.1 | Реализм / Выживание |
| Sounds-2.2.1+1.20.1+forge.jar | sounds | Sounds | 2.2.1+1.20.1+forge | Реализм / Выживание |
| SpartanShields-1.20.1-forge-3.1.1.jar | spartanshields | Spartan Shields | 3.1.1 | РПГ / Приключения / Бой |
| SpartanWeaponry-1.20.1-forge-3.2.1-all.jar | spartanweaponry | Spartan Weaponry | 3.2.1 | РПГ / Приключения / Бой |
| spawn-4.0.8-1.20.1.jar | spawn | Spawn | 4.0.8-1.20.1 | Мобы / Существа |
| species-3.5.jar | species | Species | 3.5 | Мобы / Существа |
| spell_engine-forge-1.10.10+1.20.1.jar | spell_engine | Spell Engine | 1.10.10+1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| spell_power-forge-1.6.1+1.20.1.jar | spell_power | Spell Power Attribute | 1.6.1+1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| spelunkery-1.20.1-0.3.16-forge.jar | spelunkery | Spelunkery | 1.20.1-0.3.16 | Мир / Биомы |
| stalwart-dungeons-1.20.1-1.2.8.jar | stalwart_dungeons | Stalwart Dungeons | 1.2.8 | Структуры / Данжи |
| Steam_Rails-1.7.3+forge-mc1.20.1.jar | railways | Create: Steam 'n' Rails | 1.7.3+forge-mc1.20.1 | Create и аддоны |
| StorageDrawers-forge-1.20.1-12.15.1.jar | storagedrawers | Storage Drawers | 12.15.1 | Хранение / Логистика |
| Structory_1.20.x_v1.3.5.jar | structory | Structory | 1.3.5 | Структуры / Данжи |
| Structory_Towers_1.20.x_v1.0.7.jar | structory_towers | Structory: Towers | 1.0.7 | Структуры / Данжи |
| structure_gel-1.20.1-2.16.2.jar | structure_gel | Structure Gel API | 2.16.2 | Библиотеки и зависимости (нужны другим модам) |
| structure_pool_api-forge-1.2.1+1.20.1.jar | structure_pool_api | Structure Pool API | 1.2.1+1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| structurize-1.20.1-1.0.818.jar | structurize | Structurize | 1.20.1-1.0.818 | Библиотеки и зависимости (нужны другим модам) |
| SubtleEffects-forge-1.20.1-1.15.1-hotfix.1.jar | subtle_effects | Subtle Effects | 1.15.1-hotfix.1 | Реализм / Выживание |
| Super Factory Manager (SFM)-MC1.20.1-4.34.0.jar | sfm | Super Factory Manager (SFM) | 4.34.0 | Хранение / Логистика |
| supermartijn642configlib-1.1.8-forge-mc1.20.jar | supermartijn642configlib | SuperMartijn642's Config Library | 1.1.8 | Библиотеки и зависимости (нужны другим модам) |
| supermartijn642corelib-1.1.25a-forge-mc1.20.1.jar | supermartijn642corelib | SuperMartijn642's Core Lib | 1.1.25+a | Библиотеки и зависимости (нужны другим модам) |
| supplementaries-1.20-3.1.44-forge.jar | supplementaries | Supplementaries | 1.20-3.1.44 | Декор / Строительство |
| suppsquared-1.20-1.1.29.jar | suppsquared | Supplementaries Squared | 1.20-1.1.29 | Декор / Строительство |
| tacz-1.20.1-1.1.8-hotfix2.jar | tacz | Timeless & Classics Guns: Zero | 1.1.8-hotfix2 | РПГ / Приключения / Бой |
| takesapillage-1.0.3-1.20.1.jar | takesapillage | It Takes A Pillage | 1.0.3 | Мобы / Существа |
| TConstruct-1.20.1-3.11.2.166.jar | tconstruct | Tinkers' Construct | 3.11.2.166 | Индустрия / Техника |
| tectonic-3.0.17-forge-1.20.1.jar | tectonic | Tectonic | 3.0.17 | Мир / Биомы |
| TerraBlender-forge-1.20.1-3.0.1.11.jar | terrablender | TerraBlender | 3.0.1.11 | Библиотеки и зависимости (нужны другим модам) |
| Terralith_1.20.x_v2.5.4.jar | terralith | Terralith | 2.5.4 | Мир / Биомы |
| tetra-1.20.1-6.17.0.jar | tetra | tetra | 6.17.0 | РПГ / Приключения / Бой |
| tfmg-1.0.2f.jar | tfmg | Create: The Factory Must Grow | 1.0.2f | Create и аддоны |
| The-Man-From-The-Fog-1.4-1.20.1.jar | man | The Man From The Fog | 1.4 | Мобы / Существа |
| the_bumblezone-7.15.1+1.20.1-forge.jar | the_bumblezone | The Bumblezone | 7.15.1+1.20.1-forge | Мир / Биомы |
| The_Undergarden-1.20.1-0.8.14.jar | undergarden | The Undergarden | 0.8.14 | Мир / Биомы |
| thermal_cultivation-1.20.1-11.0.1.24.jar | thermal_cultivation | Thermal Cultivation | 11.0.1 | Индустрия / Техника |
| thermal_dynamics-1.20.1-11.0.1.23.jar | thermal_dynamics | Thermal Dynamics | 11.0.1 | Индустрия / Техника |
| thermal_expansion-1.20.1-11.0.1.29.jar | thermal_expansion | Thermal Expansion | 11.0.1 | Индустрия / Техника |
| thermal_foundation-1.20.1-11.0.6.70.jar | thermal_foundation | Thermal Foundation | 11.0.6 | Индустрия / Техника |
| thermal_innovation-1.20.1-11.0.1.23.jar | thermal_innovation | Thermal Innovation | 11.0.1 | Индустрия / Техника |
| thermal_locomotion-1.20.1-11.0.1.19.jar | thermal_locomotion | Thermal Locomotion | 11.0.1 | Транспорт |
| tidal-towns-2.0.jar | mr_tidal_towns | Tidal Towns | 2.0 | Структуры / Данжи |
| Tips-Forge-1.20.1-12.1.9.jar | tipsmod | Tips | 12.1.9 | Интерфейс / Удобство (QoL) |
| titanium-1.20.1-3.8.35.jar | titanium | Titanium | 3.8.35 | Библиотеки и зависимости (нужны другим модам) |
| tlc_forge-1.0.3-R-1.20.X.jar | tlc | The Lost Castle | 1.0.2 | Структуры / Данжи |
| ToastControl-1.20.1-8.0.3.jar | toastcontrol | Toast Control | 8.0.3 | Интерфейс / Удобство (QoL) |
| toms_storage-1.20-1.7.1.jar | toms_storage | Tom's Simple Storage Mod | 1.7.1 | Хранение / Логистика |
| ToolBelt-1.20.1-1.20.03.jar | toolbelt | Tool Belt | 1.20.03 | Хранение / Логистика |
| torchmaster-20.1.9.jar | torchmaster | Torchmaster | 20.1.9 | Прочее |
| totw_modded-forge-1.20.1-1.0.5.jar | totw_modded | Towers of the Wild Modded | 1.0.5 | Структуры / Данжи |
| ToughAsNails-forge-1.20.1-9.2.0.171.jar | toughasnails | Tough As Nails | 9.2.0.171 | Реализм / Выживание |
| touhoulittlemaid-1.5.3-forge+mc1.20.1.jar | touhou_little_maid | Touhou Little Maid | 1.5.3-forge+mc1.20.1 | Мобы / Существа |
| Towns-and-Towers-1.12-Fabric+Forge.jar | t_and_t | Towns and Towers | 1.10.2 | Структуры / Данжи |
| towntalk-1.20.1-1.1.0.jar | towntalk | TownTalk | 1.1.0 | Библиотеки и зависимости (нужны другим модам) |
| TradingPost-v8.0.2-1.20.1-Forge.jar | tradingpost | Trading Post | 8.0.2 | Интерфейс / Удобство (QoL) |
| trashcans-1.1.2-forge-mc1.20.4.jar | trashcans | Trash Cans | 1.1.2 | Хранение / Логистика |
| trashslot-forge-1.20.1-15.1.5.jar | trashslot | TrashSlot | 15.1.5 | Интерфейс / Удобство (QoL) |
| travelersbackpack-forge-1.20.1-9.1.58.jar | travelersbackpack | Traveler's Backpack | 9.1.58 | Хранение / Логистика |
| TravelersTitles-1.20-Forge-4.0.2.jar | travelerstitles | Traveler's Titles | 1.20-Forge-4.0.2 | Интерфейс / Удобство (QoL) |
| trek-B0.6.2.jar | mr_trek | Trek | 1-B0.6.2 | Структуры / Данжи |
| tru.e-ending-v1.1.0c.jar | mr_limesplatus_ending | True Ending: Ender Dragon Overhaul | 1-v1.1.0c | РПГ / Приключения / Бой |
| tru.e-ending-v1.1.0c.jar | mr_true_ending | True Ending: Ender Dragon Overhaul | 1-v1.1.0c | РПГ / Приключения / Бой |
| Twigs-1.20.1-3.1.1.jar | twigs | Twigs | 1.20.1-3.1.1 | Декор / Строительство |
| twilightdelight-2.2.7.jar | twilightdelight | Twilight's Flavor & Delight | 2.2.7 | Еда / Фермерство |
| twilightforest-1.20.1-4.3.2508-universal.jar | twilightforest | The Twilight Forest | 4.3.2508 | РПГ / Приключения / Бой |
| undergroundworlds-forge-3.0.1-1.20.1.jar | undergroundworlds | Underground Worlds | 3.0.1 | Структуры / Данжи |
| unusualend-2.3.1.2.jar | unusualend | Unusual End | 2.3.1.2 | Мир / Биомы |
| upgrade_aquatic-1.20.1-6.0.3.jar | upgrade_aquatic | Upgrade Aquatic | 6.0.3 | Мобы / Существа |
| valhelsia_core-forge-1.20.1-1.1.2.jar | valhelsia_core | Valhelsia Core | 1.1.2 | Библиотеки и зависимости (нужны другим модам) |
| valhelsia_structures-forge-1.20.1-1.1.2.jar | valhelsia_structures | Valhelsia Structures | 1.20.1-1.1.2 | Структуры / Данжи |
| Vampirism-1.20.1-1.10.17.jar | vampirism | Vampirism | 1.10.17 | РПГ / Приключения / Бой |
| Vampirism-1.20.1-1.10.17.jar | teamlapenlib | Teamlapen Library | 1.10.17 | РПГ / Приключения / Бой |
| VanillaBackport-forge-1.20.1-1.2.1.jar | vanillabackport | VanillaBackport | 1.2.1 | Прочее |
| villagertradingplus-forge-mc1.20.1-1.1.0.jar | villagertradingplus | VillagerTradingPlus | 1.1.0+mc1.20.1 | Библиотеки и зависимости (нужны другим модам) |
| villagesandpillages-forge-2.0.0+mc1.20.1.jar | villagesandpillages | Villages&Pillages | 2.0.0 | Структуры / Данжи |
| visuality-forge-2.2.0.jar | visuality | Visuality: Reforged | 2.2.0 | Интерфейс / Удобство (QoL) |
| VisualWorkbench-v8.0.1-1.20.1-Forge.jar | visualworkbench | Visual Workbench | 8.0.1 | Интерфейс / Удобство (QoL) |
| voicechat-forge-1.20.1-2.6.22.jar | voicechat | Simple Voice Chat | 1.20.1-2.6.22 | Прочее |
| voicechat-forge-1.20.1-2.6.22.jar | voicechat_api | Simple Voice Chat API | 2.6.20 | Прочее |
| voidtotem-forge-1.20-3.0.1.jar | voidtotem | Void Totem | 3.0.1 | Интерфейс / Удобство (QoL) |
| Waddles-1.20.1-0.9.4.jar | waddles | Waddles | 0.9.4 | Мобы / Существа |
| watut-forge-1.20.1-1.2.3.jar | watut | What Are They Up To | 1.20.1-1.2.3 | Реализм / Выживание |
| waveycapes-forge-1.11.3-mc1.20.1.jar | waveycapes | WaveyCapes | 1.11.3 | Интерфейс / Удобство (QoL) |
| waystones-forge-1.20.1-14.1.21.jar | waystones | Waystones | 14.1.21 | Транспорт |
| weather2-1.20.1-2.8.3.jar | weather2 | Weather2 | 1.20.1-2.8.3 | Реализм / Выживание |
| weeping_angels-46.1.6-forge.jar | weeping_angels | Weeping Angels | 1 | Мобы / Существа |
| wizards-forge-3.1.3+1.20.1.jar | wizards | Wizards (RPG Series) | 3.1.3 | Фэнтези / Магия |
| woodworks-1.20.1-3.0.2.jar | woodworks | Woodworks | 3.0.2 | Декор / Строительство |
| xaerominimap-forge-1.20.1-26.6.0.jar | xaerominimap | Xaero's Minimap | 26.6.0 | Интерфейс / Удобство (QoL) |
| xaeroworldmap-forge-1.20.1-1.47.0.jar | xaeroworldmap | Xaero's World Map | 1.47.0 | Интерфейс / Удобство (QoL) |
| xnet-1.20-6.1.7.jar | xnet | XNet | 1.20-6.1.7 | Хранение / Логистика |
| xnetgases-1.20.1-5.1.4.jar | xnetgases | XNet Gases | 5.1.4 | Хранение / Логистика |
| xptome-1.20.1-2.2.1.jar | xpbook | XP Tome | 2.2.1 | Интерфейс / Удобство (QoL) |
| yet_another_config_lib_v3-3.6.6+1.20.1-forge.jar | yet_another_config_lib_v3 | YetAnotherConfigLib | 3.6.6+1.20.1-forge | Библиотеки и зависимости (нужны другим модам) |
| YungsApi-1.20-Forge-4.0.6.jar | yungsapi | YUNG's API | 1.20-Forge-4.0.6 | Библиотеки и зависимости (нужны другим модам) |
| YungsBetterCaves-1.20.1-Forge-2.0.7.jar | bettercaves | YUNG's Better Caves | 1.20.1-Forge-2.0.7 | Мир / Биомы |
| YungsBetterDesertTemples-1.20-Forge-3.0.3.jar | betterdeserttemples | YUNG's Better Desert Temples | 1.20-Forge-3.0.3 | Структуры / Данжи |
| YungsBetterDungeons-1.20-Forge-4.0.4.jar | betterdungeons | YUNG's Better Dungeons | 1.20-Forge-4.0.4 | Структуры / Данжи |
| YungsBetterEndIsland-1.20-Forge-2.0.6.jar | betterendisland | YUNG's Better End Island | 1.20-Forge-2.0.6 | Структуры / Данжи |
| YungsBetterJungleTemples-1.20-Forge-2.0.5.jar | betterjungletemples | YUNG's Better Jungle Temples | 1.20-Forge-2.0.5 | Структуры / Данжи |
| YungsBetterMineshafts-1.20-Forge-4.0.4.jar | bettermineshafts | YUNG's Better Mineshafts | 1.20-Forge-4.0.4 | Структуры / Данжи |
| YungsBetterNetherFortresses-1.20-Forge-2.0.6.jar | betterfortresses | YUNG's Better Nether Fortresses | 1.20-Forge-2.0.6 | Структуры / Данжи |
| YungsBetterOceanMonuments-1.20-Forge-3.0.4.jar | betteroceanmonuments | YUNG's Better Ocean Monuments | 1.20-Forge-3.0.4 | Структуры / Данжи |
| YungsBetterStrongholds-1.20-Forge-4.0.3.jar | betterstrongholds | YUNG's Better Strongholds | 1.20-Forge-4.0.3 | Структуры / Данжи |
| YungsBetterWitchHuts-1.20-Forge-3.0.3.jar | betterwitchhuts | YUNG's Better Witch Huts | 1.20-Forge-3.0.3 | Структуры / Данжи |
| YungsBridges-1.20-Forge-4.0.3.jar | yungsbridges | YUNG's Bridges | 1.20-Forge-4.0.3 | Структуры / Данжи |
| YungsCaveBiomes-1.20.1-Forge-2.0.5.jar | yungscavebiomes | YUNG's Cave Biomes | 1.20.1-Forge-2.0.5 | Мир / Биомы |
| YungsExtras-1.20-Forge-4.0.3.jar | yungsextras | YUNG's Extras | 1.20-Forge-4.0.3 | Структуры / Данжи |
| yuushya-1.20.1-forge-2.2.3.jar | yuushya | Yuushya | 2.2.3 | Декор / Строительство |
| ZeroCore2-1.20.1-2.1.48.jar | zerocore | Zero CORE 2 | 1.20.1-2.1.48 | Библиотеки и зависимости (нужны другим модам) |
| Zeta-1.0-31.jar | zeta | Zeta | 1.0-31 | Библиотеки и зависимости (нужны другим модам) |
| zombieawareness-1.20.1-1.13.1.jar | zombieawareness | Zombie Awareness | 1.20.1-1.13.1 | Мобы / Существа |
