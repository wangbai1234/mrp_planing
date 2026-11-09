import fs from 'node:fs/promises';
import path from 'node:path';
import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';

const root = path.resolve(import.meta.dirname, '..');
const output = process.argv[2];
if (!output) throw new Error('Output directory required');
await fs.mkdir(output, { recursive: true });

const forecastSource = path.join(root, 'evidence/runs/2026-11-02-bom-chain-20261103100337957612/forecast-bom.xlsx');
const inventorySource = path.join(root, 'evidence/runs/2026-11-02-bom-chain-20261103093807076238/inventory-bom.xlsx');

for (const [name, materials] of [
  ['forecast-365-first.xlsx', ['6839AA000365', '6839AA000362']],
  ['forecast-362-first.xlsx', ['6839AA000362', '6839AA000365']],
]) {
  const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(forecastSource));
  const sheet = workbook.worksheets.getItemAt(0);
  sheet.getRange('A3:L4').values = materials.map(material =>
    ['爱培科', '整机', material, null, 'UAT', 10, 0, 0, 0, 0, 0, 10]);
  await workbook.recalculate();
  await (await SpreadsheetFile.exportXlsx(workbook)).save(path.join(output, name));
}

const weeksWorkbook = await SpreadsheetFile.importXlsx(await FileBlob.load(forecastSource));
const weeksSheet = weeksWorkbook.worksheets.getItemAt(0);
weeksSheet.getRange('F3:L4').values = [
  [1003, 0, 0, 1003, 0, 0, 2006],
  [0, 0, 0, 0, 0, 0, 0],
];
await weeksWorkbook.recalculate();
await (await SpreadsheetFile.exportXlsx(weeksWorkbook)).save(path.join(output, 'forecast-four-five-weeks.xlsx'));

const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(inventorySource));
const sheet = workbook.worksheets.getItemAt(0);
sheet.getRange('F7').values = [['15']];
sheet.getRange('A167:G167').values = [[
  'UAT_APK', '测试仓', '结构库存', '6839AA000362', 'UAT供应商', '0', 'B07共享库存验收'
]];
sheet.getRange('A168:G168').values = [[
  'UAT_APK_DEF', '隔离测试不良品仓', '结构库存', '11520471', 'UAT供应商', '0', 'B07不良品不扣减'
]];
await workbook.recalculate();
await (await SpreadsheetFile.exportXlsx(workbook)).save(path.join(output, 'inventory-shared.xlsx'));
sheet.getRange('F7').values = [['7']];
sheet.getRange('A169:G169').values = [[
  'UAT_SCOPE_EXTRA_1103', '隔离范围外测试仓', '结构库存', '11520471', 'UAT供应商', '8', 'B08次优先级良品仓'
]];
await workbook.recalculate();
await (await SpreadsheetFile.exportXlsx(workbook)).save(path.join(output, 'inventory-multistock.xlsx'));
console.log(JSON.stringify({ output, files: ['forecast-365-first.xlsx', 'forecast-362-first.xlsx', 'forecast-four-five-weeks.xlsx', 'inventory-shared.xlsx', 'inventory-multistock.xlsx'] }));
