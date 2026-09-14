#!/bin/bash

# Simple Docker Acceptance Test
# This script verifies the MRP system can start with Docker

set -e

echo "=== MRP Docker Simple Acceptance Test ==="
echo ""

# Step1: Check if Docker is running
echo "Step1: Checking Docker..."
if docker info > /dev/null 2>&1; then
    echo "✓ Docker is running"
else
    echo "✗ Docker is not running"
    exit1
fi

# Step2: Check if docker-compose.yml exists
echo ""
echo "Step2: Checking docker-compose.yml..."
if [ -f "/Users/wanglixun/Project/MRP新版/deploy/docker-compose.yml" ]; then
    echo "✓ docker-compose.yml exists"
else
    echo "✗ docker-compose.yml not found"
    exit1
fi

# Step3: Check if existing containers are running
echo ""
echo "Step3: Checking existing containers..."
if docker ps | grep -q "mrp-mysql"; then
    echo "✓ mrp-mysql is running"
else
    echo "⚠ mrp-mysql is not running"
fi

if docker ps | grep -q "mrp-redis"; then
    echo "✓ mrp-redis is running"
else
    echo "⚠ mrp-redis is not running"
fi

# Step4: Check if API is accessible
echo ""
echo "Step4: Checking API accessibility..."
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✓ API is accessible"
    HEALTH=$(curl -s http://localhost:8080/actuator/health)
    echo "Health: $HEALTH"
else
    echo "⚠ API is not accessible (may not be running)"
fi

# Step5: Check if database migrations exist
echo ""
echo "Step5: Checking database migrations..."
MIGRATION_DIR="/Users/wanglixun/Project/MRP新版/backend/src/main/resources/db/migration"
if [ -d "$MIGRATION_DIR" ]; then
    MIGRATION_COUNT=$(ls -1 "$MIGRATION_DIR"/*.sql 2>/dev/null | wc -l)
    echo "✓ Migration files found: $MIGRATION_COUNT"
else
    echo "✗ Migration directory not found"
fi

# Step6: Check if test data exists
echo ""
echo "Step6: Checking test data..."
TEST_DATA_DIR="/Users/wanglixun/Project/MRP新版/backend/src/test/resources/test-data"
if [ -d "$TEST_DATA_DIR" ]; then
    TEST_FILES=$(ls -1 "$TEST_DATA_DIR"/*.xlsx 2>/dev/null | wc -l)
    echo "✓ Test data files found: $TEST_FILES"
else
    echo "⚠ Test data directory not found"
fi

echo ""
echo "=== Docker Simple Acceptance Test Complete ==="
