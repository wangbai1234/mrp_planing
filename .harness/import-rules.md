# MRP Excel 导入开发规范

> 本文件定义了 Excel 导入功能的开发规范，所有涉及导入功能的开发必须遵循。

---

## 1. 模板生成规范

### 1.1 模板结构

```
行1: 表头（中文列名，加粗，浅蓝背景）
行2: 提示行（填写说明，必填列红色，非必填列灰色斜体）
行3+: 示例数据
```

### 1.2 列定义

使用 `TemplateColumn` record 定义列：

```java
public record TemplateColumn(
    String displayName,  // 显示名称（中文）
    String fieldName,    // 字段标识（英文）
    String tip,          // 填写提示
    boolean required     // 是否必填
) {}
```

### 1.3 月份列动态生成

经营计划模板的月份列必须从当前月份开始，动态生成未来N个月：

```java
YearMonth currentMonth = YearMonth.now();
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
for (int i = 0; i < monthCount; i++) {
    YearMonth month = currentMonth.plusMonths(i);
    String monthStr = month.format(formatter);
    columns.add(new TemplateColumn(monthStr, "fcst_" + monthStr, "请输入数量", false));
}
```

### 1.4 自动计算列

系统自动计算的列（如 fcst-total）在模板中提示"系统自动计算，无需填写"。

---

## 2. Excel 解析规范

### 2.1 行索引定义

```java
private static final int HEADER_ROW = 0;      // 表头行
private static final int DATA_START_ROW = 2;   // 数据起始行（跳过表头和提示行）
```

### 2.2 列索引定义

必须明确定义每列的索引，与模板列顺序一致：

```java
private static final int COL_FACTORY = 0;
private static final int COL_FORM_TYPE = 1;
private static final int COL_MATERIAL_ID = 2;
// ... 依此类推
private static final int COL_FCST_START = 5;   // 月份列起始索引
private static final int FCST_MONTHS = 6;       // 月份列数量
```

### 2.3 原始行数据保存

**必须保存原始行数据**，用于生成错误报告：

```java
// 在 processRow 方法开头保存
List<String> rawRow = new ArrayList<>();
int totalCols = COL_FCST_START + FCST_MONTHS + 1; // 固定列数
for (int i = 0; i < totalCols; i++) {
    if (i < currentRowData.size()) {
        Object cell = currentRowData.get(i);
        rawRow.add(cell != null ? cell.toString() : "");
    } else {
        rawRow.add(""); // 稀疏Excel需要填充空列
    }
}
rawRows.add(rawRow);
```

**关键点**：Excel 稀疏格式会导致空单元格没有 XML 元素，必须手动填充到固定列数。

### 2.4 表头保存

```java
if (currentRow == HEADER_ROW) {
    parseHeader();
    headers.clear();
    for (Object cell : currentRowData) {
        headers.add(cell != null ? cell.toString() : "");
    }
    return;
}
```

---

## 3. 数据验证规范

### 3.1 验证顺序

1. 空行检查 → 跳过
2. 必填字段检查 → 错误
3. 格式检查（如料号不能是日期/科学计数法）→ 错误
4. 引用检查（如料号是否存在于物料表）→ 错误
5. 业务规则检查 → 错误/警告

### 3.2 料号验证

```java
// 1. 格式验证
if (materialId.matches("\\d{4}-\\d{2}-\\d{2}.*") || materialId.matches("\\d+\\.\\d+E.*")) {
    errors.add(ParseError.of(row, "material_id", materialId, 
        "INVALID_MATERIAL_ID", "料号被Excel转为日期或科学计数法"));
    return;
}

// 2. 存在性验证
Material material = materialMapper.selectByCode(materialId);
if (material == null) {
    errors.add(ParseError.of(row, "material_id", materialId, 
        "MATERIAL_NOT_FOUND", "未找到该料号，请先维护再导入"));
    return;
}
```

### 3.3 数量验证

支持小数，使用 `BigDecimal`：

```java
BigDecimal qty = BigDecimal.ZERO;
if (qtyStr != null && !qtyStr.isBlank()) {
    try {
        qty = new BigDecimal(qtyStr);
    } catch (NumberFormatException e) {
        errors.add(ParseError.of(row, "fcst_" + month, qtyStr, 
            "INVALID_QTY", "数量格式错误"));
        continue;
    }
}
```

### 3.4 引用数据自动填充

从主数据表引用的字段（如名称、项目、平台）自动填充，不需要用户填写：

```java
String materialName = material.materialName();  // 从物料表获取
String project = material.projectModel();       // 从物料表获取
String platform = material.specModel();         // 从物料表获取
```

---

## 4. ParseResult 规范

### 4.1 字段定义

```java
public record ParseResult<T>(
    List<T> rows,                    // 成功解析的行
    List<ParseError> errors,         // 错误列表
    List<String> recognizedMonths,   // 识别到的月份
    int totalRows,                   // 总行数
    int successRows,                 // 成功行数
    int errorRows,                   // 错误行数
    List<List<String>> rawRows,      // 原始行数据（用于错误报告）
    List<String> headers             // 表头（用于错误报告）
) {}
```

### 4.2 错误行统计

`errorRows` 统计有错误的原始行数（不是错误数量），一行可能有多个错误。

---

## 5. 错误报告规范

### 5.1 错误报告结构

```
列1-N: 原始数据列（与模板一致）
列N+1: 错误原因（红色字体）
```

### 5.2 生成方法

```java
public byte[] generateErrorReport(
    List<String> headers,           // 表头
    List<List<String>> rawRows,     // 原始行数据
    List<ParseError> errors         // 错误列表
) throws IOException {
    // 1. 构建错误映射: rowNumber -> 错误信息
    Map<Integer, String> errorMap = new HashMap<>();
    for (ParseError error : errors) {
        String existing = errorMap.get(error.rowNumber());
        String msg = error.column() + ": " + error.message();
        errorMap.put(error.rowNumber(), 
            existing != null ? existing + "; " + msg : msg);
    }
    
    // 2. 写入表头 + "错误原因"
    // 3. 写入数据行 + 对应的错误信息
}
```

### 5.3 行号计算

Excel 行号 = 数据索引 + DATA_START_ROW + 1

```java
int rowNum = rowIdx + DATA_START_ROW + 1; // rowIdx从0开始
String errorMsg = errorMap.get(rowNum);
```

---

## 6. 前端展示规范

### 6.1 错误提示

当 `errorRows > 0` 时，显示红色错误提示：

```vue
<div v-if="stagingResult && stagingResult.errorRows > 0" class="error-alert">
  <el-alert type="error" :closable="false" show-icon>
    <template #title>
      <span>发现 {{ stagingResult.errorRows }} 条错误数据，无法导入</span>
    </template>
    <template #default>
      <div class="error-actions">
        <span>请下载错误报告，修正后重新导入</span>
        <el-button type="primary" size="small" @click="downloadErrorReport">
          下载错误数据
        </el-button>
      </div>
    </template>
  </el-alert>
</div>
```

### 6.2 导入按钮禁用

有错误时禁用导入按钮：

```vue
<el-button 
  type="primary" 
  @click="handleConfirm" 
  :loading="confirmLoading"
  :disabled="!stagingResult || stagingResult.errorRows > 0"
>
  开始导入
</el-button>
```

### 6.3 数据预览表格

显示前10行数据 + 所有错误行：

```vue
<el-table :data="stagingResult.previewRows" size="small" border max-height="300">
  <el-table-column prop="type" label="状态" width="80">
    <template #default="{ row }">
      <el-tag :type="row.type === 'error' ? 'danger' : 'success'" size="small">
        {{ row.type === 'error' ? '错误' : '成功' }}
      </el-tag>
    </template>
  </el-table-column>
  <!-- 其他列... -->
  <el-table-column prop="message" label="错误信息" min-width="150" />
</el-table>
```

### 6.4 API 返回格式

```json
{
  "taskId": 1,
  "totalRows": 100,
  "successRows": 95,
  "errorRows": 5,
  "recognizedMonths": ["2026-09", "2026-10", ...],
  "errors": [
    {
      "rowNumber": 3,
      "column": "material_id",
      "originalValue": "BAD_CODE",
      "errorCode": "MATERIAL_NOT_FOUND",
      "message": "未找到该料号，请先维护再导入"
    }
  ],
  "previewRows": [
    {"type": "error", "row": 3, "materialId": "BAD_CODE", "message": "..."},
    {"type": "success", "factoryCode": "F001", "materialId": "xxx", ...}
  ]
}
```

---

## 7. 错误码规范

| 错误码 | 含义 | 提示信息 |
|--------|------|----------|
| `INVALID_MATERIAL_ID` | 料号格式错误 | 料号被Excel转为日期或科学计数法 |
| `MATERIAL_NOT_FOUND` | 料号不存在 | 未找到该料号，请先维护再导入 |
| `INVALID_MONTH` | 月份格式错误 | 无法识别月份 |
| `INVALID_QTY` | 数量格式错误 | 数量格式错误 |
| `REQUIRED_FIELD` | 必填字段为空 | 请填写XXX |
| `HEADER_MISMATCH` | 表头不匹配 | 表头与模板不匹配，请下载最新模板 |
| `ROW_LIMIT_EXCEEDED` | 超过行数限制 | 记录数超过XXX条限制 |

---

## 8. 数据库类型规范

| 数据类型 | Java 类型 | MySQL 类型 | 说明 |
|----------|-----------|------------|------|
| 数量（整数） | `Long` | `BIGINT` | 如库存数量 |
| 数量（小数） | `BigDecimal` | `DECIMAL(18,6)` | 如经营计划数量 |
| 金额 | `BigDecimal` | `DECIMAL(18,2)` | 如有金额字段 |
| 比例 | `BigDecimal` | `DECIMAL(10,6)` | 如有比例字段 |

**禁止使用 `float` / `double` 做业务计算。**

---

## 9. 检查清单

开发导入功能时，必须确认：

- [ ] 模板列顺序与解析器列索引一致
- [ ] 数据起始行正确（跳过表头和提示行）
- [ ] 保存了原始行数据（rawRows）和表头（headers）
- [ ] 稀疏Excel已填充空列到固定列数
- [ ] 数量字段使用 BigDecimal
- [ ] 错误报告的"错误原因"在最后一列
- [ ] 前端有错误时显示错误提示 + 下载按钮
- [ ] 前端有错误时禁用导入按钮
- [ ] 错误码和提示信息符合规范

---

**最后更新**: 2026-09-07
**版本**: 1.0.0
