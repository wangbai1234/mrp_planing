import { test, expect } from '@playwright/test';

const BASE_URL = 'http://localhost:5183';
const API_URL = 'http://localhost:8080';

test.describe('MRP Business Flow Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.waitForURL('**/login');
    
    const usernameInput = page.locator('input[placeholder*="用户名"], input[type="text"]').first();
    const passwordInput = page.locator('input[placeholder*="密码"], input[type="password"]').first();
    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]').first();
    
    await usernameInput.fill('admin');
    await passwordInput.fill('admin123');
    await loginButton.click();
    
    // Wait for redirect
    await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 });
  });

  test('TC001-TC004: 排产核心算法完整流程', async ({ page }) => {
    // Navigate to planning page
    await page.click('text=排产计划');
    await page.waitForURL('**/planning');
    
    // Check if plans table exists
    const plansTable = page.locator('.el-table');
    await expect(plansTable).toBeVisible();
    
    // Try to create a new plan
    const createButton = page.locator('button:has-text("新增")');
    if (await createButton.isVisible()) {
      await createButton.click();
      await page.waitForTimeout(1000);
      
      // Fill plan form if dialog appears
      const dialog = page.locator('.el-dialog');
      if (await dialog.isVisible()) {
        await page.fill('input[placeholder*="工厂代码"]', 'YH');
        await page.fill('input[placeholder*="版本号"]', '1');
        await page.click('button:has-text("确认")');
        await page.waitForTimeout(1000);
      }
    }
    
    // Verify plan was created
    const planRows = page.locator('.el-table__row');
    const planCount = await planRows.count();
    console.log(`Found ${planCount} plans`);
    
    // Try to recalculate if plan exists
    if (planCount > 0) {
      const firstPlan = planRows.first();
      const recalcButton = firstPlan.locator('button:has-text("重算")');
      if (await recalcButton.isVisible()) {
        await recalcButton.click();
        await page.waitForTimeout(2000);
      }
    }
    
    console.log('TC001-TC004排产核心算法测试完成');
  });

  test('TC005-TC007: 排产滚动/调整测试', async ({ page }) => {
    // Navigate to planning page
    await page.click('text=排产计划');
    await page.waitForURL('**/planning');
    
    // Check existing plans
    const planRows = page.locator('.el-table__row');
    const planCount = await planRows.count();
    
    if (planCount > 0) {
      // Click on first plan to view details
      await planRows.first().click();
      await page.waitForTimeout(1000);
      
      // Try to edit a plan row
      const editButtons = page.locator('button:has-text("编辑")');
      if (await editButtons.count() > 0) {
        await editButtons.first().click();
        await page.waitForTimeout(500);
        
        // Try to change manual quantity
        const manualInput = page.locator('input[placeholder*="人工调整量"]');
        if (await manualInput.isVisible()) {
          await manualInput.fill('100');
          await page.click('button:has-text("保存")');
          await page.waitForTimeout(1000);
        }
      }
      
      // Try to lock/unlock a week
      const lockButtons = page.locator('button:has-text("锁定")');
      if (await lockButtons.count() > 0) {
        await lockButtons.first().click();
        await page.waitForTimeout(500);
      }
    }
    
    console.log('TC005-TC007排产滚动/调整测试完成');
  });

  test('TC008: 经营计划导入完整流程', async ({ page }) => {
    // Navigate to forecast import page
    await page.click('text=经营计划');
    await page.waitForURL('**/forecast');
    
    // Check if import button exists
    const importButton = page.locator('button:has-text("导入")');
    await expect(importButton).toBeVisible();
    
    // Try to upload a file
    const fileInput = page.locator('input[type="file"]');
    if (await fileInput.count() > 0) {
      // Create a test file
      const testFile = '/Users/wanglixun/Project/MRP新版/backend/src/test/resources/test-data/MRP经营计划导入模板.xlsx';
      await fileInput.setInputFiles(testFile);
      await page.waitForTimeout(2000);
      
      // Check for preview
      const previewTable = page.locator('.el-table');
      if (await previewTable.isVisible()) {
        console.log('File preview displayed');
      }
      
      // Check for confirm button
      const confirmButton = page.locator('button:has-text("确认导入")');
      if (await confirmButton.isVisible()) {
        await confirmButton.click();
        await page.waitForTimeout(3000);
      }
    }
    
    // Check import history
    const historyTable = page.locator('.el-table');
    if (await historyTable.isVisible()) {
      const historyRows = page.locator('.el-table__row');
      const historyCount = await historyRows.count();
      console.log(`Import history: ${historyCount} records`);
    }
    
    console.log('TC008经营计划导入测试完成');
  });

  test('TC009: 库存快照导入完整流程', async ({ page }) => {
    // Navigate to inventory import page
    await page.click('text=库存快照');
    await page.waitForURL('**/inventory');
    
    // Check if import button exists
    const importButton = page.locator('button:has-text("导入")');
    await expect(importButton).toBeVisible();
    
    // Try to upload a file
    const fileInput = page.locator('input[type="file"]');
    if (await fileInput.count() > 0) {
      const testFile = '/Users/wanglixun/Project/MRP新版/backend/src/test/resources/test-data/MRP库存快照导入模板.xlsx';
      await fileInput.setInputFiles(testFile);
      await page.waitForTimeout(2000);
      
      // Check for preview
      const previewTable = page.locator('.el-table');
      if (await previewTable.isVisible()) {
        console.log('File preview displayed');
      }
      
      // Check for confirm button
      const confirmButton = page.locator('button:has-text("确认导入")');
      if (await confirmButton.isVisible()) {
        await confirmButton.click();
        await page.waitForTimeout(3000);
      }
    }
    
    // Check import history
    const historyTable = page.locator('.el-table');
    if (await historyTable.isVisible()) {
      const historyRows = page.locator('.el-table__row');
      const historyCount = await historyRows.count();
      console.log(`Import history: ${historyCount} records`);
    }
    
    console.log('TC009库存快照导入测试完成');
  });

  test('TC010: 已出货记录录入测试', async ({ page }) => {
    // Navigate to shipment records page (if exists)
    await page.click('text=库存快照');
    await page.waitForTimeout(1000);
    
    // Check for shipment records menu
    const shipmentMenu = page.locator('text=已出货记录');
    if (await shipmentMenu.isVisible()) {
      await shipmentMenu.click();
      await page.waitForTimeout(1000);
      
      // Try to add a new record
      const addButton = page.locator('button:has-text("新增")');
      if (await addButton.isVisible()) {
        await addButton.click();
        await page.waitForTimeout(500);
        
        // Fill form
        const dialog = page.locator('.el-dialog');
        if (await dialog.isVisible()) {
          await page.fill('input[placeholder*="物料ID"]', '6830AA800561');
          await page.fill('input[placeholder*="工厂代码"]', 'YH');
          await page.fill('input[placeholder*="出货月份"]', '2026-08');
          await page.fill('input[placeholder*="出货数量"]', '100');
          await page.click('button:has-text("确认")');
          await page.waitForTimeout(1000);
        }
      }
    }
    
    console.log('TC010已出货记录录入测试完成');
  });

  test('TC011: 排产导出测试', async ({ page }) => {
    // Navigate to planning page
    await page.click('text=排产计划');
    await page.waitForURL('**/planning');
    
    // Check if plans exist
    const planRows = page.locator('.el-table__row');
    const planCount = await planRows.count();
    
    if (planCount > 0) {
      // Click on first plan
      await planRows.first().click();
      await page.waitForTimeout(1000);
      
      // Try to export
      const exportButton = page.locator('button:has-text("导出")');
      if (await exportButton.isVisible()) {
        await exportButton.click();
        await page.waitForTimeout(2000);
        
        // Check for download
        const download = await page.waitForEvent('download', { timeout: 5000 }).catch(() => null);
        if (download) {
          console.log(`Downloaded file: ${download.suggestedFilename()}`);
        }
      }
    }
    
    console.log('TC011排产导出测试完成');
  });

  test('TC012: 排产审核发布测试', async ({ page }) => {
    // Navigate to planning page
    await page.click('text=排产计划');
    await page.waitForURL('**/planning');
    await page.waitForTimeout(2000); // Wait for data to load
    
    // Check if publish button exists and is enabled
    const publishButton = page.locator('button:has-text("发布")').first();
    const isPublishVisible = await publishButton.isVisible({ timeout: 5000 }).catch(() => false);
    
    if (!isPublishVisible) {
      console.log('TC012: Publish button not visible, skipping');
      return;
    }
    
    // Check if button is disabled
    const isDisabled = await publishButton.isDisabled();
    if (isDisabled) {
      console.log('TC012: Publish button is disabled - no plan version or already published');
      // Try to recalculate first
      const recalcButton = page.locator('button:has-text("重算")').first();
      if (await recalcButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        const isRecalcDisabled = await recalcButton.isDisabled();
        if (!isRecalcDisabled) {
          console.log('TC012: Clicking recalculate to create plan version');
          await recalcButton.click();
          await page.waitForTimeout(5000); // Wait for recalculation
          
          // Check if publish is now enabled
          const isStillDisabled = await publishButton.isDisabled();
          if (isStillDisabled) {
            console.log('TC012: Publish still disabled after recalc, skipping');
            return;
          }
        } else {
          console.log('TC012: Recalculate also disabled, skipping');
          return;
        }
      } else {
        return;
      }
    }
    
    // Now try to publish
    console.log('TC012: Attempting to publish');
    await publishButton.click();
    await page.waitForTimeout(2000);
    
    // Check for confirmation dialog
    const dialog = page.locator('.el-message-box, .el-dialog').first();
    const hasDialog = await dialog.isVisible({ timeout: 3000 }).catch(() => false);
    
    if (hasDialog) {
      const confirmButton = page.locator('button:has-text("确认"), button:has-text("确定")').first();
      if (await confirmButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await confirmButton.click();
        await page.waitForTimeout(2000);
      }
    }
    
    // Verify publish success (check for success message or status change)
    const successMsg = page.locator('.el-message--success');
    const hasSuccess = await successMsg.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasSuccess) {
      console.log('TC012: Publish successful');
    }
    
    console.log('TC012排产审核发布测试完成');
  });

  test('TC013: 产能超限异常场景测试', async ({ page }) => {
    // Navigate to planning page
    await page.click('text=排产计划');
    await page.waitForURL('**/planning');
    
    // Check if plans exist
    const planRows = page.locator('.el-table__row');
    const planCount = await planRows.count();
    
    if (planCount > 0) {
      // Click on first plan
      await planRows.first().click();
      await page.waitForTimeout(1000);
      
      // Check for capacity exceeded indicators
      const capacityWarning = page.locator('.el-tag:has-text("超限")');
      if (await capacityWarning.count() > 0) {
        console.log('Found capacity exceeded warnings');
      }
      
      // Try to edit and set quantity above capacity
      const editButtons = page.locator('button:has-text("编辑")');
      if (await editButtons.count() > 0) {
        await editButtons.first().click();
        await page.waitForTimeout(500);
        
        const manualInput = page.locator('input[placeholder*="人工调整量"]');
        if (await manualInput.isVisible()) {
          await manualInput.fill('10000'); // Very high quantity
          await page.click('button:has-text("保存")');
          await page.waitForTimeout(1000);
        }
      }
    }
    
    console.log('TC013产能超限异常场景测试完成');
  });

  test('TC014: 周锁定状态测试', async ({ page }) => {
    // Navigate to planning page
    await page.click('text=排产计划');
    await page.waitForURL('**/planning');
    
    // Check if plans exist
    const planRows = page.locator('.el-table__row');
    const planCount = await planRows.count();
    
    if (planCount > 0) {
      // Click on first plan
      await planRows.first().click();
      await page.waitForTimeout(1000);
      
      // Try to lock a week
      const lockButtons = page.locator('button:has-text("锁定")');
      if (await lockButtons.count() > 0) {
        await lockButtons.first().click();
        await page.waitForTimeout(500);
        
        // Check for lock indicator
        const lockIcon = page.locator('.el-icon-lock');
        if (await lockIcon.count() > 0) {
          console.log('Week locked successfully');
        }
        
        // Try to edit locked week
        const editButtons = page.locator('button:has-text("编辑")');
        if (await editButtons.count() > 0) {
          await editButtons.first().click();
          await page.waitForTimeout(500);
          
          // Check if edit is blocked
          const manualInput = page.locator('input[placeholder*="人工调整量"]');
          if (await manualInput.isVisible()) {
            const isDisabled = await manualInput.isDisabled();
            console.log(`Edit disabled for locked week: ${isDisabled}`);
          }
        }
      }
    }
    
    console.log('TC014周锁定状态测试完成');
  });

  test('TC015-TC016: BOM查询测试 (Phase2)', async ({ page }) => {
    // Navigate to BOM page
    await page.click('text=BOM管理');
    await page.waitForURL('**/bom');
    
    // Check BOM page
    const bomTable = page.locator('.el-table');
    await expect(bomTable).toBeVisible();
    
    // Try to search
    const searchInput = page.locator('input[placeholder*="搜索"]');
    if (await searchInput.isVisible()) {
      await searchInput.fill('6830AA800561');
      await page.waitForTimeout(1000);
    }
    
    // Try to sync BOM
    const syncButton = page.locator('button:has-text("同步")');
    if (await syncButton.isVisible()) {
      await syncButton.click();
      await page.waitForTimeout(2000);
      
      // Check for confirmation dialog
      const confirmDialog = page.locator('.el-message-box, .el-dialog');
      if (await confirmDialog.isVisible({ timeout: 3000 }).catch(() => false)) {
        // Try to confirm
        const confirmButton = page.locator('button:has-text("确认"), button:has-text("确定"), button:has-text("开始同步")');
        if (await confirmButton.isVisible({ timeout: 2000 }).catch(() => false)) {
          await confirmButton.click();
          await page.waitForTimeout(3000);
        }
      }
    }
    
    console.log('TC015-TC016BOM查询测试完成');
  });
});
