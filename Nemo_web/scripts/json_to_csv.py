#!/usr/bin/env python3
"""
Convert grammar JSON files to CSV (UTF-8).

Usage:
  python json_to_csv.py --input-dir "path/to/questions" --output out.csv
  python json_to_csv.py --input-dir "path/to/questions" --out-dir csvs --split

By default produces a single combined CSV. Use --split to create one CSV per JSON file.

CSV header:
id,targetGrammarId,targetUsageIndex,type,question,options,correctIndex,explanation

`options` is a JSON array string (escaped) so Postgres jsonb COPY can parse it.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path
from typing import List, Dict, Any


HEADER = [
    "id",
    "targetGrammarId",
    "targetUsageIndex",
    "type",
    "question",
    "options",
    "correctIndex",
    "explanation",
]


def load_json_file(path: Path) -> List[Dict[str, Any]]:
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError(f"Expected top-level list in {path}")
    return data


def row_from_item(item: Dict[str, Any]) -> List[str]:
    # Ensure all fields exist; options becomes JSON string
    opts = item.get("options", [])
    options_json = json.dumps(opts, ensure_ascii=False)
    return [
        str(item.get("id", "")),
        str(item.get("targetGrammarId", "")),
        str(item.get("targetUsageIndex", "")),
        str(item.get("type", "")),
        str(item.get("question", "")),
        options_json,
        str(item.get("correctIndex", "")),
        str(item.get("explanation", "")),
    ]


def write_csv(path: Path, rows: List[List[str]]):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(HEADER)
        writer.writerows(rows)


def convert_all(input_dir: Path, out_path: Path):
    rows: List[List[str]] = []
    for p in sorted(input_dir.glob("*.json")):
        items = load_json_file(p)
        for it in items:
            rows.append(row_from_item(it))
    write_csv(out_path, rows)


def convert_split(input_dir: Path, out_dir: Path):
    out_dir.mkdir(parents=True, exist_ok=True)
    for p in sorted(input_dir.glob("*.json")):
        items = load_json_file(p)
        rows = [row_from_item(it) for it in items]
        out_name = out_dir / (p.stem + ".csv")
        write_csv(out_name, rows)


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert grammar question JSON to CSV (UTF-8)")
    parser.add_argument("--input-dir", required=True, help="Directory with JSON files")
    parser.add_argument("--output", help="Output CSV file for combined output (default: combined.csv)")
    parser.add_argument("--out-dir", help="Output directory for split CSVs (used with --split)")
    parser.add_argument("--split", action="store_true", help="Create one CSV per JSON file")
    args = parser.parse_args()

    input_dir = Path(args.input_dir)
    if not input_dir.exists():
        raise SystemExit(f"Input directory not found: {input_dir}")

    if args.split:
        out_dir = Path(args.out_dir) if args.out_dir else Path("./csv_output")
        convert_split(input_dir, out_dir)
        print(f"Wrote split CSVs to {out_dir}")
    else:
        out_file = Path(args.output) if args.output else Path("./combined_questions.csv")
        convert_all(input_dir, out_file)
        print(f"Wrote combined CSV to {out_file}")


if __name__ == "__main__":
    main()
