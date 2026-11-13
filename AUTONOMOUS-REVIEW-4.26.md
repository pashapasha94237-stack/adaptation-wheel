# Независимая проверка автономной защиты 4.26

Дата: 26 октября 2026. Read-only review production-кода; проверяющий не менял Java/coremods, не запускал Gradle или игру. Native QA и сборки выполняет основной workflow.

Область: `AutonomousHealthGuard.java`, `autonomous_health.js`, `AdaptiveStatePlans.java`, `adaptive_states.js`, `StatePlan.java`; дополнительно их связь с `AdaptationEvents`, `ModAttackCompatibility`, First Aid и новым `effect_dispatch.js` / `NegativeEffects.nativeInstant`.

## Найденные и исправленные замечания

### P1 — SanityDim: запрос за пределами 0…1 обходил даже полную защиту

В `SanityProcessor.addSanity` и `lambda$tickPlayer$3` оригинальный запрос вычисляется как `getSanity() + delta`, без предварительного clamp. Оригинальный `Sanity.setSanity(float)` ограничивает его через `MathHelper.clampNorm` внутри setter. Предыдущий bridge проверял `StatePlan.accepts(requested)` раньше, поэтому при `before=1`, `requested>1` возвращал исходный запрос; нативный setter опять сохранял 1, и четвёртый этап не очищал состояние.

Исправление основного workflow просмотрено: конкретный `sanity` bridge зеркалирует finite clamp до вызова плана. Общий fail-open для неизвестной схемы не ослаблен.

### P2 — Mekanism: искусственный предел 1e12 не соответствовал нативному setter

`DefaultRadiationEntity.set(double)` имеет только нижнюю границу `max(1e-7, value)`, а `radiate(double)` суммирует положительную дозу. Локальный максимум плана 1e12 пропускал finite более высокую дозу даже при полной адаптации. Исправление просмотрено: максимум изменён на `Double.MAX_VALUE`; NaN/infinite остаются вне доказанной схемы.

### P2 — добровольная HP-цена предметов превращалась в адаптируемую атаку

Проверены оригинальные JAR и точные callsites:

- `Draconic-Evolution-1.20.1-3.1.2.621-universal.jar`: `com.brandon3055.draconicevolution.items.tools.Dislocator.m_7203_`, `setHealth` на byte offset 232, снимает 2 HP после успешного перемещения.
- `embersrekindled-1.20.1-1.4.7.jar`: `com.rekindled.embers.item.InflictorGemItem.m_7203_`, `setHealth` на offset 65, при sneak-use удаляет item NBT `type` и платит `max(health-10,1)`.

У обоих descriptor: `(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;`, dev alias — `use`.

Исправление просмотрено: добавлены точные class+method исключения, как у ранее защищённой добровольной платы Blood Magic. Широкого исключения всех `Item.use` нет; атаки оружия сохраняют защиту.

## Замечания по новому общему dispatch эффектов

### P2 — преждевременный старт давал первому мгновенному урону повторный бонус

В проверенной версии `NegativeEffects.nativeInstant` сначала вызывал `skipNativeAction`, затем `continuousExposure → state.hit`. Оригинальный мгновенный HARM после этого вызывал `hurt(magic/indirect_magic)`, который канонизируется в тот же `effect:minecraft:instant_damage`. Обработчик контакта уже видел `running`; очередь Forge и END tick давали второй `state.hit` одному первому удару: `hits=1`, скорость 1.30 вместо 1.00. Если первый hurt отменён, eager-start всё равно выдавал адаптацию до реального воздействия.

Окончательное исправление просмотрено: новый global instant-dispatch и `nativeInstant` удалены целиком. Оригинальный vanilla HARM остаётся в штатной очереди реального урона, а прежние точные instant-effect guards сохранены. Поэтому новый eager-start больше не существует. Native potion fixture основного workflow проверяет отменённый первый удар и скорости 1.00 / 1.30; его итоговый игровой результат публикуется основным workflow.

Подтверждение нативного пути: `PotionItem.finishUsingItem = m_5922_(ItemStack, Level, LivingEntity)ItemStack`; `ItemStack.finishUsingItem = m_41671_(Level, LivingEntity)ItemStack`. В PotionItem на offset 108 вызывается `MobEffect.m_19461_(Entity, Entity, LivingEntity, int, double)V` с силой 1.0. Мгновенная ветка не вызывает `addEffect`.

### P1 — удаление invoke конфликтует с обязательными Biomancy mixins

В проверенной версии `effect_dispatch.js:12` заменял `INVOKEVIRTUAL MobEffect.applyInstantenousEffect` на статический wrapper. В исходном корпусе есть `biomancy-forge-1.20.1-2.9.0.1-alpha.0.jar` с обязательными `ThrownPotionMixin` и `AreaEffectCloudMixin`. Они используют MixinExtras `WrapWithCondition` с точным `@At(INVOKE)` этого вызова. Конфигурация `mixins.biomancy.json` содержит `required: true`, `injectors.defaultRequire: 1`.

После применения такого coremod целевой invoke исчезал: если этот байткод получал Mixin, обязательная инъекция не находила цель. Это подтверждённый конфликт структурных контрактов, а не выполненный здесь игровой crash repro.

Окончательное исправление просмотрено: `effect_dispatch.js` теперь добавляет только entry gate в `MobEffectInstance.applyEffect(LivingEntity)`, обнаруженный по нативному periodic invoke. Скрипт использует только `instructions.insert(start)`, не заменяет и не удаляет исходные инструкции. Исходный `MobEffect.applyEffectTick` и его descriptor сохранены. `PotionItem`, `ThrownPotion` и `AreaEffectCloud` больше вообще не являются target этого coremod; обязательные Biomancy instant invoke остались неизменными. Этот конкретный P1 структурно устранён.

Создана отдельная native группа `compat-tests/multimod/probe/autonomous-health-biomancy.json` и идентичная копия во внешнем probe. Она использует supplied Biomancy 2.9.0.1-alpha.0 и GeckoLib 4.8.4. Metadata требует только Forge/Minecraft и GeckoLib ≥4.7; JEI optional, MixinExtras и H2 встроены в сам Biomancy JAR. Запуск этой группы выполняет основной workflow, не автор review.

## Что не показало дополнительных подтверждённых ошибок

- Каждый вложенный tracked-data вызов при активном frame получает собственный marker, в том числе не-Float/не-health данные. Return не снимает внешний scope.
- Нативный `setHealth` и его внутренний tracked-data write не применяют множитель дважды. First Aid получает guard до своего body-model adapter; внутренние parent calls не дают дополнительную автономную запись.
- `ModAttackCompatibility.directHealthWrite` учитывает автономный scope, а `healthWriteScoped` в самом guard учитывает только прежние явные scopes. Это не образует самоблокировку.
- Исходное увеличение HP проходит нативно. Загрузка, respawn, totem и операторские пути сохраняют свои исключения. First Aid `toggleTracking(false)` найден в нативной проверке totem, уже исключённой lifecycle-проверкой.
- Native callback exception после изменения tracked HP теперь сохраняет измеренный фактический ущерб; exception до commit не создаёт fake receipt. Cleanup остаётся в `finally`.
- Повторные `continuousExposure` одного TAN/Mek condition в одном tick не добавляют бонус: после первого запуска состояние уже `running`.
- Благоприятные эффекты проходят `skipNativeAction` без отмены. Новый periodic entry gate оставляет оригинальный виртуальный вызов subclass; при отсутствии иммунитета callback выполняется один раз. Exact-state схемы не отменяют полезную регенерацию или десериализацию.

Эти выводы основаны на исходном коде и байткоде. Проверяющий не выдаёт их за собственный игровой прогон.

## Проверка четырёх точных state-transform планов

| Оригинальный JAR / метод | Проверенный контракт |
|---|---|
| TAN 9.2.0.171 `ThirstHandler.onPlayerTick(Player)` | static; player slot 0; 2× `IThirst.setThirst(I)V`, 1× `setHydration(F)V`, 1× `ThirstHelper.getThirst(Player)IThirst` |
| SanityDim 1.1.0 `SanityProcessor.addSanity(ISanity,float,ServerPlayer)` | static; player slot 2; ровно 1× `ISanity.setSanity(F)V` |
| SanityDim 1.1.0 `SanityProcessor.lambda$tickPlayer$3(ServerPlayer,ISanity)` | static; player slot 0; ровно 1× `ISanity.setSanity(F)V` |
| Mekanism 10.4.16.80 `DefaultRadiationEntity.update(LivingEntity)` | instance; affected entity slot 1; ровно 2× `GETFIELD radiation:D`, 1× `getScaledDoseSeverity(D)D` |

SanityDim действительно считает больший `getSanity()` большим безумием; положительная дельта использует `getNegMul`, а выбор наиболее безумного игрока ищет максимум.

## Доказательства и оставшиеся границы

Внешние review-артефакты: `<lab>/autonomous-compat-1.20.1/review/` — `firstaid-wrapper.javap.txt`, `sanity-processor.javap.txt`, `thirst-handler.javap.txt`, `mek-radiation.javap.txt`, `potion-item-srg.javap.txt`, `biomancy-potion-mixin.javap.txt`, `biomancy-cloud-mixin.javap.txt`, `effect-invoke-injection-targets.json`.

Поиск точных injection-target строк выполнен по верхнеуровневым class entries всех 616 JAR; он нашёл два Biomancy callers. Он не доказывает отсутствие динамических/coremod/nested-library конфликтов.

Общая защита HP не доказывает смысл произвольной private capability. Новая общая защита эффектов является **periodic-only**: мгновенный эффект неизвестного подкласса, изменяющий только приватное состояние и обходящий прежний audited callback, остаётся вне нового общего hook. Его обычный native hurt/direct-health путь может защищаться своими hooks, но это не эквивалентно отмене всего custom instant callback. Защита от всех 616 модов в игре, новые схемы capability и все сторонние mixin-комбинации здесь не заявлены.

На последнем прочитанном production-коде других подтверждённых P1/P2 не осталось. Это завершённый статический review; окончательная runtime-оценка зависит от native групп основного workflow.
