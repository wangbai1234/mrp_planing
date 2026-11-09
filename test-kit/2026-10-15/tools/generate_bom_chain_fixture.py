#!/usr/bin/env python3
"""Generate isolated import files for a root with current BOM children."""
import datetime
import hashlib
import json
import pathlib

import openpyxl

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'evidence/runs/2026-11-02-positive-chain'
STAMP = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
OUTPUT = ROOT / 'evidence/runs' / ('2026-11-02-bom-chain-' + STAMP)
OUTPUT.mkdir(parents=True)
MACHINE = '6839AA000365'
QUANTITY = 10

forecast = openpyxl.load_workbook(SOURCE / 'forecast-current.xlsx')
for row in (3, 4):
    forecast.active.cell(row, 3).value = MACHINE
    forecast.active.cell(row, 6).value = QUANTITY
    forecast.active.cell(row, 12).value = QUANTITY
forecast_path = OUTPUT / 'forecast-bom.xlsx'
forecast.save(forecast_path)

inventory = openpyxl.load_workbook(SOURCE / 'inventory-current.xlsx')
for warehouse in ('UAT_APK', 'UAT_YH'):
    inventory.active.append([warehouse, '测试仓', '结构库存', MACHINE, 'UAT供应商', '0', 'BOM链验收'])
inventory_path = OUTPUT / 'inventory-bom.xlsx'
inventory.save(inventory_path)

result = {
    'root': MACHINE,
    'forecastQuantityPerFactory': QUANTITY,
    'expectedRawMaterials': ['11520471', '25900077', '43111794'],
    'forecast': str(forecast_path),
    'forecastSha256': hashlib.sha256(forecast_path.read_bytes()).hexdigest(),
    'inventory': str(inventory_path),
    'inventorySha256': hashlib.sha256(inventory_path.read_bytes()).hexdigest(),
    'createdAt': datetime.datetime.now().astimezone().isoformat(),
}
(OUTPUT / 'manifest.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(result, ensure_ascii=False))
