#!/usr/bin/env python3
"""Read-only custom-state inventory. Candidate names are NOT permission to mutate a state.

Only class metadata, member descriptors and symbolic call references are reported. No
decompiled implementation is written into the mod source tree. The optional report
must live in a separate reverse-engineering lab, not this repository.
"""
import argparse
import json
from pathlib import Path
import re
import struct
import zipfile

STATE = re.compile(r"thirst|hydrat|sanity|radiat|oxygen|stamina|exhaust|trauma|temperature|mana|blood|diet|needs|soul", re.I)
CONTAINER = re.compile(r"capability|capabilities|data|handler|processor|manager|tracker|player|stats|thirst|temperature|radiation|sanity", re.I)
SELECTED = ("ToughAsNails", "sanitydim", "dragonsurvival", "Mekanism-", "NuclearCraft-", "ad_astra-", "Vampirism-", "diet-", "ParCool-", "Paraglider-", "BloodMagic-", "irons_spellbooks-")

class Reader:
    def __init__(self, data): self.data, self.at = data, 0
    def take(self, n):
        value = self.data[self.at:self.at+n]
        if len(value) != n: raise ValueError("Truncated class")
        self.at += n
        return value
    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack(">H", self.take(2))[0]
    def u4(self): return struct.unpack(">I", self.take(4))[0]

def metadata(data):
    r = Reader(data)
    if r.u4() != 0xCAFEBABE: raise ValueError("Not a class")
    r.u2(); major = r.u2(); size = r.u2(); cp = [None] * size
    i = 1
    while i < size:
        tag = r.u1()
        if tag == 1: cp[i] = (tag, r.take(r.u2()).decode("utf-8", "replace"))
        elif tag in (3, 4): cp[i] = (tag, r.take(4).hex())
        elif tag in (5, 6): cp[i] = (tag, r.take(8).hex()); i += 1
        elif tag in (7, 8, 16, 19, 20): cp[i] = (tag, r.u2())
        elif tag in (9, 10, 11, 12, 17, 18): cp[i] = (tag, r.u2(), r.u2())
        elif tag == 15: cp[i] = (tag, r.u1(), r.u2())
        else: raise ValueError("Unknown constant tag " + str(tag))
        i += 1
    def text(n): return cp[n][1] if cp[n] and cp[n][0] == 1 else ""
    def owner(n): return text(cp[n][1])
    def attrs():
        for _ in range(r.u2()): r.u2(); r.take(r.u4())
    access, this, superclass = r.u2(), r.u2(), r.u2()
    for _ in range(r.u2()): r.u2()
    def members():
        rows = []
        for _ in range(r.u2()):
            flags, name, desc = r.u2(), text(r.u2()), text(r.u2())
            rows.append({"name": name, "descriptor": desc, "access": flags})
            attrs()
        return rows
    fields, methods = members(), members()
    refs = []
    for row in cp[1:]:
        if row and row[0] in (9, 10, 11):
            nt = cp[row[2]]
            refs.append({"owner": owner(row[1]), "name": text(nt[1]), "descriptor": text(nt[2]), "kind": row[0]})
    strings = [text(row[1]) for row in cp[1:] if row and row[0] == 8]
    return {"class": owner(this), "super": owner(superclass) if superclass else "", "major": major,
            "fields": fields, "methods": methods, "references": refs, "strings": strings}

def audit(folder):
    output = {"schema": 1, "policy": "inventory-only; names never establish harmfulness or authorize a setter hook", "jars": []}
    for jar in sorted(folder.glob("*.jar")):
        if not jar.name.startswith(SELECTED): continue
        rows, errors = [], []
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.infolist():
                if not entry.filename.endswith(".class") or not CONTAINER.search(entry.filename): continue
                if entry.file_size > 4_000_000: continue
                try: row = metadata(archive.read(entry))
                except (ValueError, struct.error, IndexError) as exc:
                    errors.append({"class": entry.filename, "error": str(exc)}); continue
                selected_fields = [f for f in row["fields"] if STATE.search(f["name"])]
                selected_methods = [m for m in row["methods"] if STATE.search(m["name"])]
                selected_refs = [m for m in row["references"] if STATE.search(m["name"]) or m["name"] in ("m_6469_", "m_21153_", "hurt", "setHealth")]
                selected_strings = [s for s in row["strings"] if len(s) < 160 and STATE.search(s)]
                if not (selected_fields or selected_methods): continue
                rows.append({"class": row["class"], "super": row["super"], "fields": selected_fields,
                             "methods": selected_methods, "references": selected_refs, "strings": selected_strings})
        output["jars"].append({"name": jar.name, "candidates": rows, "parseErrors": errors})
    return output

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mods", type=Path)
    p.add_argument("--report", type=Path, required=True)
    args = p.parse_args()
    repo = Path(__file__).resolve().parents[1]
    if args.report.resolve().is_relative_to(repo): p.error("Write private audit evidence outside the repository")
    result = audit(args.mods)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for jar in result["jars"]:
        print(f"{jar['name']}: {len(jar['candidates'])} candidate classes; {len(jar['parseErrors'])} parse errors")
    print(f"Report: {args.report}")

if __name__ == "__main__": main()
