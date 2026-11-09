#!/usr/bin/env python3
"""Check repeated same-byte upload and confirmation on the existing isolated run."""
import json
import pathlib
import subprocess
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "evidence/runs/2026-11-02-positive-chain"
BASE = "http://127.0.0.1:8182/api/v1"


def sql(query):
    result = subprocess.run(["docker", "exec", "-i", "mrp-testkit-mysql", "sh", "-c",
                             'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" mrp_testkit'],
                            input=query, text=True, capture_output=True, check=True)
    return result.stdout.strip()


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


_, auth = request("/auth/login", "POST", json.dumps({"username": "admin", "password": "admin123"}).encode())
token = auth["data"]["token"]
before = {table: sql("SELECT COUNT(*) FROM " + table) for table in ("forecast_version", "inventory_snapshot")}
results = []
for name, route, confirm, expected_task, expected_id in (
        ("forecast-current.xlsx", "/forecast-imports", "/forecast-imports/{id}/confirm", 108, 30),
        ("inventory-current.xlsx", "/inventory-imports/items", "/inventory-imports/items/{id}/confirm", 109, 36)):
    blob = (OUT / name).read_bytes()
    boundary = "----MRPRepeatedFile20261102"
    prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" +
              name + "\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n")
    body = prefix.encode() + blob + ("\r\n--" + boundary + "--\r\n").encode()
    status, preview = request(route, "POST", body, token, "multipart/form-data; boundary=" + boundary)
    task_id = preview.get("data", {}).get("taskId")
    confirm_status, confirmed = request(confirm.format(id=task_id), "POST", b"", token)
    actual_id = confirmed.get("data", {}).get("id" if name.startswith("forecast") else "snapshotId")
    results.append({"name": name, "uploadHttp": status, "taskId": task_id,
                    "expectedTask": expected_task, "confirmHttp": confirm_status,
                    "resourceId": actual_id, "expectedId": expected_id})
after = {table: sql("SELECT COUNT(*) FROM " + table) for table in before}
result = {"before": before, "after": after, "cases": results}
(OUT / "idempotency.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
print(json.dumps(result, ensure_ascii=False))
assert before == after
assert all(x["uploadHttp"] == 202 and x["confirmHttp"] == 200 and
           x["taskId"] == x["expectedTask"] and x["resourceId"] == x["expectedId"] for x in results)
