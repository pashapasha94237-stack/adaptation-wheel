import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/adaptationwheel/lang'
ru={
'world_border':('Граница мира','Подойдите к общей границе. После четвёртого этапа идите вперёд к ней или нажмите «Пройти границу». Проход личный, область снаружи ограничена безопасным расстоянием.'),
'notification_noise':('Информационный шум','Повторы безопасных системных уведомлений запускают изучение. Личные сообщения и предупреждения об опасности не скрываются. На четвёртом этапе повторы заменяются счётчиком.'),
'inventory_pressure':('Переполненный инвентарь','Длительная нехватка места запускает адаптацию. Со второго этапа совместимые стопки основного инвентаря уплотняются без удаления вещей. На четвёртом открывается карман на девять ячеек.'),
'silent_world':('Игра без звука','Отключите общую громкость Minecraft. Настоящие близкие звуки запускают адаптацию. Постепенно появляются направления, подписи и высота источника; сущности вне звуковых событий не сканируются.'),
'memory_pressure':('Нехватка памяти','Десять секунд устойчиво высокой загрузки памяти Java запускают адаптацию. Принудительный сборщик мусора не вызывается.'),
'low_tps':('Низкий TPS','Длительное замедление серверных тиков запускает адаптацию. После четвёртого этапа колесо реже сериализует прогресс и объединяет служебные обновления. Это оптимизация работы колеса, а не гарантия 20 TPS всей сборки.'),
'defeat_memory':('Память собственного поражения','После смерти сохраняются последние события боя и короткий след движения. Первый этап показывает след рядом с местом поражения. Четвёртый предупреждает о повторении знакомого начала опасной комбинации. Уклоняться нужно самому.')}
en={
'world_border':('World border','Approach the shared border. After stage four, walk toward it or choose Cross border. Access is personal and the outside area has a safe reach limit.'),
'notification_noise':('Notification noise','Exact repeats of safe system messages start adaptation. Player chat and danger warnings are never hidden. Stage four groups repeats into a counter.'),
'inventory_pressure':('Inventory pressure','A nearly full inventory starts adaptation. Stage two compacts compatible main-inventory stacks without deleting items. Stage four unlocks nine wheel pocket slots.'),
'silent_world':('Silent world','Mute Minecraft master volume. Real nearby sounds start adaptation. Direction, captions and relative height unlock progressively; no entities are scanned outside sound events.'),
'memory_pressure':('Memory pressure','Ten seconds of sustained high Java heap use starts adaptation. System.gc is never forced.'),
'low_tps':('Low server TPS','Sustained slow server ticks start adaptation. Stage four batches wheel state persistence and service updates. It optimizes this mod, not every workload on the server.'),
'defeat_memory':('Memory of defeat','Death preserves recent combat events and a short movement trail. Stage one shows the trail near the death site. Stage four warns when a familiar dangerous combo prefix repeats. Dodge manually.')}
extra_ru={
'progress':'Этап %s/4 · изучение механики',
'border.cross':'Пройти границу / вернуться',
'border.no_safe_exit':'Рядом нет безопасного места для прохода.',
'pocket.title':'Карман колеса',
'pocket.open':'Открыть карман · 9 ячеек',
'pocket.compact':'Уплотнить совместимые стопки',
'sound.steps':'Шаги','sound.shot':'Выстрел','sound.danger':'Опасный звук',
'defeat.warning':'Знакомое начало опасной комбинации — уклоняйся!',
'defeat_ghosts':'Показывать призрачный след поражения'}
extra_en={
'progress':'Stage %s/4 · learning mechanic','border.cross':'Cross border / return',
'border.no_safe_exit':'No safe crossing destination nearby.','pocket.title':'Wheel pocket',
'pocket.open':'Open pocket · 9 slots','pocket.compact':'Compact compatible stacks',
'sound.steps':'Footsteps','sound.shot':'Shot','sound.danger':'Danger sound',
'defeat.warning':'Familiar dangerous combo — dodge!',
'defeat_ghosts':'Show ghost trail of defeat'}
for language,items,extra in [('ru_ru',ru,extra_ru),('en_us',en,extra_en)]:
    file=root/(language+'.json');data=json.loads(file.read_text(encoding='utf-8'))
    for key,(name,hint) in items.items():
        prefix='meta.adaptationwheel.'+key;data[prefix]=name;data[prefix+'.hint']=hint
        for stage in range(5):data[prefix+'.stage'+str(stage)]=('Изучено этапов: '+str(stage)+'/4') if language=='ru_ru' else ('Stages learned: '+str(stage)+'/4')
    data.update({'meta.adaptationwheel.'+k:v for k,v in extra.items()})
    file.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(language,len(data))
