"""Regenerate the shipped offline catalog from the two ACSPL+ 3.12 manuals.

Only this maintenance tool needs Python, pypdf and pdfplumber. Ordinary plugin
builds read the checked-in JSON. PDF outlines establish symbol identity; text
extraction establishes descriptions, signatures and page references. Corrections
remain explicit in catalog_overrides.json rather than modifying generated data.
"""
from __future__ import annotations

import argparse
import bisect
import hashlib
import json
import re
from pathlib import Path

import pdfplumber
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = "ACSPL-Commands-Variables-Reference-Guide.pdf"
PROGRAMMER = "ACSPL-Programmers-Guide.pdf"
SYMBOL = r"#?[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)?"
LABEL = re.compile(rf"{SYMBOL}(?:(?:\s*[,/]\s*|\s*\.\.\.\s*){SYMBOL})*(?:\(\))?$")
HEADER = re.compile(r"^(\d+(?:\.\d+){2,})\s+(.+)$", re.M)
BOUNDARIES = {"Description", "Syntax", "Arguments", "Parameters", "Return Value",
              "Switches", "Command Options", "Comments", "Accessibility", "Tag",
              "Example", "Examples", "Error Conditions", "Related ACSPL+ Commands",
              "Related ACSPL+ Variables", "COM Library Methods and .NET Library Methods",
              "C Library Functions"}


def outlines(reader):
    def walk(items):
        for item in items:
            if isinstance(item, list):
                yield from walk(item)
            else:
                m = HEADER.fullmatch(item.title.strip())
                if m and m[1].split(".")[0] in {"2", "3", "4", "5"}:
                    label = re.sub(r"ENABLE\s+ALL", "ENABLEALL", m[2].strip())
                    if label == "MotionDuration Struct":
                        label = "MotionDuration"
                    yield m[1], label, reader.get_destination_page_number(item) + 1
    return list(walk(reader.outline))


def clean(text):
    lines = []
    for line in text.splitlines():
        if re.match(r"^(?:ACSPL\+ Commands & Variables Reference Guide|ACSPL\+ Programmer's Guide|"
                    r"Version 3\.12 \d+|\d+ Version 3\.12|[2345]\.\s+ACSPL\+)", line):
            continue
        lines.append(line.strip())
    return "\n".join(lines)


def section_field(body, heading, max_lines=10):
    lines = body.splitlines()
    for i, line in enumerate(lines):
        if line == heading or line.startswith(heading + " Comments"):
            result = []
            for value in lines[i + 1:]:
                if value == heading and not result:
                    continue
                if any(value == boundary or value.startswith(boundary + " Comments") for boundary in BOUNDARIES) or re.match(r"^(?:Table|Figure) \d", value) or HEADER.match(value):
                    break
                if value:
                    result.append(value)
                if len(result) == max_lines:
                    break
            return result
    return []


def extract(root):
    ref_path = root / REFERENCE
    reader = PdfReader(ref_path)
    for filename in [REFERENCE, PROGRAMMER]:
        if "3.12" not in (PdfReader(root / filename).pages[0].extract_text() or ""):
            raise ValueError(f"Expected the ACSPL+ 3.12 manual: {filename}")
    outline = outlines(reader)
    pages = {}
    rows = []
    positions = []
    symbol_sections = {n for n, label, _ in outline if LABEL.fullmatch(label)}
    with pdfplumber.open(ref_path) as pdf:
        for n in range(40, 811):
            page = pdf.pages[n - 1]
            pages[n] = clean(page.extract_text(x_tolerance=0.6) or "")
            for word in page.extract_words(x_tolerance=0.6):
                if word["text"] in symbol_sections:
                    positions.append((n, word["top"], word["text"]))
            for table in page.find_tables():
                for row, cells in zip(table.rows, table.extract(x_tolerance=0.6)):
                    left = page.crop((0, row.bbox[1], table.bbox[0], row.bbox[3])).extract_text(x_tolerance=0.6) or ""
                    left = " ".join(left.split())
                    cells = [" ".join((cell or "").split()) for cell in cells]
                    if not re.fullmatch(r"[/#][A-Za-z][A-Za-z0-9_]*", left) and cells and re.fullmatch(r"[/#][A-Za-z][A-Za-z0-9_]*", cells[0]):
                        left, cells = cells[0], cells[1:]
                    if re.fullmatch(r"[/#][A-Za-z][A-Za-z0-9_]*", left):
                        rows.append((n, row.bbox[1], left, cells))
            if n % 100 == 0:
                print(f"Read reference page {n}/810", flush=True)
    all_text = "\n".join(pages.values())
    headers = list(HEADER.finditer(all_text))
    sections = {}
    for i, match in enumerate(headers):
        sections.setdefault(match[1], all_text[match.end():headers[i + 1].start() if i + 1 < len(headers) else len(all_text)])

    entries = {}
    covered, excluded = [], []

    def add(name, kind, page, section, body, description=None, owner=None):
        description = description or " ".join(section_field(body, "Description", 5))
        if not description and body:
            # Several entries start directly with a paragraph (no Description label).
            paragraph = []
            for line in body.splitlines():
                if line in BOUNDARIES or HEADER.match(line) or line.startswith(("Table ", "Figure ")):
                    break
                if line:
                    paragraph.append(line)
                if len(paragraph) == 5:
                    break
            description = " ".join(paragraph)
        signatures = section_field(body, "Syntax", 6)
        signatures = [s for s in signatures if not s.startswith((">", "Where", "where", "See the ", "functions, and variables"))]
        source = {"manual": REFERENCE, "section": section, "page": page}
        key = (name, kind)
        if key not in entries:
            entries[key] = dict(name=name, kind=kind, signatures=[], description=description,
                                parameters=[], switches=[], sources=[], array=False, owners=[])
        entry = entries[key]
        for s in signatures:
            if s not in entry["signatures"]:
                entry["signatures"].append(s)
        for p in section_field(body, "Arguments", 18):
            if p not in entry["parameters"]:
                entry["parameters"].append(p)
        if source not in entry["sources"]:
            entry["sources"].append(source)
        if not entry["description"]:
            entry["description"] = description
        if kind == "variable":
            entry["array"] |= bool(re.search(r"\barray\b", description, re.I))
        if owner and owner not in entry["owners"]:
            entry["owners"].append(owner)
        # Restrict switches to the explicit switch section, excluding C library text.
        switch_lines = section_field(body, "Switches", 100)
        last = None
        preceding = []
        for line in switch_lines:
            m = re.match(r"^/([A-Za-z]+)\b\s*(.*)$", line)
            if m:
                value = {"name": m[1], "description": " ".join(preceding + ([m[2]] if m[2] else []))}
                preceding = []
                existing = next((s for s in entry["switches"] if s["name"] == value["name"]), None)
                last = existing or value
                if not existing:
                    entry["switches"].append(value)
            elif last:
                last["description"] = (last["description"] + " " + line).strip()
            else:
                preceding.append(line)

    for number, label, page in outline:
        if not LABEL.fullmatch(label):
            excluded.append(dict(section=number, title=label, page=page))
            continue
        names = re.findall(SYMBOL, label)
        kind = {"2": "command", "3": "variable", "4": "function", "5": "type"}[number[0]]
        if number.startswith("5.1.1."):
            kind = "function"
            names = ["LCI." + name for name in names]
        elif number.startswith("5.1.2."):
            kind = "variable"
            names = ["LCI." + name for name in names]
        body = sections.get(number, "")
        for name in names:
            actual_kind = "constant" if name.startswith("#") else kind
            if name in {"END", "ENDS", "RET", "ELSE", "ELSEIF"}:
                actual_kind = "keyword"
            if name.startswith("LCI."):
                actual_kind = "function" if "Syntax" in body else "variable"
            add(name, actual_kind, page, number, body)
        covered.append(dict(section=number, title=label, page=page, symbols=names))

    # The label column is unruled and outside pdfplumber's detected table bbox.
    # Associate it with the SAME row by coordinates rather than linearized text.
    positions.sort()
    by_section = {row["section"]: row["symbols"] for row in covered}
    for entry in entries.values():
        entry["switches"] = []
    for page, top, label, cells in rows:
        pos = bisect.bisect_right(positions, (page, top, "\uffff")) - 1
        if pos < 0:
            continue
        section = positions[pos][2]
        owners = by_section.get(section, [])
        if label.startswith("/"):
            for owner in owners:
                entry = entries.get((owner, "command")) or entries.get((owner, "function"))
                if entry and not any(s["name"] == label[1:] for s in entry["switches"]):
                    entry["switches"].append({"name": label[1:], "description": " ".join(cells)})
        elif label.startswith("#") and cells and re.fullmatch(r"\d+", cells[0]):
            for owner in owners:
                add(label, "constant", page, section, "", f"{owner} bit {cells[0]}. {' '.join(cells[1:])}", owner)

    # Alphabetical index rows catch symbols omitted from PDF bookmarks.
    for kind, lo, hi in [("command", 40, 44), ("variable", 210, 221), ("function", 539, 544)]:
        for page in range(lo, hi + 1):
            for line in pages[page].splitlines():
                m = re.match(rf"^({SYMBOL})\s+(.+)$", line)
                if not m or m[1] in {"Name", "Command", "Function", "Description", "ACSPL", "Version"}:
                    continue
                name = m[1]
                if not (name.isupper() or name in {"AxListAsMask", "BPTPCalc"}):
                    continue
                if not any(key[0] == name for key in entries):
                    add(name, kind, page, "Alphabetical index", "", m[2])
                elif (name, kind) in entries and not entries[(name, kind)]["description"]:
                    entries[(name, kind)]["description"] = m[2]

    overrides = json.loads((ROOT / "tools/catalog_overrides.json").read_text(encoding="utf-8"))
    table_entries = json.loads((ROOT / "tools/catalog_standard_structures.json").read_text(encoding="utf-8"))
    overrides.update(table_entries)
    for name, correction in overrides.items():
        kind = correction.get("kind", "keyword")
        # The alphabetical index occasionally lists a variable among functions.
        # A reviewed category correction keeps the authoritative section data.
        previous = [entries.pop(key) for key in list(entries) if key[0] == name]
        entry = previous[0] if previous else dict(name=name, kind=kind, signatures=[],
            description="", parameters=[], switches=[], sources=[], array=False, owners=[])
        for duplicate in previous[1:]:
            for field in ["signatures", "parameters", "switches", "sources", "owners"]:
                for value in duplicate[field]:
                    if value not in entry[field]:
                        entry[field].append(value)
        entries[(name, kind)] = entry
        entry.update(correction)
        if not entry["sources"]:
            entry["sources"] = [{"manual": PROGRAMMER, "section": "3. ACSPL+ Overview", "page": 45}]
    result = sorted(entries.values(), key=lambda x: (x["name"].casefold(), x["kind"]))
    for entry in result:
        if not entry["signatures"]:
            entry["signatures"] = [entry["name"]]
        if not entry["description"]:
            entry["description"] = f"ACSPL+ {entry['kind']}; see the referenced manual section."
    return {"languageVersion": "3.12", "sources": [{"file": f, "sha256": hashlib.sha256((root / f).read_bytes()).hexdigest(),
                "pages": len(PdfReader(root / f).pages)} for f in [REFERENCE, PROGRAMMER]], "entries": result}, {
            "coveredSymbolSections": covered, "excludedNonSymbolSections": excluded,
            "reviewedTableSymbols": list(table_entries),
            "entryCounts": {kind: sum(e["kind"] == kind for e in result) for kind in sorted({e["kind"] for e in result})}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf-dir", type=Path, default=ROOT)
    args = parser.parse_args()
    catalog, report = extract(args.pdf_dir)
    output = ROOT / "src/main/resources/catalog/acspl-3.12.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report_file = ROOT / "docs/catalog-coverage.json"
    report_file.parent.mkdir(parents=True, exist_ok=True)
    report_file.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(report["entryCounts"], flush=True)
