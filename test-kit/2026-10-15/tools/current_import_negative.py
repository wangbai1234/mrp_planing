#!/usr/bin/env python3
"""Current isolated import negative tests; never confirms a valid file."""
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
OUT = ROOT / ("evidence/runs/2026-11-02-import-negative-" + datetime.datetime.now().strftime("%H%M%S"))
OUT.mkdir(parents=True, exist_ok=True)
BASE = "http://127.0.0.1:8182/api/v1"


def request(path, method="GET", data=None, token=None, content_type="application/json"):
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def sql(query):
    result = subprocess.run(
        ["docker", "exec", "-i", "mrp-testkit-mysql", "sh", "-c",
         'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" mrp_testkit'],
        input=query, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def upload(path, blob, filename, token):
    boundary = "----MRPE2ETest20261102"
    prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
              "filename=\"" + filename + "\"\r\n"
              "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n")
    body = prefix.encode() + blob + ("\r\n--" + boundary + "--\r\n").encode()
    return request(path, "POST", body, token, "multipart/form-data; boundary=" + boundary)


_, login = request("/auth/login", "POST", json.dumps({"username": "admin", "password": "admin123"}).encode())
token = login["data"]["token"]
tables = ["forecast_version", "forecast_detail", "inventory_snapshot", "inventory_item"]
before = {table: sql("SELECT COUNT(*) FROM " + table) for table in tables}
records = []
cases = [
    ("forecast_missing_material", "forecast-accuracy", "/forecast-imports", "/forecast-imports/{id}/confirm", 3, None),
    ("forecast_negative_qty", "forecast-accuracy", "/forecast-imports", "/forecast-imports/{id}/confirm", 6, -7),
    ("forecast_overprecision_qty", "forecast-accuracy", "/forecast-imports", "/forecast-imports/{id}/confirm", 6, "1.1234567"),
    ("forecast_unknown_material", "forecast-accuracy", "/forecast-imports", "/forecast-imports/{id}/confirm", 3, "NO_SUCH_MRP_MATERIAL_20261102"),
    ("forecast_duplicate_month", "forecast-accuracy", "/forecast-imports", "/forecast-imports/{id}/confirm", 7, "2026-11"),
    ("forecast_mixed_valid_negative", "forecast-accuracy", "/forecast-imports", "/forecast-imports/{id}/confirm", 6, -9),
    ("inventory_negative_qty", "inventory-accuracy", "/inventory-imports/items", "/inventory-imports/items/{id}/confirm", 7, -7),
    ("inventory_missing_material", "inventory-accuracy", "/inventory-imports/items", "/inventory-imports/items/{id}/confirm", 4, None),
    ("inventory_mixed_valid_negative", "inventory-accuracy", "/inventory-imports/items", "/inventory-imports/items/{id}/confirm", 7, -9),
]
for name, source, route, confirm, col, replacement in cases:
    workbook = openpyxl.load_workbook(ROOT / "data/xlsx" / (source + ".xlsx"))
    sheet = workbook.active
    sheet.delete_rows(4, sheet.max_row)
    sheet.cell(1 if name == "forecast_duplicate_month" else 3, col).value = replacement
    if name == "forecast_mixed_valid_negative":
        sheet.append(["爱培科", "整机", "6839AA000685", "", "UAT", 1, 0, 0, 0, 0, 0, 1])
    if name == "inventory_mixed_valid_negative":
        sheet.append(["UAT_APK", "测试仓", "结构库存", "1117AA000040", "良品", "UAT供应商", 0])
    buffer = io.BytesIO()
    workbook.save(buffer)
    blob = buffer.getvalue()
    (OUT / (name + ".xlsx")).write_bytes(blob)
    status, response = upload(route, blob, name + ".xlsx", token)
    data = response.get("data") or {}
    task_id = data.get("taskId")
    confirm_status, confirm_response = request(confirm.format(id=task_id), "POST", b"", token) if task_id else (None, {})
    records.append({"case": name, "sha256": hashlib.sha256(blob).hexdigest(),
                    "upload_status": status, "task_id": task_id,
                    "total_rows": data.get("totalRows"), "error_rows": data.get("errorRows"),
                    "errors": data.get("errors"), "confirm_status": confirm_status,
                    "confirm_code": confirm_response.get("code"),
                    "confirm_message": str(confirm_response.get("message"))[:150]})
after = {table: sql("SELECT COUNT(*) FROM " + table) for table in tables}
result = {"timestamp": datetime.datetime.now().isoformat(), "base": BASE,
          "before": before, "after": after, "cases": records}
(OUT / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
print(json.dumps(result, ensure_ascii=False))
assert before == after, "A rejected file changed official inventory or forecast data"
for case in records:
    assert case["upload_status"] == 202 and case["error_rows"] >= 1 and case["confirm_status"] == 400, case
