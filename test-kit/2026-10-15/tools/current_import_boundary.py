#!/usr/bin/env python3
"""Probe current Excel import boundaries in the isolated 8182/3317 environment.

This script never confirms a preview that unexpectedly succeeds.
"""
import datetime
import hashlib
import io
import json
import pathlib
import subprocess
import urllib.error
import urllib.request

import openpyxl

ROOT = pathlib.Path(__file__).resolve().parents[1]
SEED = ROOT / "evidence/runs/2026-11-02-positive-chain"
OUT = ROOT / ("evidence/runs/2026-11-02-import-boundary-" + datetime.datetime.now().strftime("%H%M%S"))
OUT.mkdir(parents=True, exist_ok=True)
BASE = "http://127.0.0.1:8182/api/v1"


def request(path, method="GET", data=None, token=None, content_type="application/json"):
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def sql(query):
    assert query.startswith("SELECT COUNT(*) FROM ")
    result = subprocess.run(
        ["docker", "exec", "-i", "mrp-testkit-mysql", "sh", "-c",
         'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" mrp_testkit'],
        input=query, capture_output=True, text=True, check=True)
    return int(result.stdout.strip())


def upload(path, blob, filename, token):
    boundary = "----MRPBoundary20261102"
    prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
              + filename + "\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n")
    body = prefix.encode() + blob + ("\r\n--" + boundary + "--\r\n").encode()
    return request(path, "POST", body, token, "multipart/form-data; boundary=" + boundary)


health = urllib.request.urlopen("http://127.0.0.1:8182/actuator/health", timeout=5)
assert health.status == 200, "isolated API is not healthy"
assert subprocess.run(["docker", "inspect", "mrp-testkit-mysql"],
                      capture_output=True).returncode == 0
_, auth = request("/auth/login", "POST", json.dumps({"username": "admin", "password": "admin123"}).encode())
token = auth["data"]["token"]
tables = ("forecast_version", "forecast_detail", "inventory_snapshot", "inventory_item")
before = {table: sql("SELECT COUNT(*) FROM " + table) for table in tables}


def forecast():
    wb = openpyxl.load_workbook(SEED / "forecast-current.xlsx")
    wb.active.delete_rows(4, wb.active.max_row)
    return wb


def inventory():
    wb = openpyxl.load_workbook(SEED / "inventory-current.xlsx")
    sheet = wb.active
    rows = [list(row) for row in sheet.values]
    chosen = [next(row for row in rows[2:] if row[0] == wh) for wh in ("UAT_APK", "UAT_YH")]
    sheet.delete_rows(3, sheet.max_row)
    for row in chosen:
        sheet.append(row)
    return wb


cases = [
    ("forecast_empty_factory", forecast, lambda s: setattr(s.cell(3, 1), "value", None), "/forecast-imports"),
    ("forecast_unknown_factory", forecast, lambda s: setattr(s.cell(3, 1), "value", "NO_FACTORY"), "/forecast-imports"),
    ("forecast_duplicate_source_row", forecast, lambda s: s.append(list(s[3][c].value for c in range(0, 12))), "/forecast-imports"),
    ("forecast_non_numeric", forecast, lambda s: setattr(s.cell(3, 6), "value", "abc"), "/forecast-imports"),
    ("forecast_empty_data", forecast, lambda s: s.delete_rows(3, s.max_row), "/forecast-imports"),
    ("inventory_empty_warehouse", inventory, lambda s: setattr(s.cell(3, 1), "value", None), "/inventory-imports/items"),
    ("inventory_unknown_warehouse", inventory, lambda s: setattr(s.cell(3, 1), "value", "NO_WAREHOUSE"), "/inventory-imports/items"),
    ("inventory_non_numeric", inventory, lambda s: setattr(s.cell(3, 6), "value", "abc"), "/inventory-imports/items"),
    ("inventory_empty_qty", inventory, lambda s: setattr(s.cell(3, 6), "value", None), "/inventory-imports/items"),
    ("inventory_overprecision_qty", inventory, lambda s: setattr(s.cell(3, 6), "value", "1.1234567"), "/inventory-imports/items"),
    ("inventory_overflow_qty", inventory, lambda s: setattr(s.cell(3, 6), "value", "1000000000000"), "/inventory-imports/items"),
    ("inventory_empty_data", inventory, lambda s: s.delete_rows(3, s.max_row), "/inventory-imports/items"),
    ("inventory_unknown_material", inventory, lambda s: setattr(s.cell(3, 4), "value", "NO_MRP_MATERIAL"), "/inventory-imports/items"),
]
results = []
for name, factory, mutate, path in cases:
    wb = factory()
    mutate(wb.active)
    buffer = io.BytesIO()
    wb.save(buffer)
    blob = buffer.getvalue()
    filename = name + "-" + OUT.name[-6:] + ".xlsx"
    (OUT / filename).write_bytes(blob)
    status, response = upload(path, blob, filename, token)
    staging = response.get("data") or {}
    task_id = staging.get("taskId")
    confirm_status = None
    if task_id and staging.get("errorRows", 0) > 0:
        confirm_path = path + "/" + str(task_id) + "/confirm"
        confirm_status, _ = request(confirm_path, "POST", b"", token)
    results.append({"case": name, "file": filename, "sha256": hashlib.sha256(blob).hexdigest(),
                    "uploadHttp": status, "taskId": task_id, "totalRows": staging.get("totalRows"),
                    "errorRows": staging.get("errorRows"), "errors": staging.get("errors"),
                    "confirmHttp": confirm_status, "status": "PASS" if
                    status == 202 and staging.get("errorRows", 0) > 0 and confirm_status == 400
                    else "FAIL"})
    (OUT / "progress.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
after = {table: sql("SELECT COUNT(*) FROM " + table) for table in tables}
summary = {"base": BASE, "before": before, "after": after, "cases": results,
           "status": "PASS" if before == after and all(r["status"] == "PASS" for r in results) else "FAIL"}
(OUT / "result.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
print(json.dumps({"path": str(OUT), "status": summary["status"],
                  "countsUnchanged": before == after,
                  "cases": [(r["case"], r["status"], r["errorRows"], r["confirmHttp"]) for r in results]}, ensure_ascii=False))
