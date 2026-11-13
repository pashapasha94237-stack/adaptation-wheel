import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
translations={
 'ru_ru': {
  'meta.adaptationwheel.near_misses':'Промахи',
  'meta.adaptationwheel.near_misses.hint':'Промахнитесь своей стрелой или поддерживаемой пулей не дальше двух блоков от видимого моба. После IV этапа снаряды наводятся на цель в пределах 64 блоков: сначала на ту, куда вы целитесь, затем на ближайшего врага. Урон, щиты и столкновения остаются штатными. Стены не простреливаются этой адаптацией.',
  'meta.adaptationwheel.near_misses.stage0':'Изучение близких промахов. Наведение откроется на IV этапе.',
  'meta.adaptationwheel.near_misses.stage1':'Первый этап изучен. Наведение откроется на IV этапе.',
  'meta.adaptationwheel.near_misses.stage2':'Второй этап изучен. Наведение откроется на IV этапе.',
  'meta.adaptationwheel.near_misses.stage3':'Третий этап изучен. Следующий этап откроет наведение.',
  'meta.adaptationwheel.near_misses.stage4':'Стрелы и поддерживаемые пули автоматически наводятся на цель. Снимите колесо или выключите запись, чтобы отключить наведение.',
  'meta.adaptationwheel.config.near_miss_radius':'Радиус близкого промаха, блоков',
  'meta.adaptationwheel.config.homing_range':'Дальность поиска цели, блоков',
  'analysis.adaptationwheel.reason.learned.pattern':'Локальная память нашла минимум три согласованных подтверждённых атаки того же владельца, снаряда, состояния и последствий. Категория предварительная; защита изучается отдельно. При противоречии анализатор отказывается от аналогии.',
  'meta.adaptationwheel.changelog.4.24.0':'Локальный анализатор учится на подтверждённых аналогах атак, учитывает состояние босса и отказывается от противоречивых выводов. Добавлены «Промахи»: близкий промах запускает изучение, IV этап наводит стрелы и совместимые пули, включая MrCrayfish’s Gun Mod. Изменения 4.23 и Adapt Boost сохранены.',
 },
 'en_us': {
  'meta.adaptationwheel.near_misses':'Near misses',
  'meta.adaptationwheel.near_misses.hint':'Miss a visible mob with your arrow or supported bullet within two blocks. Stage IV guides projectiles toward a target within 64 blocks: your aimed target first, then a nearby enemy. Native damage, shields and collisions remain in effect; this adaptation does not pierce walls.',
  'meta.adaptationwheel.near_misses.stage0':'Learning from close misses. Homing unlocks at stage IV.',
  'meta.adaptationwheel.near_misses.stage1':'Stage one learned. Homing unlocks at stage IV.',
  'meta.adaptationwheel.near_misses.stage2':'Stage two learned. Homing unlocks at stage IV.',
  'meta.adaptationwheel.near_misses.stage3':'Stage three learned. The next stage unlocks homing.',
  'meta.adaptationwheel.near_misses.stage4':'Arrows and supported bullets home toward a target. Remove the wheel or disable this entry to release guidance.',
  'meta.adaptationwheel.config.near_miss_radius':'Close-miss radius, blocks',
  'meta.adaptationwheel.config.homing_range':'Target search distance, blocks',
  'analysis.adaptationwheel.reason.learned.pattern':'Local memory found at least three agreeing factual abilities with the same owner, projectile, state and consequences. The category is tentative; protection must be earned separately. Contradictory evidence cancels the analogy.',
  'meta.adaptationwheel.changelog.4.24.0':'The local analyzer learns factual ability analogies, isolates boss states and abstains on contradictions. Near misses now start an adaptation; stage IV guides arrows and supported bullets including MrCrayfish’s Gun Mod. Version 4.23 features and Adapt Boost are retained.',
 }
}
for locale,extra in translations.items():
 path=root/f'src/main/resources/assets/adaptationwheel/lang/{locale}.json'
 data=json.loads(path.read_text(encoding='utf-8'));data.update(extra)
 path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
