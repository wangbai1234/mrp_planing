# 数据导入与列表设计规范

> 所有导入和列表功能必须遵循本规范，确保用户体验一致、大批量数据稳定运行。

---

## 1. 页面布局规范（强制）

### 1.1 页面头部布局
所有页面必须使用统一的头部布局：
```vue
<div class="page-header">
  <div>
    <h1 class="page-title">页面标题</h1>
  </div>
  <div class="page-actions">
    <!-- 右侧按钮组 -->
  </div>
</div>
```

**样式规范：**
```css
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1F2329;
}
```

**注意：不要添加 page-desc 描述文字，保持头部简洁。**

### 1.2 筛选栏布局
筛选栏使用统一的卡片样式：
```vue
<div class="filter-bar">
  <el-select ... style="width: 140px" />
  <el-input ... style="width: 220px" />
</div>
```

**样式规范：**
```css
.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
}
```

**组件尺寸规范：**
| 组件 | 宽度 | 尺寸 |
|------|------|------|
| 搜索框 | 220px | default |
| 下拉筛选 | 140px | default |
| 按钮 | auto | default |

### 1.3 按钮规范（强制）

**按钮文案统一：**
| 功能 | 文案 | 样式 |
|------|------|------|
| 导入 | 导入 | type="primary" |
| 导出 | 导出 | type="primary" |
| 下载模板 | 下载模板 | type="primary" |
| 新增 | 新增 | type="primary" |
| 删除 | 删除 | type="danger" link |
| 取消 | 取消 | 默认 |
| 确认 | 确认 | type="primary" |

**按钮颜色（飞书风格）：**
- 主按钮：`type="primary"`（蓝色 #1677FF）
- 危险按钮：`type="danger" link`（红色文字链接）
- 次要按钮：默认样式（白色背景灰色边框）

**禁止：**
- ❌ 不要使用 `plain` 修饰符
- ❌ 不要使用多种颜色的按钮
- ❌ 不要在按钮文字中添加"上传"、"填写后"等多余词汇

### 1.3 表格卡片布局
```vue
<div class="table-card" v-loading="loading">
  <el-table :data="..." size="default" border />
  <div class="pagination-bar">
    <span class="total-text">共 X 条</span>
    <el-pagination ... />
  </div>
</div>
```

**样式规范：**
```css
.table-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
}

.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e6eb;
}

.total-text {
  font-size: 14px;
  color: #86909C;
}
```

---

## 2. 分页规范（强制）

### 2.1 核心原则
- **所有列表接口必须支持分页**，禁止一次性返回全部数据
- 默认每页 20 条，最大每页 100 条
- 超过 50 条数据的列表必须使用后端分页

### 2.2 分页参数
| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码（从1开始） |
| pageSize | int | 20 | 每页条数 |

### 2.3 分页响应格式
```json
{
    "success": true,
    "data": {
        "items": [...],
        "total": 19918,
        "page": 1,
        "pageSize": 20,
        "totalPages": 996
    }
}
```

### 2.4 前端实现
```vue
<el-pagination
    v-model:current-page="currentPage"
    v-model:page-size="pageSize"
    :total="total"
    :page-sizes="[20, 50, 100]"
    layout="total, sizes, prev, pager, next"
    @size-change="handleSizeChange"
    @current-change="handleCurrentChange"
/>
```

---

## 3. 中文化规范（强制）

### 3.1 Element Plus 中文配置
```typescript
// main.ts
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
app.use(ElementPlus, { locale: zhCn })
```

### 3.2 文案规范
| 场景 | 英文（禁止） | 中文（正确） |
|------|-------------|-------------|
| 分页 | 20/page | 20条/页 |
| 分页 | total | 共 X 条 |
| 按钮 | Cancel | 取消 |
| 按钮 | Confirm | 确认 |
| 提示 | No data | 暂无数据 |

---

## 4. 导入弹窗规范（飞书风格）

### 4.1 弹窗尺寸
- 宽度：760px
- 圆角：12px
- 白色背景，轻微阴影

### 4.2 弹窗结构
1. **标题**：入库批量导入（20px 半粗深灰）
2. **步骤提示**：步骤 1/2 + "下载模板"蓝色链接
3. **上传区域**：虚线边框，200px高，圆角10px
4. **规则说明**：5条规范，14px灰色文字
5. **底部按钮**：取消 + 开始导入

### 4.3 颜色规范
- 主色：#1677FF
- 标题：#1F2329
- 正文：#4E5969
- 辅助：#86909C
- 错误：#F53F3F
- 成功：#00B42A

### 4.4 导入模板规范
| 行号 | 内容 | 说明 |
|------|------|------|
| 第1行 | 展示名称 | 中文列名，带*号表示必填 |
| 第2行 | 填写说明 | 提示用户如何填写 |
| 第3行起 | 示例数据/实际数据 | 示例数据第一列为"示例" |

---

## 5. 检查清单

每个页面开发前必须确认：
- [ ] 页面头部布局是否符合规范（左右结构）
- [ ] 筛选栏尺寸是否符合规范（搜索220px，下拉140px）
- [ ] 表格卡片样式是否符合规范
- [ ] 分页组件是否实现且中文化
- [ ] 导入弹窗是否使用飞书风格
- [ ] 模板结构是否符合规范（2行表头+示例数据）

---

**最后更新**: 2026-09-04
**版本**: 2.1.0

### 1.1 模板优先
- **所有导入必须先提供统一模板下载**
- 用户必须使用官方模板填写数据
- 禁止直接上传任意格式的Excel/CSV

### 1.2 两步流程
1. **下载模板** → 用户填写数据
2. **上传文件** → 系统校验 → 确认导入

---

## 2. 导入弹窗UI规范

### 2.1 弹窗布局
```
┌─────────────────────────────────────────────────────┐
│  批量导入物料                               [X]关闭 │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ● 步骤1 ─────────── ● 步骤2                        │
│  下载模板             上传数据                       │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │  📥 步骤1/2: 可下载模板获取数据，根据指引      │  │
│  │     编辑后再上传                               │  │
│  │                                               │  │
│  │  [下载模板] 按钮                               │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │                                               │  │
│  │          拖拽文件至此，或[选择文件上传]         │  │
│  │                                               │  │
│  │   仅支持上传小于30MB且不超过50,000条记录的     │  │
│  │   .xlsx文件                                   │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │  📋 解析结果（上传后显示）                     │  │
│  │  - 总行数: 100                                │  │
│  │  - 有效行: 95                                 │  │
│  │  - 错误行: 5                                  │  │
│  │  [查看错误详情]                                │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
├─────────────────────────────────────────────────────┤
│                        [取消]     [开始导入]         │
└─────────────────────────────────────────────────────┘
```

### 2.2 交互流程
1. 点击"导入"按钮 → 打开导入弹窗
2. 弹窗默认显示"下载模板"区域（步骤1高亮）
3. 用户点击"下载模板" → 下载xlsx文件
4. 用户关闭弹窗填写数据（或在弹窗外填写）
5. 再次打开弹窗 → 拖拽或选择文件上传
6. 系统解析 → 显示预览结果（步骤2高亮）
7. 用户点击"开始导入" → 执行导入
8. 显示最终结果，自动关闭弹窗

### 2.3 按钮状态
| 状态 | 取消按钮 | 开始导入按钮 |
|------|----------|--------------|
| 待上传 | 可点击 | 禁用（灰色） |
| 解析中 | 可点击 | 禁用（loading） |
| 解析完成 | 可点击 | 可点击 |
| 导入中 | 禁用 | loading状态 |
| 导入完成 | - | 变为"完成" |

### 2.4 文件校验规则
| 规则 | 值 | 错误提示 |
|------|-----|----------|
| 文件格式 | 仅.xlsx | "仅支持.xlsx格式文件" |
| 文件大小 | < 30MB | "文件大小超过30MB限制" |
| 记录数 | ≤ 50,000 | "记录数超过50,000条限制" |
| 表头匹配 | 必须与模板一致 | "表头与模板不匹配，请下载最新模板" |

---

## 3. 模板规范

### 3.1 模板文件命名
- 格式：`{业务名称}_导入模板.xlsx`
- 示例：`物料_导入模板.xlsx`

### 3.2 模板结构
| 行号 | 内容 | 说明 |
|------|------|------|
| 第1行 | 展示名称 | 中文列名，用户可见 |
| 第2行 | 字段标识 | 英文字段名，以_开头 |
| 第3行 | 填写说明 | 提示用户如何填写 |
| 第4-5行 | 示例数据 | 1-2行示例 |

### 3.3 必填列标记
- 必填列在展示名称后加 `*` 号
- 示例：`料号*`、`物料名称*`

---

## 4. 批量导入设计规范（重要）

### 4.1 设计目标
- 支持 **50,000条** 数据一次性导入
- 内存占用可控，**不能OOM**
- 导入过程可中断、可恢复
- 部分失败不影响已成功数据

### 4.2 分批处理策略
```
上传文件 → 解析到内存 → 分批校验 → 分批写入 → 返回结果
                ↓
        50,000条 ≈ 50批 x 1000条/批
```

#### 分批参数
| 参数 | 值 | 说明 |
|------|-----|------|
| BATCH_SIZE | 1000 | 每批写入条数 |
| MAX_ROWS | 50000 | 最大行数限制 |
| FILE_MAX_SIZE | 30MB | 文件大小限制 |

### 4.3 内存管理
```java
// ✅ 正确：流式解析，不一次性加载所有数据
try (InputStream is = file.getInputStream()) {
    // SAX方式解析，逐行处理
}

// ❌ 错误：一次性加载到内存
List<Row> allRows = readAllRows(file); // 可能OOM
```

### 4.4 事务设计原则

#### 4.4.1 分批独立事务
```java
// 每批一个独立事务，失败不影响其他批次
for (int i = 0; i < totalRows; i += BATCH_SIZE) {
    List<Row> batch = rows.subList(i, min(i + BATCH_SIZE, totalRows));
    try {
        batchInsert(batch); // 独立事务
        successCount += batch.size();
    } catch (Exception e) {
        errorCount += batch.size();
        log.error("Batch {} failed", i / BATCH_SIZE, e);
    }
}
```

#### 4.4.2 事务边界
- **解析阶段**：无事务（只读）
- **写入阶段**：每批一个事务
- **确认阶段**：整体提交（可选）

#### 4.4.3 幂等设计
- 基于 `checksum` 做幂等判断
- 相同文件重复上传返回相同结果
- 重复确认不会重复写入

### 4.5 错误处理策略
| 策略 | 说明 | 适用场景 |
|------|------|----------|
| SKIP | 跳过错误行，继续导入 | 默认策略 |
| STOP | 遇到错误立即停止 | 严格模式 |
| ROLLBACK | 回滚所有已导入数据 | 一致性要求高 |

### 4.6 进度反馈
```json
{
    "taskId": "task_123",
    "status": "IMPORTING",
    "progress": {
        "total": 50000,
        "processed": 25000,
        "success": 24500,
        "failed": 500,
        "percent": 50
    }
}
```

---

## 5. 校验规则

### 5.1 文件校验（上传时）
- 文件格式：仅支持 `.xlsx`
- 文件大小：不超过 30MB
- Sheet数量：至少1个
- 行数限制：不超过 50,000 行

### 5.2 表头校验（解析时）
- 第2行字段标识必须与模板完全一致
- 必填列不可缺失
- 列顺序必须与模板一致

### 5.3 数据校验（每行）
- 必填字段不能为空
- 字段长度不超过数据库限制
- 数值字段必须为有效数字
- 枚举字段必须在允许值范围内

### 5.4 业务校验（写入前）
- 唯一字段不可重复（如料号）
- 关联数据必须存在（如适用）

---

## 6. 接口设计

### 6.1 模板下载
```
GET /api/v1/{resource}/template
Response: application/octet-stream (文件流)
```

### 6.2 上传解析
```
POST /api/v1/{resource}/import
Content-Type: multipart/form-data
Request: file=xxx.xlsx
Response: {
    "checksum": "abc123",
    "totalRows": 50000,
    "successRows": 49500,
    "errorRows": 500,
    "errors": [...]
}
```

### 6.3 确认导入
```
POST /api/v1/{resource}/import/confirm
Request: {
    "checksum": "abc123",
    "skipDuplicates": true
}
Response: {
    "imported": 49000,
    "skipped": 500,
    "failed": 500
}
```

---

## 7. 前端实现要点

### 7.1 组件结构
```vue
<el-dialog title="批量导入物料" v-model="visible">
    <!-- 步骤条 -->
    <el-steps :active="currentStep">
        <el-step title="下载模板" />
        <el-step title="上传数据" />
    </el-steps>

    <!-- 步骤1：下载模板 -->
    <div v-if="currentStep === 0">
        <p>可下载模板获取数据，根据指引编辑后再上传</p>
        <el-button @click="downloadTemplate">下载模板</el-button>
    </div>

    <!-- 步骤2：上传文件 -->
    <div v-if="currentStep === 1">
        <el-upload drag :before-upload="beforeUpload">
            <p>拖拽文件至此，或选择文件上传</p>
            <p>仅支持上传小于30MB且不超过50,000条记录的.xlsx文件</p>
        </el-upload>
    </div>

    <!-- 解析结果 -->
    <div v-if="parseResult">
        总行数: {{ parseResult.totalRows }}
        有效行: {{ parseResult.successRows }}
        错误行: {{ parseResult.errorRows }}
    </div>

    <!-- 底部按钮 -->
    <template #footer>
        <el-button @click="cancel">取消</el-button>
        <el-button type="primary" :disabled="!canImport" @click="startImport">
            开始导入
        </el-button>
    </template>
</el-dialog>
```

### 7.2 大文件上传
- 使用 `FormData` 上传
- 显示上传进度（可选）
- 超时设置：5分钟

### 7.3 结果展示
- 解析完成后显示统计信息
- 错误行可展开查看详情
- 导入完成后自动刷新列表

---

## 8. 后端实现要点

### 8.1 解析器设计
```java
public class MaterialExcelParser {
    // 使用SAX方式解析，避免一次性加载
    public ParseResult<Material> parse(Path file) {
        // 流式解析，逐行处理
    }
}
```

### 8.2 Service设计
```java
@Service
public class MaterialImportService {
    private static final int BATCH_SIZE = 1000;

    public ImportResult confirm(String checksum) {
        List<Material> staged = getStagedData(checksum);

        int success = 0;
        int failed = 0;

        // 分批处理
        for (int i = 0; i < staged.size(); i += BATCH_SIZE) {
            List<Material> batch = staged.subList(i, min(i + BATCH_SIZE, staged.size()));
            try {
                batchInsert(batch);
                success += batch.size();
            } catch (Exception e) {
                failed += batch.size();
            }
        }

        return new ImportResult(success, failed);
    }

    @Transactional
    protected void batchInsert(List<Material> batch) {
        materialMapper.insertBatch(batch);
    }
}
```

### 8.3 检查清单
每个导入功能开发前必须确认：
- [ ] 模板文件已创建并放到正确位置
- [ ] 模板下载接口可用
- [ ] 弹窗UI符合规范（步骤条、拖拽上传）
- [ ] 文件校验逻辑已实现（格式、大小、行数）
- [ ] 表头校验逻辑已实现
- [ ] 数据校验规则已定义
- [ ] 分批处理逻辑已实现（BATCH_SIZE=1000）
- [ ] 事务边界已明确（每批独立事务）
- [ ] 错误提示信息清晰
- [ ] 重复数据处理策略已确认
- [ ] 内存占用已优化（流式解析）

---

**最后更新**: 2026-09-04
**版本**: 1.1.0
