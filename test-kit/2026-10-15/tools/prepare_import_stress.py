#!/usr/bin/env python3
"""Build the optional 100k import-only workbook specification; no DB access."""
import pathlib,csv,json
p=pathlib.Path(__file__).resolve().parents[1]
with (p/'data/forecast-import-100000.csv').open(encoding='utf-8-sig',newline='') as f:
 rows=list(csv.reader(f))
assert len(rows)==100002
(p/'data/workbooks-100000.json').write_text(json.dumps([{'name':'forecast-import-100000','headers':rows[0],'tips':rows[1],'rows':rows[2:]}],ensure_ascii=False))
print('Prepared 100000 physical rows; this is not 100000 distinct materials.')
