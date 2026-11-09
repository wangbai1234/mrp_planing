#!/usr/bin/env python3
"""Independent Decimal checks for the small v5 whole-machine acceptance batch."""
import collections
import decimal
import json
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "evidence/runs/2026-11-02-positive-chain"
grid = json.loads((OUT / "grid-current.json").read_text())["data"]
assert grid["ruleVersion"] == "v5-shifted-business-weeks"
assert grid["activeRunId"] == 68
assert grid["totalRows"] == 2

def sql(query):
    result = subprocess.run(["docker", "exec", "-i", "mrp-testkit-mysql", "sh", "-c",
                             'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B --default-character-set=utf8mb4 -u"$MYSQL_USER" mrp_testkit'],
                            input=query, capture_output=True, text=True, check=True)
    return result.stdout.strip()

results = []
for row in grid["rows"]:
    factory = row["factoryCode"]
    assert factory in {"爱培科", "永惠"}
    source = sql("SELECT forecast_qty FROM forecast_detail WHERE version_id=30 AND factory_code='" +
                 factory + "' AND material_id='6839AA000359' AND plan_month='2026-11-01'")
    expected = decimal.Decimal(source)
    actual = sum(map(decimal.Decimal, row["dayQuantities"].values()))
    by_month = collections.defaultdict(decimal.Decimal)
    for day, quantity in row["dayQuantities"].items():
        by_month[day[:7]] += decimal.Decimal(quantity)
    assert expected == actual == decimal.Decimal(1003), (factory, expected, actual)
    assert by_month == {"2026-10": decimal.Decimal(203), "2026-11": decimal.Decimal(800)}, by_month
    assert row["dayQuantities"]["2026-10-29"] == "102"
    assert row["dayQuantities"]["2026-10-30"] == "101"
    results.append({"factory": factory, "sourceForecast": str(expected), "dailyTotal": str(actual),
                    "actualMonthTotals": {key: str(value) for key, value in by_month.items()}})

result = {"batchId": 74, "runId": 68, "rule": grid["ruleVersion"], "results": results,
          "status": "PASS", "basis": "forecast_detail source quantity compared with API daily quantities"}
(OUT / "decimal-oracle.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(result, ensure_ascii=False))
