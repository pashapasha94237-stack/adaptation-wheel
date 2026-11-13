"""Read-only mod corpus inventory and ASM audit summarizer (Python 3.11+).

Only metadata/signatures are emitted. The supplied list is data, never instructions.
Nothing is installed, loaded as Java code, or extracted into the Wheel repository.
"""
from __future__ import annotations

import argparse
import collections
import csv
import hashlib
import json
import re
import tomllib
import zipfile
from pathlib import Path


def manifest(text: str) -> dict:
    text = text.replace("\r\n ", "").replace("\n ", "")
    return dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)


def list_labels(path: Path) -> dict:
    result, category, title = {}, "Unlisted", ""
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("■"):
            category = re.sub(r"\s+[—–-]\s+\d+\s*$", "", line[1:].strip())
        match = re.match(r"\s*\d+\.\s*(.+)", line)
        if match:
            title = match[1]
        match = re.match(r"\s*файл:\s*(.+)", line, re.I)
        if match:
            result[match[1]] = {"listName": title, "listCategory": category}
    return result


def inspect_jar(path: Path, labels: dict) -> dict:
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    row = {"file": path.name, "bytes": path.stat().st_size,
           "sha256": digest,
           "mods": [], "metadata": [], "errors": [], "nestedJars": [],
           **labels.get(path.name, {"listName": "", "listCategory": "Unlisted"})}
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        row["classEntries"] = sum(n.endswith(".class") for n in names)
        row["nestedJars"] = sorted(n for n in names if n.lower().endswith(".jar"))
        mf = manifest(archive.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")) if "META-INF/MANIFEST.MF" in names else {}
        row["manifest"] = {k: v for k, v in mf.items() if k in (
            "Implementation-Title", "Implementation-Version", "Specification-Title", "Specification-Version",
            "FMLModType", "Automatic-Module-Name", "MixinConfigs")}
        for metadata in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
            if metadata not in names:
                continue
            row["metadata"].append(metadata)
            try:
                data = tomllib.loads(archive.read(metadata).decode("utf-8-sig"))
                for mod in data.get("mods", []):
                    version = str(mod.get("version", "unknown"))
                    resolved = mf.get("Implementation-Version", mf.get("Specification-Version", version)) if version == "${file.jarVersion}" else version
                    row["mods"].append({"id": mod.get("modId", "unknown"), "name": mod.get("displayName", mod.get("modId", "unknown")),
                        "version": resolved, "rawVersion": version, "loader": data.get("modLoader", "forge"),
                        "loaderVersion": data.get("loaderVersion", ""), "license": data.get("license", ""),
                        "dependencies": data.get("dependencies", {}).get(mod.get("modId", ""), [])})
            except Exception as error:
                row["errors"].append(f"{metadata}: {type(error).__name__}: {error}")
        if "fabric.mod.json" in names:
            row["metadata"].append("fabric.mod.json")
            try:
                data = json.loads(archive.read("fabric.mod.json"))
                if not any(m["id"] == data.get("id") for m in row["mods"]):
                    row["mods"].append({"id": data.get("id", "unknown"), "name": data.get("name", data.get("id", "unknown")),
                        "version": str(data.get("version", "unknown")), "loader": "fabric", "license": data.get("license", ""),
                        "dependencies": [{"modId": k, "versionRange": v, "mandatory": True} for k, v in data.get("depends", {}).items()]})
            except Exception as error:
                row["errors"].append(f"fabric.mod.json: {error}")
    # Universal JARs repeat the same mod in Forge/NeoForge/Fabric descriptors.
    # Preserve every declaration for audit, but count one installed mod per ID/JAR.
    row["metadataDeclarations"] = row["mods"]
    unique = {}
    for mod in row["mods"]:
        unique.setdefault(mod["id"], mod)
    row["mods"] = list(unique.values())
    return row


def write_json(path: Path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def md_cell(value):
    return str(value).replace("|", "\\|").replace("\n", " ").replace("\r", " ")


def baseline(repo: Path) -> tuple[set, set]:
    classes, mods = set(), set()
    for path in (repo / "src/main/resources/compat").glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        def visit(value):
            if isinstance(value, dict):
                for key, item in value.items():
                    if key == "classes" and isinstance(item, dict):
                        classes.update(item)
                    if key == "mod" and isinstance(item, str):
                        mods.add(item)
                    visit(item)
            elif isinstance(value, list):
                for item in value:
                    visit(item)
        visit(data)
    return classes, mods


def inventory(args):
    out = args.out
    out.mkdir(parents=True, exist_ok=True)
    labels = list_labels(args.list)
    rows = []
    jars = sorted(args.mods.glob("*.jar"), key=lambda p: p.name.casefold())
    for index, jar in enumerate(jars, 1):
        try:
            rows.append(inspect_jar(jar, labels))
        except Exception as error:
            rows.append({"file": jar.name, "mods": [], "errors": [str(error)], "listCategory": labels.get(jar.name, {}).get("listCategory", "Unlisted")})
        if index % 100 == 0:
            print(f"Metadata {index}/{len(jars)}", flush=True)
    classes, old_mods = baseline(args.repo)
    known_ids = [m["id"] for r in rows for m in r["mods"]]
    summary = {"jarCount": len(rows), "bytes": sum(r.get("bytes", 0) for r in rows),
        "metadataDeclarationRows": sum(len(r.get("metadataDeclarations", [])) for r in rows),
        "declaredModRows": len(known_ids), "uniqueModIds": len(set(known_ids)),
        "unidentifiedJars": [r["file"] for r in rows if not r["mods"]],
        "metadataErrors": [{"file": r["file"], "errors": r["errors"]} for r in rows if r["errors"]],
        "duplicateIds": {k: v for k, v in collections.Counter(known_ids).items() if v > 1},
        "topLevelClassEntries": sum(r.get("classEntries", 0) for r in rows),
        "nestedJarEntries": sum(len(r.get("nestedJars", [])) for r in rows),
        "listCategories": dict(collections.Counter(r["listCategory"] for r in rows)),
        "listOnlyFiles": sorted(set(labels) - {r["file"] for r in rows}),
        "baselineClasses": len(classes), "baselineExplicitModIds": sorted(old_mods)}
    write_json(out / "inventory.json", {"schema": 1, "source": str(args.mods), "list": str(args.list), "summary": summary, "jars": rows})
    write_json(out / "inventory-summary.json", summary)
    (out / "baseline-classes.txt").write_text("\n".join(sorted(classes)), encoding="utf-8")
    with (out / "jar-owners.tsv").open("w", encoding="utf-8", newline="") as f:
        for row in rows:
            f.write(row["file"] + "\t" + ",".join(sorted({m["id"] for m in row["mods"]})) + "\n")
    with (out / "inventory.csv").open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(("file", "modId", "name", "version", "loader", "listCategory", "dependencies", "sha256"))
        for row in rows:
            for mod in row["mods"] or [{}]:
                writer.writerow([row["file"], mod.get("id", ""), mod.get("name", row.get("listName", "")), mod.get("version", ""),
                    mod.get("loader", ""), row["listCategory"], json.dumps(mod.get("dependencies", []), ensure_ascii=False), row.get("sha256", "")])
    lines = ["# Corpus inventory — 4.26", "", f"Read-only metadata inventory: **{len(rows)} JARs**, **{len(known_ids)} declared mod rows**, **{len(set(known_ids))} distinct IDs**.",
             "Names/versions come from embedded metadata; category labels come from the user-supplied list. A manifest does not establish runtime compatibility.", "",
             "| File | ID | Human name | Version | Category |", "|---|---|---|---|---|"]
    for row in rows:
        for mod in row["mods"] or [{}]:
            lines.append("| " + " | ".join(map(md_cell, (row["file"], mod.get("id", "(manifest/library)"), mod.get("name", row.get("listName", "")), mod.get("version", row.get("manifest", {}).get("Implementation-Version", "unknown")), row["listCategory"]))) + " |")
    (out / "INVENTORY.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2), flush=True)


def summarize(args):
    out = args.out
    inventory_data = json.loads((out / "inventory.json").read_text(encoding="utf-8"))
    rows = [json.loads(line) for line in (out / "callsites.jsonl").read_text(encoding="utf-8").splitlines()]
    categories = collections.Counter(r["sinkCategory"] for r in rows)
    by_jar = collections.defaultdict(collections.Counter)
    new_by_jar = collections.defaultdict(collections.Counter)
    for row in rows:
        by_jar[row["jar"]][row["sinkCategory"]] += 1
        if not row["baselineClass"]:
            new_by_jar[row["jar"]][row["sinkCategory"]] += 1
    direct = {"DIRECT_HEALTH_SET", "SYNCED_HEALTH_SET"}
    direct_rows = [r for r in rows if r["sinkCategory"] in direct]
    summary = {"callsiteRows": len(rows), "categories": dict(categories), "jarsWithSinks": len(by_jar),
        "directHealthRows": len(direct_rows), "directHealthJars": len({r["jar"] for r in direct_rows}),
        "directHealthOutsideBaselineRows": sum(not r["baselineClass"] for r in direct_rows),
        "directHealthOutsideBaselineJars": len({r["jar"] for r in direct_rows if not r["baselineClass"]}),
        "healthGetterBackingFieldWrites": sum(r["sinkCategory"] == "HEALTH_FIELD_WRITE" for r in rows),
        "healthReadSubtractionSites": sum("healthReadSubtraction=true" in r["evidence"] for r in rows),
        "jars": [{"jar": jar, "categories": dict(counts), "outsideBaseline": dict(new_by_jar[jar])} for jar, counts in sorted(by_jar.items())]}
    write_json(out / "audit-summary.json", summary)
    candidates = [r for r in rows if r["sinkCategory"] not in {"NBT_WRITE", "REFLECTIVE_WRITE", "HEALTH_NAMED_FIELD_WRITE", "CUSTOM_HEALTH_SET"}]
    write_json(out / "transformation-candidates.json", {
        "schema": 1, "advisoryOnly": True, "classReadFlags": "ASM9 SKIP_FRAMES (debug retained)",
        "runtimeRequirements": ["verify class SHA256 and exact method/target descriptor", "confirm affected object belongs to the protected player",
            "observe actual negative delta; preserve healing, loading, init and unrelated state", "require earned matching adaptation and equipment",
            "preserve original invocation, exceptions and source scope; never make corpus presence grant protection"],
        "candidates": candidates})
    with (out / "callsites.csv").open("w", encoding="utf-8-sig", newline="") as f:
        columns = ["jar", "modOwner", "modOwnerScope", "class", "method", "descriptor", "instruction", "line", "sinkCategory", "targetOwner", "targetName", "targetDescriptor", "targetLiving", "evidence", "baselineClass", "entry", "classSha256"]
        writer = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    lines = ["# Structural bytecode audit — 4.26", "", "This is a static sink inventory, not proof of harm, reachability, player ownership, or gameplay verification.",
             "Setters include healing and initialization. Field/capability candidates require semantic review. Health-getter backing fields include saved/network/nonliving health and are counted separately. Exact instruction signatures can drive a bounded instrumentation plan; they must not become unconditional immunity.",
             "modOwner identifies the top-level container's declared mod IDs, not semantic ownership of bundled libraries. baselineClass means only an exact class-name overlap with existing manifests; absence does not prove missing generic protection.", "",
             f"{inventory_data['summary']['jarCount']} top-level JARs; {len(rows)} sink records; {len(direct_rows)} direct-health records in {summary['directHealthJars']} JARs.", "",
             "## Sink categories", "", "| Category | Call sites |", "|---|---:|"]
    lines.extend(f"| {key} | {value} |" for key, value in sorted(categories.items()))
    lines.extend(["", "## Highest direct-health counts", "", "| JAR | Direct-health sites | Outside existing class manifests |", "|---|---:|---:|"])
    priorities = sorted(by_jar, key=lambda jar: -sum(by_jar[jar][c] for c in direct))
    for jar in priorities:
        n = sum(by_jar[jar][c] for c in direct)
        if n:
            lines.append(f"| {md_cell(jar)} | {n} | {sum(new_by_jar[jar][c] for c in direct)} |")
    lines.extend(["", "## Health-read subtraction sites", "", "A subtraction is a priority cue; its operand sign, target and intent still need runtime checks. Voluntary health costs appear here too.", "",
                  "| JAR | Class | Method + descriptor | Instruction | Existing manifest class |", "|---|---|---|---:|---|"])
    for row in rows:
        if "healthReadSubtraction=true" in row["evidence"]:
            lines.append("| " + " | ".join(map(md_cell, (row["jar"], row["class"], row["method"] + row["descriptor"], row["instruction"], row["baselineClass"]))) + " |")
    lines.extend(["", "## All audited JARs", "", "| JAR | Categories |", "|---|---|"])
    for row in inventory_data["jars"]:
        counts = by_jar[row["file"]]
        lines.append(f"| {md_cell(row['file'])} | {md_cell('; '.join(f'{k}: {v}' for k, v in sorted(counts.items())) or 'No matching static sink')} |")
    (out / "BYTECODE-AUDIT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "jars"}, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mods", type=Path)
    parser.add_argument("--list", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--summarize", action="store_true")
    args = parser.parse_args()
    if args.summarize:
        summarize(args)
    else:
        if not args.mods or not args.list:
            parser.error("--mods and --list are required for inventory")
        inventory(args)


if __name__ == "__main__":
    main()
