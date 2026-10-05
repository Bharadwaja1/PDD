#!/usr/bin/env python3
"""Create auditable HTML/XLSX reports from real JUnit or JSON result files."""

from __future__ import annotations

import argparse
import html
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill


def junit_rows(path: Path):
    root = ET.parse(path).getroot()
    if root.tag not in {"testsuite", "testsuites"}:
        rows = []
        for alert in root.findall(".//alertitem"):
            risk = alert.findtext("riskdesc", "Unknown")
            rows.append({
                "id": alert.findtext("pluginid", "ZAP"),
                "category": alert.findtext("alert", "DAST finding"),
                "status": "FAILED" if not risk.startswith(("Informational", "Low")) else "MEASURED",
                "duration_seconds": "",
                "details": f"{risk}: {alert.findtext('desc', '')}",
            })
        if not rows:
            rows.append({"id": "ZAP", "category": "DAST", "status": "PASSED", "duration_seconds": "", "details": "The ZAP XML report contained no alerts."})
        return rows
    suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
    rows = []
    for suite in suites:
        for case in suite.findall("testcase"):
            failure = case.find("failure")
            error = case.find("error")
            skipped = case.find("skipped")
            node = failure if failure is not None else error if error is not None else skipped
            status = "FAILED" if failure is not None or error is not None else "SKIPPED" if skipped is not None else "PASSED"
            rows.append({
                "id": case.get("name", "unknown"),
                "category": case.get("classname", "test"),
                "status": status,
                "duration_seconds": case.get("time", "0"),
                "details": (node.get("message", "") if node is not None else ""),
            })
    return rows


def json_rows(path: Path):
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data.get("results"), list):
        return data["results"]
    metrics = data.get("metrics", data)
    rows = []
    for name, value in metrics.items():
        values = value.get("values", value) if isinstance(value, dict) else value
        rows.append({"id": name, "category": "metric", "status": "MEASURED", "duration_seconds": "", "details": json.dumps(values, sort_keys=True)})
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite", required=True)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--basename", required=True)
    args = parser.parse_args()

    if not args.input.is_file():
        raise SystemExit(f"Result input does not exist: {args.input}")
    rows = junit_rows(args.input) if args.input.suffix == ".xml" else json_rows(args.input)
    if not rows:
        raise SystemExit(f"No results found in {args.input}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    counts = {status: sum(r.get("status") == status for r in rows) for status in ("PASSED", "FAILED", "SKIPPED", "MEASURED")}

    wb = Workbook()
    ws = wb.active
    ws.title = "Results"
    headers = ["ID", "Category", "Status", "Duration (seconds)", "Details"]
    ws.append(headers)
    for cell in ws[1]:
        cell.font = Font(bold=True, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor="1F4E78")
    for row in rows:
        ws.append([row.get("id", ""), row.get("category", ""), row.get("status", ""), row.get("duration_seconds", ""), row.get("details", "")])
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = ws.dimensions
    for width, column in zip((38, 30, 14, 20, 80), "ABCDE"):
        ws.column_dimensions[column].width = width
    summary = wb.create_sheet("Summary")
    summary.append(["Suite", args.suite])
    summary.append(["Source", str(args.input)])
    summary.append(["Total records", len(rows)])
    for status, count in counts.items():
        summary.append([status.title(), count])
    wb.save(args.output_dir / f"{args.basename}.xlsx")

    body = "\n".join(
        "<tr>" + "".join(f"<td>{html.escape(str(row.get(key, '')))}</td>" for key in ("id", "category", "status", "duration_seconds", "details")) + "</tr>"
        for row in rows
    )
    page = f"""<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>{html.escape(args.suite)}</title>
<style>body{{font:14px system-ui;margin:2rem;color:#17202a}}table{{border-collapse:collapse;width:100%}}th,td{{border:1px solid #ccd1d1;padding:.45rem;text-align:left}}th{{background:#1f4e78;color:white}}tr:nth-child(even){{background:#f4f6f7}}.summary{{display:flex;gap:1.5rem;margin:1rem 0}}</style></head>
<body><h1>{html.escape(args.suite)}</h1><p>Generated from <code>{html.escape(str(args.input))}</code>. Results are not inferred or fabricated.</p>
<div class=\"summary\"><b>Total: {len(rows)}</b>{''.join(f'<span>{k.title()}: {v}</span>' for k, v in counts.items())}</div>
<table><thead><tr>{''.join(f'<th>{h}</th>' for h in headers)}</tr></thead><tbody>{body}</tbody></table></body></html>"""
    (args.output_dir / f"{args.basename}.html").write_text(page, encoding="utf-8")


if __name__ == "__main__":
    main()
