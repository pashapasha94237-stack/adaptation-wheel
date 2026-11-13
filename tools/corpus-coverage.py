"""Record exact-JAR runtime coverage; a loaded mod is never an attack-proof claim."""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
from pathlib import Path


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path, required=True)
    parser.add_argument('--probe', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    inventory = json.loads(args.inventory.read_text(encoding='utf-8-sig'))
    records = json.loads(args.evidence.read_text(encoding='utf-8-sig'))['records']
    jars = inventory['jars']
    by_hash = {row['sha256'].lower(): row for row in jars}
    seen: dict[str, list[dict]] = {}
    run_rows = []
    for record in records:
        run = args.probe / record['run']
        if run.parent.resolve() != args.probe.resolve():
            raise ValueError('Runtime evidence must name a direct isolated probe directory')
        log = run / 'logs/latest.log'
        text = log.read_text(encoding='utf-8', errors='replace')
        if 'MULTIMOD_QA PASS:' not in text or 'MULTIMOD_QA FAIL' in text:
            raise ValueError(f'No completed successful native run: {run.name}')
        for marker in record.get('requiredMarkers', []):
            if marker not in text:
                raise ValueError(f'Missing native assertion marker {marker}: {run.name}')
        case_mods = {case['mod'] for case in record.get('cases', [])}
        matched = []
        loaded_ids = set()
        for jar in sorted((run / 'mods').glob('*.jar')):
            row = by_hash.get(digest(jar))
            if row is None:
                continue  # A different version, translation, or synthetic QA mod is not this corpus JAR.
            ids = {mod['id'] for mod in row['mods']}
            loaded_ids.update(ids)
            cases = [case for case in record.get('cases', []) if case['mod'] in ids]
            proof = {'release': record['release'], 'run': run.name,
                     'level': 'native-defense-tested' if cases else 'runtime-loaded',
                     'cases': cases, 'log': str(log)}
            seen.setdefault(row['sha256'], []).append(proof)
            matched.append(row['file'])
        # Never silently attach a defense claim to a different version of a mod.
        run_rows.append({'run': run.name, 'corpusJars': len(matched),
                         'unmatchedCaseMods': sorted(case_mods - loaded_ids)})
    rows = []
    for jar in jars:
        evidence = seen.get(jar['sha256'], [])
        level = ('native-defense-tested' if any(e['level'] == 'native-defense-tested' for e in evidence)
                 else 'runtime-loaded' if evidence else 'static-audited')
        rows.append({'file': jar['file'], 'sha256': jar['sha256'],
                     'mods': [{'id': mod['id'], 'name': mod['name'], 'version': mod['version']}
                              for mod in jar['mods']], 'level': level, 'evidence': evidence})
    summary = {level: sum(row['level'] == level for row in rows)
               for level in ['native-defense-tested', 'runtime-loaded', 'static-audited']}
    args.output.mkdir(parents=True, exist_ok=True)
    output = {'schema': 1, 'meaning': 'Selected native cases only; no exhaustive compatibility claim.',
              'summary': summary, 'runs': run_rows, 'jars': rows}
    (args.output / 'coverage.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    with (args.output / 'coverage.csv').open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.writer(stream)
        writer.writerow(['file', 'modIds', 'versions', 'coverage', 'nativeCases', 'sha256'])
        for row in rows:
            writer.writerow([row['file'], '; '.join(m['id'] for m in row['mods']),
                             '; '.join(m['version'] for m in row['mods']), row['level'],
                             '; '.join(sorted({c['case'] for e in row['evidence'] for c in e['cases']})), row['sha256']])
    lines = ['# Проверка конкретных файлов из корпуса 616 JAR', '',
             'Здесь совпадение определяется SHA-256. Другая версия того же мода не засчитывается.',
             '`native-defense-tested` означает конкретные перечисленные сценарии, а не все способности мода.',
             '`runtime-loaded` означает успешный запуск с проверками, без заявления о его собственных атаках.',
             '`static-audited` означает только проведённый статический анализ.', '',
             f"Итог: нативные защитные сценарии — {summary['native-defense-tested']} JAR; "
             f"дополнительно загружены — {summary['runtime-loaded']}; "
             f"пока только статически проверены — {summary['static-audited']}.", '',
             '| JAR | Уровень | Проверенные нативные сценарии |', '|---|---|---|']
    for row in rows:
        cases = sorted({c['case'] for e in row['evidence'] for c in e['cases']})
        lines.append(f"| {row['file'].replace('|', '/')} | {row['level']} | {'; '.join(cases)} |")
    (args.output / 'COVERAGE.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(json.dumps({'status': 'PASS', 'summary': summary, 'runs': run_rows}, ensure_ascii=False))


if __name__ == '__main__':
    main()
