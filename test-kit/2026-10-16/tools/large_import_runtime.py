"""Reproducible real Excel imports in isolated MySQL; protected planning data are read-only."""
import argparse
import datetime
from decimal import Decimal
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
import zipfile
from xml.sax.saxutils import escape

import psutil
import continuation_import_owner as http
import import_confirmation_runtime as fixture
from continuation_access import password
from continuation_publish import ROOT, sql


def write_row(stream, number, values):
    cells = ''.join(f'<c r="{chr(65+i)}{number}" t="inlineStr"><is><t>{escape(str(value))}</t></is></c>'
                    for i, value in enumerate(values))
    stream.write(f'<row r="{number}">{cells}</row>'.encode())


def workbook(flow, count, codes, marker):
    base = fixture.workbook(flow, codes[0])
    path = Path('/tmp') / ('mrp-large-import-' + marker + '.xlsx')
    assert not path.exists()
    with zipfile.ZipFile(io.BytesIO(base)) as source, zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as target:
        for name in source.namelist():
            if name != 'xl/worksheets/sheet1.xml':
                target.writestr(name, source.read(name))
        with target.open('xl/worksheets/sheet1.xml', 'w') as sheet:
            sheet.write(b'<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>')
            headers = (['工厂', '形态', '料号', '模具', '状态', '2026-10', '2026-11', '2026-12', '2027-01', '2027-02', '2027-03', '总量']
                       if flow == 'forecast' else ['仓库编码', '仓库名称', '库存分类', '料号', '品质', '供应商名称', '库存数量'])
            write_row(sheet, 1, headers)
            write_row(sheet, 2, [marker])
            for index in range(count):
                qty = str(Decimal(index % 97 + 1) + Decimal('0.000001'))
                code = codes[index % len(codes)]
                values = (['爱培科' if index % 2 == 0 else '永惠', '整机', code, f'test-{index}', 'test']
                          + [qty] * 6 + [str(Decimal(qty) * 6)] if flow == 'forecast'
                          else ['LARGE-IMPORT', '隔离大导入仓', 'test', code,
                                'GOOD' if index % 2 == 0 else 'DEFECTIVE', f'test-{index}', qty])
                write_row(sheet, index + 3, values)
            sheet.write(b'</sheetData></worksheet>')
    return path


def request(path, auth, raw=None, content_type='application/json'):
    headers = {'Authorization': 'Bearer ' + auth, 'Content-Type': content_type}
    start = time.monotonic()
    try:
        with urllib.request.urlopen(urllib.request.Request(http.BASE + path, data=raw, headers=headers, method='POST'), timeout=240) as response:
            status, body = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, body = error.code, error.read()
    return status, json.loads(body), round((time.monotonic() - start) * 1000)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--rows', type=int, nargs='+', required=True)
    parser.add_argument('--flows', choices=['forecast', 'items'], nargs='+', default=['forecast', 'items'])
    parser.add_argument('--label', required=True)
    args = parser.parse_args()
    assert all(n in (10000, 100000) for n in args.rows)
    assert re.fullmatch('[a-z0-9-]+', args.label)
    out = ROOT / ('evidence/large-import-' + args.label + '.json')
    assert not out.exists(), 'Do not overwrite evidence'
    before = fixture.protection()
    user = role = None
    cases = []
    passed = False
    try:
        http.admin = http.ok('/auth/login', 'POST', {'username': 'admin', 'password': password()})['token']
        stamp = str(time.time_ns())
        role = http.ok('/roles', 'POST', {'code': 'large_' + stamp, 'name': '大导入临时验收'})
        ids = {p['code']: p['id'] for p in http.ok('/permissions')}
        codes = ['forecast:view', 'forecast:import', 'forecast:import_confirm', 'inventory:view', 'inventory:import']
        http.ok(f"/roles/{role['id']}/permissions", 'PUT', {'permissionIds': [ids[c] for c in codes]})
        username = 'large_' + stamp
        user = http.ok('/users', 'POST', {'username': username, 'displayName': '大导入验收', 'roleIds': [role['id']]})
        auth = http.ok('/auth/login', 'POST', {'username': username, 'password': 'Mrp@123456'})['token']
        materials = sql('SELECT HEX(material_code) FROM material WHERE is_deleted=0 ORDER BY material_code LIMIT 20000;')
        materials = [bytes.fromhex(value).decode() for value in materials]
        assert materials
        pid = int(subprocess.run(['lsof', '-t', '-iTCP:8180', '-sTCP:LISTEN'], capture_output=True, text=True, check=True).stdout.strip())
        process = psutil.Process(pid)
        assert any('com.mrp.Mrp' in value for value in process.cmdline())
        for requested_count in args.rows:
            for flow in args.flows:
                # Forecast prohibits duplicate factory/material rows. Reuse the real, read-only
                # master catalog: a six-month file can still generate >100k monthly roots.
                count = requested_count
                if flow == 'forecast' and count > len(materials):
                    count = (requested_count + 5) // 6
                    assert count <= len(materials), 'Not enough existing unique materials for the forecast scale'
                marker = args.label + '-' + flow + '-' + str(count) + '-' + uuid.uuid4().hex
                path = workbook(flow, count, materials, marker)
                samples = []
                stop = threading.Event()

                def monitor():
                    while not stop.is_set():
                        try:
                            samples.append({'at': time.time(), 'rss': process.memory_info().rss,
                                            'cpuSeconds': sum(process.cpu_times()[:2])})
                        except psutil.Error:
                            break
                        stop.wait(.5)

                thread = threading.Thread(target=monitor, daemon=True)
                thread.start()
                case = {'flow': flow, 'requestedScale': requested_count, 'inputRows': count,
                        'expectedFormalRows': count * 6 if flow == 'forecast' else count,
                        'distinctSourceMaterials': min(count, len(materials)),
                        'marker': marker, 'fileBytes': path.stat().st_size,
                        'fileSha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'backendPid': pid}
                cases.append(case)
                try:
                    boundary = 'large' + uuid.uuid4().hex
                    raw = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
                           'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
                    raw += path.read_bytes() + f'\r\n--{boundary}--\r\n'.encode()
                    endpoint = '/forecast-imports' if flow == 'forecast' else '/inventory-imports/items'
                    status, body, elapsed = request(endpoint, auth, raw, 'multipart/form-data; boundary=' + boundary)
                    case.update(uploadStatus=status, uploadMs=elapsed)
                    assert status == 202 and body['success'], (flow, count, status, body.get('error'))
                    data = body['data']
                    case['preview'] = {key: data[key] for key in ['taskId', 'totalRows', 'successRows', 'errorRows']}
                    assert data['totalRows'] == count and data['successRows'] == count and data['errorRows'] == 0
                    task = data['taskId']
                    endpoint = f'/forecast-imports/{task}/confirm' if flow == 'forecast' else f'/inventory-imports/items/{task}/confirm'
                    status, body, elapsed = request(endpoint, auth)
                    case.update(confirmStatus=status, confirmMs=elapsed)
                    assert status == 200 and body['success'], (flow, count, status, body.get('error'))
                    resource = body['data']['id' if flow == 'forecast' else 'snapshotId']
                    expected_sum = sum((Decimal(i % 97 + 1) + Decimal('0.000001') for i in range(count)), Decimal(0))
                    if flow == 'forecast':
                        rows = sql(f'SELECT COUNT(*),SUM(forecast_qty),COUNT(DISTINCT plan_month) FROM forecast_detail WHERE version_id={resource};')
                        n, quantity, months = rows[0].split('\t')
                        assert int(n) == count * 6 and Decimal(quantity) == expected_sum * 6 and int(months) == 6
                        by_month = sql(f'SELECT plan_month,SUM(forecast_qty) FROM forecast_detail WHERE version_id={resource} GROUP BY plan_month ORDER BY plan_month;')
                        assert len(by_month) == 6 and all(Decimal(line.split('\t')[1]) == expected_sum for line in by_month)
                        case['monthlySums'] = by_month
                        invalid = int(sql(f'''SELECT COUNT(*) FROM forecast_detail f
JOIN material m ON m.material_code=f.material_id AND m.is_deleted=0
WHERE f.version_id={resource} AND (
 f.forecast_qty <> MOD(CAST(SUBSTRING(f.mold,6) AS UNSIGNED),97)+1+0.000001
 OR f.factory_code <> IF(MOD(CAST(SUBSTRING(f.mold,6) AS UNSIGNED),2)=0,'爱培科','永惠')
 OR NOT (HEX(f.platform) <=> HEX(m.spec_model)));''')[0])
                        assert invalid == 0, 'Per-row quantity, factory or full source specification changed'
                        groups = sql(f'''SELECT COUNT(*),MIN(n),MAX(n),MIN(months),MAX(months)
FROM (SELECT mold,COUNT(*) n,COUNT(DISTINCT plan_month) months FROM forecast_detail
WHERE version_id={resource} GROUP BY mold) source_rows;''')[0].split('\t')
                        assert list(map(int, groups)) == [count, 6, 6, 6, 6]
                        case['perRowMismatchCount'] = invalid
                        case['sourceRowMonthCounts'] = groups
                    else:
                        rows = sql(f'SELECT COUNT(*),SUM(shipping_available_qty),COUNT(DISTINCT inventory_quality) FROM inventory_item WHERE snapshot_id={resource};')
                        n, quantity, qualities = rows[0].split('\t')
                        assert int(n) == count and Decimal(quantity) == expected_sum and int(qualities) == 2
                        by_quality = sql(f'SELECT inventory_quality,COUNT(*),SUM(shipping_available_qty) FROM inventory_item WHERE snapshot_id={resource} GROUP BY inventory_quality ORDER BY inventory_quality;')
                        for line in by_quality:
                            quality, n, quantity = line.split('\t')
                            parity = 0 if quality == 'GOOD' else 1
                            expected = sum((Decimal(i % 97 + 1) + Decimal('0.000001') for i in range(parity, count, 2)), Decimal(0))
                            assert int(n) == count // 2 and Decimal(quantity) == expected
                        case['qualitySums'] = by_quality
                        invalid = int(sql(f'''SELECT COUNT(*) FROM inventory_item WHERE snapshot_id={resource}
AND (shipping_available_qty <> MOD(CAST(SUBSTRING(supplier_name,6) AS UNSIGNED),97)+1+0.000001
OR inventory_quality <> IF(MOD(CAST(SUBSTRING(supplier_name,6) AS UNSIGNED),2)=0,'GOOD','DEFECTIVE'));''')[0])
                        assert invalid == 0, 'Per-row quantity or inventory quality changed'
                        assert int(sql(f'SELECT COUNT(DISTINCT supplier_name) FROM inventory_item WHERE snapshot_id={resource};')[0]) == count
                        case['perRowMismatchCount'] = invalid
                    case['formalQuantities'] = rows
                    case['status'] = 'PASS'
                finally:
                    stop.set(); thread.join(timeout=2)
                    case['samples'] = samples
                    case['peakRssBytes'] = max((s['rss'] for s in samples), default=0)
                    case['cpuSeconds'] = round(samples[-1]['cpuSeconds'] - samples[0]['cpuSeconds'], 3) if samples else None
                    path.unlink()
                with urllib.request.urlopen('http://127.0.0.1:8180/actuator/health', timeout=10) as health:
                    assert health.status == 200 and json.load(health)['status'] == 'UP'
                current_pid = int(subprocess.run(['lsof', '-t', '-iTCP:8180', '-sTCP:LISTEN'],
                                                capture_output=True, text=True, check=True).stdout.strip())
                assert current_pid == pid and process.is_running(), 'Backend restarted during import'
                case['sameBackendProcessHealthy'] = True
                print(flow, count, 'PASS uploadMs', case['uploadMs'], 'confirmMs', case['confirmMs'], flush=True)
        passed = True
    finally:
        cleanup = False
        if user:
            uid = user['id']
            sql(f'''START TRANSACTION;
DELETE FROM forecast_detail WHERE version_id IN (SELECT id FROM forecast_version WHERE created_by={uid});
DELETE FROM inventory_item WHERE snapshot_id IN (SELECT id FROM inventory_snapshot WHERE created_by={uid});
DELETE FROM inventory_detail WHERE snapshot_id IN (SELECT id FROM inventory_snapshot WHERE created_by={uid});
DELETE FROM import_task WHERE created_by={uid};
DELETE FROM forecast_version WHERE created_by={uid};
DELETE FROM inventory_snapshot WHERE created_by={uid};
COMMIT;''')
            cleanup = all(fixture.count(flow, uid) == 0 for flow in ['forecast', 'items'])
            http.ok('/users/' + str(uid), 'DELETE')
        if role:
            http.ok('/roles/' + str(role['id']), 'DELETE')
        after = fixture.protection()
        result = {'status': 'PASS' if passed and cleanup and before == after else 'FAIL', 'cases': cases,
                  'protectionBefore': before, 'protectionAfter': after, 'temporaryFormalRowsCleared': cleanup,
                  'finishedAt': datetime.datetime.now().astimezone().isoformat()}
        out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    assert result['status'] == 'PASS'
