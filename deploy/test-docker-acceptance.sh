#!/bin/bash

# Docker Empty Database Acceptance Test Script
# This script tests the MRP system with a fresh database

set -e

echo "=== MRP Docker Empty Database Acceptance Test ==="
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

# Step1: Stop and remove existing containers
echo "Step1: Stopping and removing existing containers..."
cd /Users/wanglixun/Project/MRP新版/deploy
docker compose down -v 2>/dev/null || true
print_status "Containers stopped" "PASS"

# Step2: Start fresh containers
echo ""
echo "Step2: Starting fresh containers..."
docker compose up -d
sleep 10

# Wait for MySQL to be healthy
echo "Waiting for MySQL to be healthy..."
for i in {1..30}; do
    if docker compose exec mysql mysqladmin ping -h localhost --silent 2>/dev/null; then
        print_status "MySQL is healthy" "PASS"
        break
    fi
    if [ $i -eq30 ]; then
        print_status "MySQL health check timeout" "FAIL"
        exit1
    fi
    sleep2
done

# Wait for Redis to be healthy
echo "Waiting for Redis to be healthy..."
for i in {1..15}; do
    if docker compose exec redis redis-cli ping 2>/dev/null | grep -q PONG; then
        print_status "Redis is healthy" "PASS"
        break
    fi
    if [ $i -eq15 ]; then
        print_status "Redis health check timeout" "FAIL"
        exit1
    fi
    sleep2
done

# Step3: Wait for API to be ready
echo ""
echo "Step3: Waiting for API to be ready..."
for i in {1..60}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        print_status "API is ready" "PASS"
        break
    fi
    if [ $i -eq60 ]; then
        print_status "API startup timeout" "FAIL"
        exit1
    fi
    sleep2
done

# Step4: Verify Flyway migrations
echo ""
echo "Step4: Verifying Flyway migrations..."
MIGRATION_COUNT=$(docker compose exec mysql mysql -u mrp -pmrp_dev mrp -e "SELECT COUNT(*) FROM flyway_schema_history" -s -N 2>/dev/null)
if [ "$MIGRATION_COUNT" -gt "0" ]; then
    print_status "Flyway migrations applied: $MIGRATION_COUNT" "PASS"
else
    print_status "No Flyway migrations found" "FAIL"
    exit1
fi

# Step5: Verify API health check
echo ""
echo "Step5: Verifying API health check..."
HEALTH_RESPONSE=$(curl -s http://localhost:8080/actuator/health)
if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"'; then
    print_status "API health check passed" "PASS"
else
    print_status "API health check failed" "FAIL"
    echo "Response: $HEALTH_RESPONSE"
fi

# Step6: Test login endpoint
echo ""
echo "Step6: Testing login endpoint..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')

if echo "$LOGIN_RESPONSE" | grep -q '"success":true'; then
    print_status "Login endpoint works" "PASS"
    TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    echo "Token obtained: ${TOKEN:0:20}..."
else
    print_status "Login endpoint failed" "FAIL"
    echo "Response: $LOGIN_RESPONSE"
fi

# Step7: Test protected endpoints
echo ""
echo "Step7: Testing protected endpoints..."

# Test materials endpoint
MATERIALS_RESPONSE=$(curl -s http://localhost:8080/api/v1/materials \
    -H "Authorization: Bearer $TOKEN")
if echo "$MATERIALS_RESPONSE" | grep -q '"success":true'; then
    print_status "Materials endpoint works" "PASS"
else
    print_status "Materials endpoint failed" "FAIL"
fi

# Test plans endpoint
PLANS_RESPONSE=$(curl -s http://localhost:8080/api/v1/plans \
    -H "Authorization: Bearer $TOKEN")
if echo "$PLANS_RESPONSE" | grep -q '"success":true'; then
    print_status "Plans endpoint works" "PASS"
else
    print_status "Plans endpoint failed" "FAIL"
fi

# Test users endpoint
USERS_RESPONSE=$(curl -s http://localhost:8080/api/v1/users \
    -H "Authorization: Bearer $TOKEN")
if echo "$USERS_RESPONSE" | grep -q '"success":true'; then
    print_status "Users endpoint works" "PASS"
else
    print_status "Users endpoint failed" "FAIL"
fi

# Step8: Verify seed data
echo ""
echo "Step8: Verifying seed data..."
USER_COUNT=$(docker compose exec mysql mysql -u mrp -pmrp_dev mrp -e "SELECT COUNT(*) FROM user" -s -N 2>/dev/null)
ROLE_COUNT=$(docker compose exec mysql mysql -u mrp -pmrp_dev mrp -e "SELECT COUNT(*) FROM role" -s -N 2>/dev/null)
PERMISSION_COUNT=$(docker compose exec mysql mysql -u mrp -pmrp_dev mrp -e "SELECT COUNT(*) FROM permission" -s -N 2>/dev/null)

if [ "$USER_COUNT" -gt "0" ]; then
    print_status "Users seeded: $USER_COUNT" "PASS"
else
    print_status "No users found" "FAIL"
fi

if [ "$ROLE_COUNT" -gt "0" ]; then
    print_status "Roles seeded: $ROLE_COUNT" "PASS"
else
    print_status "No roles found" "FAIL"
fi

if [ "$PERMISSION_COUNT" -gt "0" ]; then
    print_status "Permissions seeded: $PERMISSION_COUNT" "PASS"
else
    print_status "No permissions found" "FAIL"
fi

# Step9: Verify tables created
echo ""
echo "Step9: Verifying tables created..."
TABLES=$(docker compose exec mysql mysql -u mrp -pmrp_dev mrp -e "SHOW TABLES" -s -N 2>/dev/null)
TABLE_COUNT=$(echo "$TABLES" | wc -l)

if [ "$TABLE_COUNT" -gt "10" ]; then
    print_status "Tables created: $TABLE_COUNT" "PASS"
    echo "Tables: $TABLES"
else
    print_status "Insufficient tables: $TABLE_COUNT" "FAIL"
fi

# Step10: Cleanup
echo ""
echo "Step10: Cleaning up..."
docker compose down -v 2>/dev/null || true
print_status "Cleanup complete" "PASS"

echo ""
echo "=== Docker Empty Database Acceptance Test Complete ==="
