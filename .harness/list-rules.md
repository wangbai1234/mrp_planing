# MRP 列表页面开发规范

> 本文件定义了列表页面的分页、搜索、筛选功能规范，所有涉及列表的开发必须遵循。
> 搜索框布局和尺寸规范参考 `AGENTS.md` 中的"筛选框尺寸"部分。

---

## 1. 核心原则

**所有列表都必须支持：**
- ✅ 分页（服务端分页）
- ✅ 搜索（关键词搜索）
- ✅ 筛选（条件筛选）

**禁止：**
- ❌ 前端分页（数据量大时性能差）
- ❌ 不分页直接加载所有数据
- ❌ 没有搜索功能的列表

---

## 2. 分页规范

### 2.1 默认分页参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `page` | 1 | 页码（从1开始） |
| `pageSize` | 20 | 每页条数 |

### 2.2 特殊场景分页

| 场景 | 默认pageSize | 说明 |
|------|--------------|------|
| 历史版本列表 | 3 | 版本数量少，但需要分页展示 |
| 详情列表 | 20 | 正常分页 |
| 弹窗选择列表 | 10 | 弹窗空间有限 |

### 2.3 分页响应格式

```json
{
  "success": true,
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20,
    "totalPages": 5
  }
}
```

### 2.4 前端分页组件

```vue
<el-pagination
  v-model:current-page="page"
  v-model:page-size="pageSize"
  :page-sizes="[3, 10, 20, 50, 100]"
  :total="total"
  layout="total, sizes, prev, pager, next, jumper"
  @size-change="handleSizeChange"
  @current-change="handlePageChange"
/>
```

---

## 3. 搜索和筛选规范

**布局和尺寸遵循 `AGENTS.md` 规范：**
- 搜索框：`width: 220px`
- 下拉筛选：`width: 140px`
- 搜索框放在列表上方右侧

### 3.1 搜索行为

- 支持回车搜索
- 支持点击搜索按钮
- 支持清空后自动搜索
- 搜索时重置页码到第1页

---

## 4. 后端API规范

### 4.1 通用分页查询参数

```java
@GetMapping("/list")
public ResponseEntity<ApiResponse<PageResult<T>>> list(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int pageSize,
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) String status,
    // 其他筛选条件...
) {
    // ...
}
```

### 4.2 PageResult 封装

```java
public record PageResult<T>(
    List<T> items,
    long total,
    int page,
    int pageSize,
    int totalPages
) {
    public static <T> PageResult<T> of(List<T> items, long total, int page, int pageSize) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PageResult<>(items, total, page, pageSize, totalPages);
    }
}
```

### 4.3 MyBatis 分页查询

```xml
<select id="selectPage" resultType="...">
    SELECT * FROM table
    <where>
        <if test="keyword != null and keyword != ''">
            AND (field1 LIKE CONCAT('%', #{keyword}, '%')
            OR field2 LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        <if test="status != null and status != ''">
            AND status = #{status}
        </if>
    </where>
    ORDER BY id DESC
    LIMIT #{offset}, #{pageSize}
</select>

<select id="countPage" resultType="long">
    SELECT COUNT(*) FROM table
    <where>
        <if test="keyword != null and keyword != ''">
            AND (field1 LIKE CONCAT('%', #{keyword}, '%')
            OR field2 LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        <if test="status != null and status != ''">
            AND status = #{status}
        </if>
    </where>
</select>
```

---

## 5. 前端代码规范

### 5.1 分页状态

```typescript
const page = ref(1)
const pageSize = ref(20)  // 或特殊场景的默认值
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
```

### 5.2 加载数据函数

```typescript
async function loadData() {
  loading.value = true
  try {
    const res = await get<any>('/list', {
      params: {
        page: page.value,
        pageSize: pageSize.value,
        keyword: keyword.value,
        // 其他筛选条件
      }
    })
    items.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}
```

### 5.3 分页事件处理

```typescript
function handleSizeChange(val: number) {
  pageSize.value = val
  page.value = 1
  loadData()
}

function handlePageChange(val: number) {
  page.value = val
  loadData()
}

function handleSearch() {
  page.value = 1
  loadData()
}
```

---

## 6. 检查清单

开发列表页面时，必须确认：

- [ ] 使用服务端分页，不使用前端分页
- [ ] 分页参数：page、pageSize
- [ ] 搜索功能：keyword + 回车/按钮搜索
- [ ] 筛选功能：按业务字段筛选
- [ ] 搜索/筛选时重置页码到第1页
- [ ] 分页组件显示：total、sizes、prev、pager、next、jumper
- [ ] 空数据时显示空状态提示
- [ ] 加载时显示loading状态
- [ ] 搜索框宽度：220px
- [ ] 下拉筛选宽度：140px

---

**最后更新**: 2026-09-07
**版本**: 1.0.0
