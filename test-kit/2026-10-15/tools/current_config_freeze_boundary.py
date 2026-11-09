#!/usr/bin/env python3
"""B15: prove config snapshots stay frozen while current masters change."""
import datetime
import decimal
import json
from pathlib import Path

from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, await_task, sql

OUT = Path(__file__).resolve().parents[1] / 'evidence/runs' / ('2026-11-03-b15-config-' + datetime.datetime.now().strftime('%H%M%S'))
CALENDAR_URL = '/mrp/production-calendars/month?factoryCode=%E7%88%B1%E5%9F%B9%E7%A7%91&month=2026-10'


def frozen(batch, day):
    calendar = sql(f"SELECT daily_capacity FROM production_calendar_day WHERE version_id={batch['productionCalendarVersionId']} AND factory_code='爱培科' AND calendar_date='{day}';")
    mapping = sql(f"SELECT priority_no FROM factory_warehouse_mapping_detail WHERE version_id={batch['factoryWarehouseMappingVersionId']} AND warehouse_code='UAT_APK';")
    assert len(calendar) == len(mapping) == 1, (calendar, mapping)
    return {'capacity': calendar[0], 'priority': mapping[0]}


def main():
    OUT.mkdir(parents=True)
    result = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'beforeSource': snapshot()}
    token = None
    batches = []
    calendar = mapping = None
    calendar_changed = mapping_changed = False
    try:
        status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
        assert status == 200 and login['success'], (status, login)
        token = login['data']['token']
        calendar = next(row for row in data(CALENDAR_URL, token) if row['calendarDate'] == '2026-10-29' and row['productionDay'])
        mapping = next(row for row in data('/mrp/factory-warehouse-mappings', token) if row['warehouseCode'] == 'UAT_APK')
        assert mapping['enabled'] and mapping['goodWarehouse'] and mapping['priorityNo'] is not None
        day = calendar['calendarDate']
        old = data('/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46', token, 'POST', b'')
        batches.append(old['id'])
        result['old'] = {'id': old['id'], 'inputChecksum': old['inputChecksum'], 'calendarId': old['productionCalendarVersionId'], 'mappingId': old['factoryWarehouseMappingVersionId'], 'frozen': frozen(old, day)}
        updated = dict(calendar, dailyCapacity=str(decimal.Decimal(calendar['dailyCapacity']) + 1))
        data(f"/mrp/production-calendars/{calendar['id']}", token, 'PUT', updated)
        calendar_changed = True
        data(f"/mrp/factory-warehouse-mappings/{mapping['id']}", token, 'PUT', dict(mapping, priorityNo=mapping['priorityNo'] + 1))
        mapping_changed = True
        old_now = data(f"/mrp/batches/{old['id']}", token)
        result['oldAfterChange'] = {'inputChecksum': old_now['inputChecksum'], 'calendarId': old_now['productionCalendarVersionId'], 'mappingId': old_now['factoryWarehouseMappingVersionId'], 'frozen': frozen(old_now, day)}
        assert result['oldAfterChange'] == {k: v for k, v in result['old'].items() if k != 'id'}
        assert data(f"/mrp/batches/{old['id']}/precheck", token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        result['oldTaskId'] = data(f"/mrp/batches/{old['id']}/calculate", token, 'POST', b'')['id']
        result['oldTask'] = await_task(old['id'], token)
        result['oldRunId'] = data(f"/mrp/batches/{old['id']}", token)['activeRunId']
        assert result['oldRunId'] and frozen(old, day) == result['old']['frozen']
        assert data(f"/mrp/batches/{old['id']}/cancel", token, 'POST', b'')['status'] == 'CANCELLED'
        batches.remove(old['id'])
        new = data('/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46', token, 'POST', b'')
        batches.append(new['id'])
        result['new'] = {'id': new['id'], 'inputChecksum': new['inputChecksum'], 'calendarId': new['productionCalendarVersionId'], 'mappingId': new['factoryWarehouseMappingVersionId'], 'frozen': frozen(new, day)}
        assert result['new']['inputChecksum'] != result['old']['inputChecksum']
        assert result['new']['calendarId'] != result['old']['calendarId'] and result['new']['mappingId'] != result['old']['mappingId']
        assert decimal.Decimal(result['new']['frozen']['capacity']) == decimal.Decimal(result['old']['frozen']['capacity']) + 1
        assert int(result['new']['frozen']['priority']) == int(result['old']['frozen']['priority']) + 1
        assert data(f"/mrp/batches/{new['id']}/precheck", token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        result['status'] = 'PASS'
    except Exception as error:
        result.update(status='FAIL', error=repr(error))
    finally:
        for batch_id in batches:
            try:
                result.setdefault('cancelled', []).append(data(f'/mrp/batches/{batch_id}/cancel', token, 'POST', b'')['status'])
            except Exception as error:
                result.setdefault('cleanupErrors', []).append(f'batch {batch_id}: {error!r}')
        if mapping_changed:
            try:
                data(f"/mrp/factory-warehouse-mappings/{mapping['id']}", token, 'PUT', mapping)
            except Exception as error:
                result.setdefault('cleanupErrors', []).append(f'mapping: {error!r}')
        if calendar_changed:
            try:
                current = next(row for row in data(CALENDAR_URL, token) if row['id'] == calendar['id'])
                data(f"/mrp/production-calendars/{calendar['id']}", token, 'PUT', dict(calendar, revision=current['revision']))
            except Exception as error:
                result.setdefault('cleanupErrors', []).append(f'calendar: {error!r}')
        result['afterSource'] = snapshot()
        result['protectedSourcesUnchanged'] = result['beforeSource']['tables'] == result['afterSource']['tables']
        result['restoredBusinessValues'] = bool(token and calendar and mapping and
            next(row for row in data(CALENDAR_URL, token) if row['id'] == calendar['id'])['dailyCapacity'] == calendar['dailyCapacity'] and
            next(row for row in data('/mrp/factory-warehouse-mappings', token) if row['id'] == mapping['id'])['priorityNo'] == mapping['priorityNo'])
        if result.get('cleanupErrors') or not result['protectedSourcesUnchanged'] or not result['restoredBusinessValues'] or any(status != 'CANCELLED' for status in result.get('cancelled', [])):
            result['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': result['status'], 'evidence': str(OUT), 'error': result.get('error'), 'cleanupErrors': result.get('cleanupErrors')}, ensure_ascii=False))
    assert result['status'] == 'PASS', result.get('error') or result.get('cleanupErrors')


if __name__ == '__main__':
    main()
