#!/usr/bin/env python3
"""Create fresh, valid isolated UI imports without touching source master data."""
import datetime
import hashlib
import json
import pathlib

import openpyxl

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "evidence/runs/2026-11-02-positive-chain"
STAMP = datetime.datetime.now().strftime("%Y%m%d%H%M%S%f")
OUT = ROOT / "evidence/runs" / ("current-ui-import-" + STAMP)
OUT.mkdir(parents=True)
forecast_qty = 1004 + int(STAMP[-6:])
inventory_qty = 1 + int(STAMP[-6:])

forecast = openpyxl.load_workbook(SOURCE / "forecast-current.xlsx")
forecast.active.cell(3, 6).value = forecast_qty
forecast.active.cell(3, 12).value = forecast_qty
forecast_path = OUT / "forecast-fresh.xlsx"
forecast.save(forecast_path)

inventory = openpyxl.load_workbook(SOURCE / "inventory-current.xlsx")
inventory.active.cell(3, 6).value = str(inventory_qty)
inventory_path = OUT / "inventory-fresh.xlsx"
inventory.save(inventory_path)

result = {
    "forecast": str(forecast_path),
    "forecastSha256": hashlib.sha256(forecast_path.read_bytes()).hexdigest(),
    "forecastQty": forecast_qty,
    "inventory": str(inventory_path),
    "inventorySha256": hashlib.sha256(inventory_path.read_bytes()).hexdigest(),
    "inventoryQty": inventory_qty,
    "createdAt": datetime.datetime.now().astimezone().isoformat(),
}
(OUT / "manifest.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(result, ensure_ascii=False))
