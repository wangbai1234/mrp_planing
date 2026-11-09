#!/usr/bin/env python3
"""Small current-API import to batch chain against the fixed isolated port."""
import hashlib
import io
import json
import pathlib
import subprocess
import urllib.error
import urllib.request

import openpyxl

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "evidence/runs/2026-11-02-positive-chain"
OUT.mkdir(parents=True, exist_ok=True)
BASE = "http://127.0.0.1:8182/api/v1"


def req(path, method="GET", payload=None, token=None, content_type="application/json"):
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(payload).encode() if isinstance(payload, (dict, list)) else payload
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def sql(query):
    result = subprocess.run(
        ["docker", "exec", "-i", "mrp-testkit-mysql", "sh", "-c",
         'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" mrp_testkit'],
        input=query, capture_output=True, text=True, check=True)
    return result.stdout.strip()


def upload(path, name, file_bytes, token):
    boundary = "----MRPCurrentChain20261102"
    prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" +
              name + "\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n")
    body = prefix.encode() + file_bytes + ("\r\n--" + boundary + "--\r\n").encode()
    return req(path, "POST", body, token, "multipart/form-data; boundary=" + boundary)


def save(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


_, auth = req("/auth/login", "POST", {"username": "admin", "password": "admin123"})
token = auth["data"]["token"]
record = {"base": BASE, "before": {x: sql("SELECT COUNT(*) FROM " + x)
          for x in ["forecast_version", "forecast_detail", "inventory_snapshot", "inventory_item", "mrp_calc_batch"]}}

# Confirm existing test warehouse quality through the normal audited API.
status, response = req("/mrp/factory-warehouse-mappings", token=token)
assert status == 200, response
for mapping in response["data"]:
    if mapping["warehouseCode"] not in ("UAT_APK", "UAT_YH"):
        raise RuntimeError("Unexpected warehouse: " + mapping["warehouseCode"])
    if not mapping["qualityConfigured"]:
        update = dict(mapping)
        update["goodWarehouse"] = True
        update["warehouseAttribute"] = "自有仓"
        update["priorityNo"] = 1
        status, updated = req("/mrp/factory-warehouse-mappings/" + str(mapping["id"]), "PUT", update, token)
        assert status == 200 and updated["data"]["qualityConfigured"], updated
record["warehouses"] = [m["warehouseCode"] for m in response["data"]]

forecast = openpyxl.Workbook()
sheet = forecast.active
sheet.append(["工厂", "形态", "料号", "模具", "状态", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03", "2027-04", "fcst-total"])
sheet.append(["填写说明"])
for factory in ("爱培科", "永惠"):
    sheet.append([factory, "整机", "6839AA000359", "", "UAT", 1003, 0, 0, 0, 0, 0, 1003])
buffer = io.BytesIO()
forecast.save(buffer)
forecast_bytes = buffer.getvalue()
(OUT / "forecast-current.xlsx").write_bytes(forecast_bytes)
record["forecastSha256"] = hashlib.sha256(forecast_bytes).hexdigest()
status, preview = upload("/forecast-imports", "forecast-current.xlsx", forecast_bytes, token)
record["forecastPreview"] = {"http": status, "data": preview.get("data")}
save("progress.json", record)
assert status == 202 and preview["data"]["errorRows"] == 0, preview
forecast_task = preview["data"]["taskId"]
status, version = req(f"/forecast-imports/{forecast_task}/confirm", "POST", b"", token)
record["forecastConfirm"] = {"http": status, "data": version.get("data")}
save("progress.json", record)
assert status == 200, version
forecast_id = version["data"]["id"]
status, repeated = req(f"/forecast-imports/{forecast_task}/confirm", "POST", b"", token)
record["forecastRepeatedConfirm"] = {"http": status, "sameVersion": repeated.get("data", {}).get("id") == forecast_id}
assert status == 200 and record["forecastRepeatedConfirm"]["sameVersion"], repeated

# Convert the old candidate stock fixture to the current 7-column template.
old = openpyxl.load_workbook(ROOT / "data/xlsx/inventory-accuracy.xlsx", read_only=True)
inventory = openpyxl.Workbook()
sheet = inventory.active
sheet.append(["仓库编码*", "仓库名称", "库存分类", "料号*", "供应商名称", "库存数量*", "备注"])
sheet.append(["填写说明"])
for row in old.active.iter_rows(min_row=3, values_only=True):
    sheet.append([row[0], row[1], row[2], row[3], row[5], row[6], "UAT"])
buffer = io.BytesIO()
inventory.save(buffer)
inventory_bytes = buffer.getvalue()
(OUT / "inventory-current.xlsx").write_bytes(inventory_bytes)
record["inventorySha256"] = hashlib.sha256(inventory_bytes).hexdigest()
status, preview = upload("/inventory-imports/items", "inventory-current.xlsx", inventory_bytes, token)
record["inventoryPreview"] = {"http": status, "totalRows": preview.get("data", {}).get("totalRows"),
                              "errorRows": preview.get("data", {}).get("errorRows"),
                              "errors": preview.get("data", {}).get("errors", [])[:8]}
save("progress.json", record)
assert status == 202 and preview["data"]["errorRows"] == 0, preview
inventory_task = preview["data"]["taskId"]
status, snapshot = req(f"/inventory-imports/items/{inventory_task}/confirm", "POST", b"", token)
record["inventoryConfirm"] = {"http": status, "data": snapshot.get("data")}
save("progress.json", record)
assert status == 200, snapshot
snapshot_id = snapshot["data"]["snapshotId"]
status, repeated = req(f"/inventory-imports/items/{inventory_task}/confirm", "POST", b"", token)
record["inventoryRepeatedConfirm"] = {"http": status, "sameSnapshot": repeated.get("data", {}).get("snapshotId") == snapshot_id}
assert status == 200 and record["inventoryRepeatedConfirm"]["sameSnapshot"], repeated

query = f"factoryCode=ALL&forecastVersionId={forecast_id}&inventorySnapshotId={snapshot_id}"
status, batch = req("/mrp/batches?" + query, "POST", b"", token)
record["batchCreate"] = {"http": status, "data": batch.get("data"), "error": batch.get("error")}
record["after"] = {x: sql("SELECT COUNT(*) FROM " + x) for x in record["before"]}
save("progress.json", record)
print(json.dumps({"forecastTask": forecast_task, "forecastVersion": forecast_id,
                  "inventoryTask": inventory_task, "inventorySnapshot": snapshot_id,
                  "batchHttp": status, "batchId": (batch.get("data") or {}).get("id"),
                  "batchError": batch.get("error"), "evidence": str(OUT)}, ensure_ascii=False))
