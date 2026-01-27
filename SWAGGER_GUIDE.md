# OpenAPI (Swagger UI) 使用指南

## 🎯 已添加的功能

✅ **Springdoc OpenAPI 2.3.0** - 自动生成API文档
✅ **Swagger UI** - 可视化API测试界面
✅ **OpenAPI注解** - 所有控制器已添加详细注解
✅ **HTTP测试文件** - IntelliJ IDEA HTTP Client支持

## 📡 访问方式

### 1. Swagger UI (推荐)

启动应用后,在浏览器访问:

```
http://localhost:8080/swagger-ui.html
```

**功能特性**:
- 📋 查看所有API端点
- 🧪 直接在浏览器测试接口
- 📝 查看请求/响应示例
- 🔍 查看参数说明
- ⚡ 实时测试和调试

### 2. OpenAPI JSON文档

获取标准OpenAPI 3.0 JSON格式文档:

```
http://localhost:8080/api-docs
```

可以导入到Postman、Insomnia等工具。

### 3. IntelliJ IDEA HTTP Client

使用项目根目录的 `api-test.http` 文件:

1. 在IntelliJ IDEA中打开 `api-test.http`
2. 点击请求左侧的绿色▶️按钮
3. 查看响应结果

### 4. 命令行 (curl)

```bash
# 查看API文档
curl http://localhost:8080/api-docs | jq '.'

# 测试健康检查
curl http://localhost:8080/actuator/health | jq '.'
```

## 📚 API分组

### 审核管理 (Audit Management)
- `POST /api/v1/audit/content` - 单条内容审核
- `POST /api/v1/audit/batch` - 批量审核
- `GET /api/v1/audit/job/{jobId}` - 查询任务状态
- `GET /api/v1/audit/result/{postId}` - 获取审核结果

### 文件管理 (File Management)
- `POST /api/v1/audit/upload` - 上传Excel文件
- `GET /api/v1/audit/download/{jobId}` - 下载审核结果

## 🧪 Swagger UI 使用教程

### 步骤1: 打开Swagger UI

1. 确保应用正在运行
2. 浏览器访问: http://localhost:8080/swagger-ui.html

### 步骤2: 浏览API

- 页面会显示所有API分组
- 点击分组名称展开/折叠
- 查看每个接口的详细信息

### 步骤3: 测试接口

#### 示例: 测试单条内容审核

1. 找到 "审核管理" 分组
2. 点击 `POST /api/v1/audit/content`
3. 点击 "Try it out" 按钮
4. 在Request body中输入:
```json
{
  "url": "https://www.xiaohongshu.com/explore/test123",
  "forceRefresh": false
}
```
5. 点击 "Execute" 按钮
6. 查看响应结果

#### 示例: 上传Excel文件

1. 找到 "文件管理" 分组
2. 点击 `POST /api/v1/audit/upload`
3. 点击 "Try it out"
4. 点击 "Choose File" 选择Excel文件
5. 点击 "Execute"
6. 查看返回的任务ID

#### 示例: 查询任务状态

1. 复制上一步返回的jobId
2. 找到 `GET /api/v1/audit/job/{jobId}`
3. 点击 "Try it out"
4. 在jobId参数框输入任务ID
5. 点击 "Execute"
6. 查看任务进度

## 📝 API文档说明

每个API接口包含:

- **Summary**: 接口简要说明
- **Description**: 详细描述
- **Parameters**: 参数说明
  - Name: 参数名
  - Type: 数据类型
  - Required: 是否必填
  - Description: 参数说明
- **Request Body**: 请求体示例
- **Responses**: 响应示例
  - 200: 成功响应
  - 400: 客户端错误
  - 500: 服务器错误

## 🔧 配置说明

配置位置: `src/main/resources/application.yml`

```yaml
springdoc:
  api-docs:
    path: /api-docs              # API文档JSON路径
    enabled: true                # 启用API文档
  swagger-ui:
    path: /swagger-ui.html       # Swagger UI路径
    enabled: true                # 启用Swagger UI
    tags-sorter: alpha           # 标签排序方式
    operations-sorter: alpha     # 操作排序方式
    display-request-duration: true  # 显示请求耗时
  show-actuator: true            # 显示Actuator端点
```

## 🚀 快速开始

### 1. 重新编译并启动

```bash
# 停止旧进程
lsof -ti:8080 | xargs kill

# 重新启动
mvn clean spring-boot:run
```

### 2. 访问Swagger UI

浏览器打开: http://localhost:8080/swagger-ui.html

### 3. 测试第一个接口

1. 展开 "审核管理"
2. 点击 `POST /api/v1/audit/content`
3. 点击 "Try it out"
4. 输入测试数据
5. 点击 "Execute"

## 📊 与其他工具对比

| 工具 | 优点 | 缺点 |
|------|------|------|
| **Swagger UI** | ✅ 浏览器访问<br>✅ 可视化界面<br>✅ 自动生成 | ❌ 需要依赖 |
| **Postman** | ✅ 功能强大<br>✅ 团队协作 | ❌ 需要安装<br>❌ 手动维护 |
| **HTTP Client** | ✅ IDE集成<br>✅ 版本控制 | ❌ 需要IntelliJ |
| **curl** | ✅ 轻量级<br>✅ 脚本友好 | ❌ 命令复杂 |

## 💡 最佳实践

### 1. 使用Swagger UI进行:
- ✅ 快速接口测试
- ✅ API文档查看
- ✅ 参数验证

### 2. 使用HTTP文件进行:
- ✅ 自动化测试
- ✅ 版本控制
- ✅ 团队共享

### 3. 使用curl/脚本进行:
- ✅ CI/CD集成
- ✅ 性能测试
- ✅ 批量操作

## 🐛 常见问题

### Q1: 无法访问Swagger UI?

**检查清单**:
```bash
# 1. 确认应用已启动
curl http://localhost:8080/actuator/health

# 2. 检查端口
lsof -i :8080

# 3. 查看日志
tail -f /tmp/xhs_audit.log | grep swagger
```

### Q2: API列表为空?

**原因**: Controller未被扫描

**解决**:
```java
// 确保Controller在正确的包下
package com.xhs.audit.controller;
```

### Q3: 请求失败?

**检查**:
1. 参数格式是否正确
2. Content-Type是否匹配
3. 查看浏览器Console错误
4. 查看应用日志

## 📖 扩展阅读

- [Springdoc官方文档](https://springdoc.org/)
- [OpenAPI规范](https://swagger.io/specification/)
- [Swagger UI使用指南](https://swagger.io/tools/swagger-ui/)

## 🎉 完成

现在你可以:

1. ✅ 在浏览器测试所有API
2. ✅ 查看完整的API文档
3. ✅ 导出OpenAPI JSON给其他工具
4. ✅ 使用HTTP文件进行自动化测试

**访问地址**: http://localhost:8080/swagger-ui.html

祝测试愉快! 🚀
