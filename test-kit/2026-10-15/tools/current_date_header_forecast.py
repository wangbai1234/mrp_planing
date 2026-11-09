#!/usr/bin/env python3
"""Confirm a six-month forecast with real Excel date header cells in isolation."""
import datetime
import hashlib
import io
import json
import pathlib
import subprocess

import openpyxl

from current_material_cell_matrix import BASE, SEED, call, count
from current_source_snapshot import snapshot

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-date-header-' + datetime.datetime.now().strftime('%H%M%S'))


def sql(statement):
    assert statement.startswith('SELECT')
    proc = subprocess.run(['docker', 'exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
                           'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" mrp_testkit'],
                          input=statement, text=True, capture_output=True, check=True)
    return proc.stdout.splitlines()


def main():
    OUT.mkdir(parents=True)
    status, auth = call('/auth/login', 'POST', json.dumps({'username': 'admin', 'password': 'admin123'}).encode())
    assert status == 200, auth
    token = auth['data']['token']
    assert sql("SELECT COUNT(*) FROM mrp_calc_batch WHERE status NOT IN ('CANCELLED','PUBLISHED','STALE','SUPERSEDED');") == ['0']
    before = {'versions': count('forecast_version'), 'details': count('forecast_detail')}
    record = {'status': 'IN_PROGRESS', 'base': BASE, 'before': before, 'beforeSource': snapshot()}
    try:
        wb = openpyxl.load_workbook(SEED / 'forecast-current.xlsx')
        sheet = wb.active
        sheet.delete_rows(4, sheet.max_row - 3)
        months = []
        for i, column in enumerate(range(6, 12)):
            year = 2026 + (10 + i) // 12
            month = (10 + i) % 12 + 1
            value = datetime.date(year, month, 1)
            sheet.cell(1, column, value)
            sheet.cell(1, column).number_format = 'yyyy-mm'
            sheet.cell(3, column, str(i + 1))
            months.append(value.strftime('%Y-%m'))
        sheet['L3'] = 21
        buffer = io.BytesIO()
        wb.save(buffer)
        blob = buffer.getvalue()
        (OUT / 'forecast-date-header.xlsx').write_bytes(blob)
        record['sha256'] = hashlib.sha256(blob).hexdigest()
        boundary = '----MRPDateHeader20261103'
        head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="forecast-date-header.xlsx"'
                '\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n')
        http, staged = call('/forecast-imports', 'POST',
                            head.encode() + blob + ('\r\n--' + boundary + '--\r\n').encode(), token,
                            'multipart/form-data; boundary=' + boundary)
        record['uploadHttp'] = http
        record['preview'] = staged
        data = staged.get('data') or {}
        assert http == 202 and data.get('errorRows') == 0 and data.get('recognizedMonths') == months, staged
        task_id = data['taskId']
        http, confirmed = call(f'/forecast-imports/{task_id}/confirm', 'POST', b'', token)
        record['confirmHttp'] = http
        record['confirmation'] = confirmed
        assert http == 200 and confirmed.get('success'), confirmed
        version = confirmed['data']['id']
        rows = sql(f'SELECT DATE_FORMAT(plan_month,"%Y-%m"),CAST(forecast_qty AS CHAR) '
                   f'FROM forecast_detail WHERE version_id={int(version)} ORDER BY plan_month;')
        record['formalRows'] = rows
        assert rows == [f'{month}\t{i + 1}.000000' for i, month in enumerate(months)], rows
        after = {'versions': count('forecast_version'), 'details': count('forecast_detail')}
        record['after'] = after
        assert after['versions'] == before['versions'] + 1 and after['details'] == before['details'] + 6
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged']:
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS'
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT), 'formalRows': record['formalRows']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
