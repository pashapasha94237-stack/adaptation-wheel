"""Keep the local analyzer's evidence wording consistent in both bundled locales."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/adaptationwheel/lang'
updates = {
    'ru_ru': {
        'context': 'Дистанция: %s · связанные отрицательные эффекты: %s',
        'protection': 'Проверок без потери HP: %s · возможных обходов: %s · наибольшая потеря HP: %s',
        'leak_explained': 'При ожидаемой полной защите замечена потеря HP, в том числе перед обычным лечением. Это возможный обход, а не доказательство его исправления. Неоднозначные окна и исчезновение только поглощения не считаются подтверждённой потерей HP от атаки.',
        'interval': 'Устойчивый интервал этого источника в текущем состоянии: около %s с',
        'next': 'Если знакомая цепочка повторится, далее возможно: %s',
    },
    'en_us': {
        'context': 'Range: %s · attributed harmful effects: %s',
        'protection': 'Checks without HP loss: %s · possible bypasses: %s · largest HP loss: %s',
        'leak_explained': 'HP loss was observed despite expected full protection, including before native healing. This is a possible bypass, not proof of a fix. Ambiguous windows and absorption-only expiry are not treated as confirmed attack HP loss.',
        'interval': 'Stable interval for this source in the current state: about %s s',
        'next': 'If the familiar chain repeats, the next event may be: %s',
    },
}
for locale, values in updates.items():
    path = root / (locale + '.json')
    data = json.loads(path.read_text(encoding='utf-8'))
    data.update({'analysis.adaptationwheel.' + key: value for key, value in values.items()})
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
