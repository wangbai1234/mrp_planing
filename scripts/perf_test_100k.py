#!/usr/bin/env python3
"""
Performance test script for MRP system: 100,000 materials × 12 weeks
"""
import subprocess
import time
import json
import sys
import os

MATERIAL_COUNT = 100000
MONTHS = 6
BATCH_SIZE = 200  # Smaller batches to avoid command line length limits

def run_sql(sql, database="mrp"):
    """Execute SQL via Docker using stdin pipe to avoid argument length limits"""
    cmd = [
        "docker", "exec", "-i", "mrp-mysql", "mysql",
        "-u", "mrp", "-pmrp_dev", database
    ]
    result = subprocess.run(cmd, input=sql, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"SQL Error: {result.stderr}")
        return None
    return result.stdout

def generate_test_data():
    """Generate 100,000 forecast materials and insert into database"""
    print(f"=== Generating {MATERIAL_COUNT} materials test data ===")
    
    # Create new forecast version
    print("Creating forecast version...")
    run_sql("""
        INSERT INTO forecast_version (version_no, status, created_by)
        SELECT COALESCE(MAX(version_no), 0) + 1, 'IMPORTED', 1
        FROM forecast_version;
    """)
    
    result = run_sql("SELECT MAX(id) as id FROM forecast_version;")
    version_id = int(result.strip().split('\n')[1].strip())
    print(f"Created forecast version: {version_id}")
    
    # Generate materials in batches
    print(f"Generating {MATERIAL_COUNT} materials with {MONTHS} months each...")
    total_rows = 0
    start_time = time.time()
    
    months = [
        '2026-09-01', '2026-10-01', '2026-11-01',
        '2026-12-01', '2027-01-01', '2027-02-01'
    ]
    
    for batch_start in range(0, MATERIAL_COUNT, BATCH_SIZE):
        batch_end = min(batch_start + BATCH_SIZE, MATERIAL_COUNT)
        values = []
        
        for i in range(batch_start, batch_end):
            material_id = f"PERF{i:08d}"
            material_name = f"Performance Test Material {i}"
            for month in months:
                qty = 100 + (i % 500)  # Vary quantity between 100-600
                values.append(
                    f"({version_id}, 'YH', '{material_id}', '{material_name}', "
                    f"'测试业务线', '整机', 'PERF_PROJECT_{i % 100}', "
                    f"'PERF_PLATFORM_{i % 50}', NULL, NULL, '{month}', {qty})"
                )
        
        # Batch insert
        sql = f"""
            INSERT INTO forecast_detail 
            (version_id, factory_code, material_id, material_name, 
             business_line, form_type, project, platform, mold, status,
             plan_month, forecast_qty)
            VALUES {','.join(values)};
        """
        
        run_sql(sql)
        total_rows += len(values)
        
        elapsed = time.time() - start_time
        progress = (batch_end / MATERIAL_COUNT) * 100
        print(f"\rProgress: {progress:.1f}% ({batch_end}/{MATERIAL_COUNT}) - "
              f"{total_rows} rows inserted - {elapsed:.1f}s", end='', flush=True)
    
    elapsed = time.time() - start_time
    print(f"\nData generation complete: {total_rows} rows in {elapsed:.2f}s")
    return version_id

def trigger_recalculation(forecast_version_id):
    """Trigger MRP recalculation via API"""
    print(f"\n=== Triggering recalculation for forecast_version_id={forecast_version_id} ===")
    
    # Login
    login_cmd = [
        "curl", "-s", "-X", "POST",
        "http://localhost:8080/api/v1/auth/login",
        "-H", "Content-Type: application/json",
        "-d", '{"username":"admin","password":"admin123"}'
    ]
    result = subprocess.run(login_cmd, capture_output=True, text=True)
    login_data = json.loads(result.stdout)
    token = login_data['data']['token']
    
    # Trigger recalculation
    payload = {
        "factoryCode": "YH",
        "currentWeekStart": "2026-09-15",
        "forecastVersionId": forecast_version_id,
        "capacityVersionId": 6
    }
    
    recalc_cmd = [
        "curl", "-s", "-X", "POST",
        "http://localhost:8080/api/v1/recalculations",
        "-H", "Authorization: Bearer " + token,
        "-H", "Content-Type: application/json",
        "-d", json.dumps(payload)
    ]
    
    start_time = time.time()
    result = subprocess.run(recalc_cmd, capture_output=True, text=True)
    response = json.loads(result.stdout)
    
    if not response.get('success'):
        print(f"Recalculation failed: {response}")
        return None
    
    task_id = response['data']['taskId']
    print(f"Task created: {task_id}")
    
    # Poll for completion
    print("Waiting for completion...")
    while True:
        time.sleep(2)
        
        status_cmd = [
            "curl", "-s",
            f"http://localhost:8080/api/v1/recalculations/{task_id}",
            "-H", "Authorization: Bearer " + token
        ]
        result = subprocess.run(status_cmd, capture_output=True, text=True)
        status_data = json.loads(result.stdout)
        
        status = status_data['data']['status']
        elapsed = time.time() - start_time
        
        print(f"\rStatus: {status} - Elapsed: {elapsed:.1f}s", end='', flush=True)
        
        if status in ['SUCCEEDED', 'FAILED']:
            print()
            break
    
    elapsed = time.time() - start_time
    return {
        'task_id': task_id,
        'status': status,
        'elapsed_seconds': elapsed,
        'plan_version_id': status_data['data'].get('resultResourceId')
    }

def verify_results(plan_version_id):
    """Verify the calculation results"""
    print(f"\n=== Verifying results for plan_version_id={plan_version_id} ===")
    
    # Count plan_detail rows
    result = run_sql(f"SELECT COUNT(*) as cnt FROM plan_detail WHERE plan_version_id = {plan_version_id};")
    detail_count = int(result.strip().split('\n')[1].strip())
    print(f"Plan detail rows: {detail_count}")
    
    # Count distinct materials
    result = run_sql(f"SELECT COUNT(DISTINCT material_id) as cnt FROM plan_detail WHERE plan_version_id = {plan_version_id};")
    material_count = int(result.strip().split('\n')[1].strip())
    print(f"Distinct materials: {material_count}")
    
    # Get checksum
    result = run_sql(f"SELECT result_checksum FROM plan_version WHERE id = {plan_version_id};")
    checksum = result.strip().split('\n')[1].strip()
    print(f"Result checksum: {checksum}")
    
    return {
        'detail_count': detail_count,
        'material_count': material_count,
        'checksum': checksum
    }

def verify_batch_writes():
    """Verify batch writes by checking logs"""
    print("\n=== Verifying batch writes ===")
    # Check backend logs for batch size info
    result = subprocess.run(
        ["docker", "logs", "--tail", "100", "mrp-backend"],
        capture_output=True, text=True
    )
    
    # Look for batch insert logs
    batch_logs = [line for line in result.stdout.split('\n') if 'batch' in line.lower() or 'insert' in line.lower()]
    if batch_logs:
        print("Recent batch-related logs:")
        for log in batch_logs[-5:]:
            print(f"  {log}")
    
    return True

def verify_checksum_consistency(forecast_version_id):
    """Verify checksum consistency by running calculation twice"""
    print("\n=== Verifying checksum consistency ===")
    
    # Run first calculation
    result1 = trigger_recalculation(forecast_version_id)
    if not result1 or result1['status'] != 'SUCCEEDED':
        print("First calculation failed")
        return False
    
    checksum1 = verify_results(result1['plan_version_id'])['checksum']
    
    # Run second calculation
    result2 = trigger_recalculation(forecast_version_id)
    if not result2 or result2['status'] != 'SUCCEEDED':
        print("Second calculation failed")
        return False
    
    checksum2 = verify_results(result2['plan_version_id'])['checksum']
    
    if checksum1 == checksum2:
        print(f"Checksum consistency verified: {checksum1}")
        return True
    else:
        print(f"Checksum mismatch: {checksum1} vs {checksum2}")
        return False

def main():
    print("=" * 60)
    print("MRP Performance Test: 100,000 Materials × 12 Weeks")
    print("=" * 60)
    
    # Step 1: Generate test data
    forecast_version_id = generate_test_data()
    
    # Step 2: Trigger recalculation and measure time
    result = trigger_recalculation(forecast_version_id)
    if not result:
        print("Recalculation failed!")
        sys.exit(1)
    
    # Step 3: Verify results
    if result['plan_version_id']:
        verification = verify_results(result['plan_version_id'])
    
    # Step 4: Verify batch writes
    verify_batch_writes()
    
    # Print summary
    print("\n" + "=" * 60)
    print("PERFORMANCE TEST RESULTS")
    print("=" * 60)
    print(f"Material count: {MATERIAL_COUNT}")
    print(f"Months per material: {MONTHS}")
    print(f"Total forecast rows: {MATERIAL_COUNT * MONTHS}")
    print(f"Calculation time: {result['elapsed_seconds']:.2f} seconds")
    print(f"Status: {result['status']}")
    if result.get('plan_version_id'):
        print(f"Plan version ID: {result['plan_version_id']}")
        print(f"Plan detail rows: {verification['detail_count']}")
        print(f"Result checksum: {verification['checksum']}")
    print("=" * 60)

if __name__ == "__main__":
    main()
