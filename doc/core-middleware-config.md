# 核心中间件配置文档

## 概述

核心中间件已从 `GlobalMiddleware` 迁移到 `middleware/core/` 目录下，采用统一的中间件接口和管理方式。

## 架构优化

### 优化前 vs 优化后

| 方面       | 优化前                   | 优化后                 |
| ---------- | ------------------------ | ---------------------- |
| 代码组织   | 集中在GlobalMiddleware   | 分散到独立的中间件类   |
| 接口统一性 | 直接使用Vert.x Handler   | 统一实现Middleware接口 |
| 可测试性   | 难以单独测试             | 每个中间件可独立测试   |
| 可配置性   | 硬编码配置               | 基于配置文件的灵活配置 |
| 扩展性     | 需要修改GlobalMiddleware | 只需添加新的中间件类   |

## 核心中间件

### 1. CorsMiddleware - CORS中间件

**功能**：处理跨域资源共享(CORS)配置

**执行顺序**：10（最高优先级）

**配置示例**：
```json
{
  "cors": {
    "enabled": true,
    "allowed_origins": "*",
    "allowed_headers": ["Content-Type", "Authorization", "X-Requested-With"],
    "allowed_methods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
  }
}
```

**配置说明**：
- `enabled`: 是否启用CORS中间件
- `allowed_origins`: 允许的源，支持通配符
- `allowed_headers`: 允许的请求头
- `allowed_methods`: 允许的HTTP方法

### 2. BodyHandlerMiddleware - Body处理器中间件

**功能**：处理HTTP请求体的解析和缓存

**执行顺序**：20

**配置示例**：
```json
{
  "body_handler": {
    "enabled": true,
    "body_limit": 1048576,
    "uploads_directory": "uploads",
    "delete_uploaded_files_on_end": true
  }
}
```

**配置说明**：
- `enabled`: 是否启用Body处理器中间件
- `body_limit`: 请求体大小限制（字节），默认1MB
- `uploads_directory`: 文件上传目录
- `delete_uploaded_files_on_end`: 请求结束后是否删除上传的文件

### 3. RequestLoggerMiddleware - 请求日志中间件

**功能**：记录HTTP请求的详细信息和处理时间

**执行顺序**：30

**配置示例**：
```json
{
  "request_log": {
    "enabled": true,
    "log_request_details": true,
    "log_response_details": true
  }
}
```

**配置说明**：
- `enabled`: 是否启用请求日志中间件
- `log_request_details`: 是否记录请求详情
- `log_response_details`: 是否记录响应详情

## 中间件执行流程

```
请求 → CorsMiddleware(10) → BodyHandlerMiddleware(20) → RequestLoggerMiddleware(30) → 注解处理层 → 控制器
```

### 执行顺序说明

1. **CorsMiddleware (order=10)**：最先执行，处理CORS预检请求
2. **BodyHandlerMiddleware (order=20)**：解析请求体，为后续中间件提供body数据
3. **RequestLoggerMiddleware (order=30)**：记录请求开始时间，设置响应结束处理器

## 使用方式

### 1. 自动注册

核心中间件会通过 `GlobalMiddleware` 自动注册到中间件链中：

```java
@Inject
public GlobalMiddleware(
    // ... 其他参数
    CorsMiddleware corsMiddleware,
    BodyHandlerMiddleware bodyHandlerMiddleware,
    RequestLoggerMiddleware requestLoggerMiddleware) {
    // 构造函数会自动注入所有核心中间件
}

public void register() {
    // 自动注册所有启用的核心中间件
    registerCoreMiddlewares();
}
```

### 2. 手动控制

如果需要手动控制中间件的注册，可以直接使用 `MiddlewareChain`：

```java
@Inject
private MiddlewareChain middlewareChain;

@Inject
private CorsMiddleware corsMiddleware;

public void customRegister() {
    if (corsMiddleware.isEnabled()) {
        middlewareChain.register(corsMiddleware);
    }
}
```

## 扩展新的核心中间件

### 1. 创建中间件类

```java
@Singleton
public class CustomMiddleware implements Middleware {

    @Inject
    public CustomMiddleware(JsonObject config) {
        // 初始化逻辑
    }

    @Override
    public MiddlewareResult handle(RoutingContext context) {
        // 处理逻辑
        return MiddlewareResult.success("处理完成");
    }

    @Override
    public String getName() {
        return "CustomMiddleware";
    }

    @Override
    public int getOrder() {
        return 40; // 设置执行顺序
    }
}
```

### 2. 添加依赖注入配置

在 `AppModule.java` 中添加：

```java
@Provides
@Singleton
public CustomMiddleware provideCustomMiddleware(JsonObject config) {
    return new CustomMiddleware(config);
}
```

### 3. 在GlobalMiddleware中注册

```java
@Inject
public GlobalMiddleware(
    // ... 其他参数
    CustomMiddleware customMiddleware) {
    // ...
}

private void registerCoreMiddlewares() {
    // ... 其他中间件注册

    if (customMiddleware.isEnabled()) {
        middlewareChain.register(customMiddleware);
        logger.debug("已注册自定义中间件");
    }
}
```

## 测试

### 单元测试示例

```java
@Test
public void testCorsMiddleware() {
    JsonObject config = new JsonObject()
        .put("cors", new JsonObject()
            .put("enabled", true)
            .put("allowed_origins", "*"));

    CorsMiddleware middleware = new CorsMiddleware(config);

    // 创建模拟的RoutingContext
    RoutingContext context = mock(RoutingContext.class);

    // 执行中间件
    MiddlewareResult result = middleware.handle(context);

    // 验证结果
    assertTrue(result.isSuccess());
    assertEquals("CorsMiddleware", middleware.getName());
    assertEquals(10, middleware.getOrder());
}
```

## 性能优化

### 1. 条件执行

每个中间件都支持通过配置禁用：

```json
{
  "cors": { "enabled": false },
  "body_handler": { "enabled": false },
  "request_log": { "enabled": false }
}
```

禁用的中间件不会被注册到中间件链中，避免不必要的性能开销。

### 2. 执行顺序优化

中间件按照 `getOrder()` 返回的数值顺序执行，数值越小优先级越高：

- CORS (10) - 必须最先执行
- Body处理器 (20) - 在CORS之后，为后续中间件提供body数据
- 请求日志 (30) - 在基础处理完成后记录

### 3. 异常处理

每个中间件都有完善的异常处理机制，确保单个中间件的异常不会影响整个请求处理流程。

## 总结

通过将核心中间件迁移到 `middleware/core/` 目录：

- ✅ **统一架构**：所有中间件都实现相同的接口
- ✅ **模块化**：每个中间件职责单一，易于维护
- ✅ **可测试**：每个中间件可以独立测试
- ✅ **可配置**：基于配置文件的灵活配置
- ✅ **可扩展**：添加新中间件无需修改现有代码
- ✅ **高性能**：支持条件执行和优化的执行顺序
