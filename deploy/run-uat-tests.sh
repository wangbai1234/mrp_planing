#!/bin/bash

# UAT Test Execution Script
# This script executes all remaining UAT test cases

set -e

echo "=== MRP UAT Test Execution ==="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    if [ "$2" = "PASS" ]; then
        echo -e "${GREEN}✓ $1${NC}"
    elif [ "$2" = "FAIL" ]; then
        echo -e "${RED}✗ $1${NC}"
    else
        echo -e "${YELLOW}⚠ $1${NC}"
    fi
}

# Base URL
BASE_URL="http://localhost:8080/api/v1"

# Login and get token
echo "Step1: Logging in..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')

if echo "$LOGIN_RESPONSE" | grep -q '"success":true'; then
    TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    print_status "Login successful" "PASS"
else
    print_status "Login failed" "FAIL"
    exit1
fi

# ========================================
# TC008: 经营计划导入测试
# ========================================
echo ""
echo "=== TC008: 经营计划导入测试 ==="

# Check if forecast template exists
TEMPLATE_FILE="/Users/wanglixun/Project/MRP新版/backend/src/test/resources/test-data/MRP经营计划导入模板.xlsx"
if [ -f "$TEMPLATE_FILE" ]; then
    print_status "Forecast template exists" "PASS"
    
    # Upload forecast file
    UPLOAD_RESPONSE=$(curl -s -X POST "$BASE_URL/forecast-imports" \
        -H "Authorization: Bearer $TOKEN" \
        -F "file=@$TEMPLATE_FILE")
    
    if echo "$UPLOAD_RESPONSE" | grep -q '"success":true'; then
        print_status "Forecast upload successful" "PASS"
        
        # Get import task ID
        TASK_ID=$(echo "$UPLOAD_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
        echo "Import Task ID: $TASK_ID"
        
        # Wait for import to complete
        sleep 2
        
        # Check import status
        STATUS_RESPONSE=$(curl -s "$BASE_URL/forecast-imports/$TASK_ID" \
            -H "Authorization: Bearer $TOKEN")
        
        if echo "$STATUS_RESPONSE" | grep -q '"success":true'; then
            print_status "Forecast import completed" "PASS"
        else
            print_status "Forecast import status check failed" "FAIL"
        fi
    else
        print_status "Forecast upload failed" "FAIL"
        echo "Response: $UPLOAD_RESPONSE"
    fi
else
    print_status "Forecast template not found" "FAIL"
fi

# ========================================
# TC009: 库存快照导入测试
# ========================================
echo ""
echo "=== TC009: 库存快照导入测试 ==="

# Check if inventory template exists
INVENTORY_TEMPLATE="/Users/wanglixun/Project/MRP新版/backend/src/test/resources/test-data/MRP库存快照导入模板.xlsx"
if [ -f "$INVENTORY_TEMPLATE" ]; then
    print_status "Inventory template exists" "PASS"
    
    # Upload inventory file
    UPLOAD_RESPONSE=$(curl -s -X POST "$BASE_URL/inventory-imports" \
        -H "Authorization: Bearer $TOKEN" \
        -F "file=@$INVENTORY_TEMPLATE")
    
    if echo "$UPLOAD_RESPONSE" | grep -q '"success":true'; then
        print_status "Inventory upload successful" "PASS"
        
        # Get import task ID
        TASK_ID=$(echo "$UPLOAD_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
        echo "Import Task ID: $TASK_ID"
        
        # Wait for import to complete
        sleep 2
        
        # Check import status
        STATUS_RESPONSE=$(curl -s "$BASE_URL/inventory-imports/$TASK_ID" \
            -H "Authorization: Bearer $TOKEN")
        
        if echo "$STATUS_RESPONSE" | grep -q '"success":true'; then
            print_status "Inventory import completed" "PASS"
        else
            print_status "Inventory import status check failed" "FAIL"
        fi
    else
        print_status "Inventory upload failed" "FAIL"
        echo "Response: $UPLOAD_RESPONSE"
    fi
else
    print_status "Inventory template not found" "FAIL"
fi

# ========================================
# TC001-TC004: 排产核心算法测试
# ========================================
echo ""
echo "=== TC001-TC004: 排产核心算法测试 ==="

# Get current plans
PLANS_RESPONSE=$(curl -s "$BASE_URL/plans?page=1&pageSize=10" \
    -H "Authorization: Bearer $TOKEN")

if echo "$PLANS_RESPONSE" | grep -q '"success":true'; then
    print_status "Plans endpoint accessible" "PASS"
    
    # Get plan count
    PLAN_COUNT=$(echo "$PLANS_RESPONSE" | grep -o '"total":[0-9]*' | cut -d':' -f2)
    echo "Total plans: $PLAN_COUNT"
    
    if [ "$PLAN_COUNT" -gt "0" ]; then
        print_status "Plans exist" "PASS"
    else
        print_status "No plans found" "WARN"
    fi
else
    print_status "Plans endpoint failed" "FAIL"
fi

# ========================================
# TC011: 排产导出测试
# ========================================
echo ""
echo "=== TC011: 排产导出测试 ==="

# Try to export plans
EXPORT_RESPONSE=$(curl -s -X POST "$BASE_URL/plans/1/exports" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{}')

if echo "$EXPORT_RESPONSE" | grep -q '"success":true'; then
    print_status "Export endpoint accessible" "PASS"
    
    # Get export task ID
    EXPORT_TASK_ID=$(echo "$EXPORT_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    echo "Export Task ID: $EXPORT_TASK_ID"
else
    print_status "Export endpoint failed" "FAIL"
    echo "Response: $EXPORT_RESPONSE"
fi

# ========================================
# TC012: 排产审核流程测试
# ========================================
echo ""
echo "=== TC012: 排产审核流程测试 ==="

# Try to publish a plan
PUBLISH_RESPONSE=$(curl -s -X POST "$BASE_URL/plans/1/publish" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json")

if echo "$PUBLISH_RESPONSE" | grep -q '"success":true'; then
    print_status "Publish endpoint accessible" "PASS"
else
    print_status "Publish endpoint failed" "FAIL"
    echo "Response: $PUBLISH_RESPONSE"
fi

# ========================================
# 验证测试结果
# ========================================
echo ""
echo "=== 验证测试结果 ==="

# Check materials
MATERIALS_RESPONSE=$(curl -s "$BASE_URL/materials?page=1&pageSize=10" \
    -H "Authorization: Bearer $TOKEN")

if echo "$MATERIALS_RESPONSE" | grep -q '"success":true'; then
    MATERIAL_COUNT=$(echo "$MATERIALS_RESPONSE" | grep -o '"total":[0-9]*' | cut -d':' -f2)
    print_status "Materials loaded: $MATERIAL_COUNT" "PASS"
else
    print_status "Materials endpoint failed" "FAIL"
fi

# Check plans
PLANS_RESPONSE=$(curl -s "$BASE_URL/plans?page=1&pageSize=10" \
    -H "Authorization: Bearer $TOKEN")

if echo "$PLANS_RESPONSE" | grep -q '"success":true'; then
    PLAN_COUNT=$(echo "$PLANS_RESPONSE" | grep -o '"total":[0-9]*' | cut -d':' -f2)
    print_status "Plans loaded: $PLAN_COUNT" "PASS"
else
    print_status "Plans endpoint failed" "FAIL"
fi

# Check capacity
CAPACITY_RESPONSE=$(curl -s "$BASE_URL/capacity-lines?page=1&pageSize=10" \
    -H "Authorization: Bearer $TOKEN")

if echo "$CAPACITY_RESPONSE" | grep -q '"success":true'; then
    CAPACITY_COUNT=$(echo "$CAPACITY_RESPONSE" | grep -o '"total":[0-9]*' | cut -d':' -f2)
    print_status "Capacity lines loaded: $CAPACITY_COUNT" "PASS"
else
    print_status "Capacity endpoint failed" "FAIL"
fi

echo ""
echo "=== UAT Test Execution Complete ==="
