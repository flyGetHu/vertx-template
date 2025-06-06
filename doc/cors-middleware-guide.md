# CORS中间件使用指南

## 概述

本项目实现了完整的CORS（跨域资源共享）中间件系统，支持灵活的配置和模块化管理。

## 架构设计

### 核心组件

1. **Middleware接口** - 统一的中间件规范
2. **MiddlewareChain** - 中间件链管理器
3. **CorsMiddleware** - CORS中间件实现
4. **GlobalMiddleware** - 全局中间件管理器

### 执行流程

```
请求 → CorsMiddleware(order=10) → 其他中间件 → 路由处理器 → 响应
```

## 配置说明

### config.yml配置

```yaml
cors:
  enabled: true                    # 是否启用CORS
  allowed_origins: "*"             # 允许的源（生产环境请设置具体域名）
  allowed_methods:                 # 允许的HTTP方法
    - GET
    - POST
    - PUT
    - DELETE
    - OPTIONS
  allowed_headers:                 # 允许的请求头
    - Content-Type
    - Authorization
    - Access-Control-Allow-Method
    - Access-Control-Allow-Origin
    - Access-Control-Allow-Credentials
  allow_credentials: false         # 是否允许发送凭证
  max_age: 86400                  # 预检请求缓存时间（秒）
```

### 配置参数详解

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | true | 是否启用CORS中间件 |
| `allowed_origins` | string | "*" | 允许的源，支持通配符或具体域名 |
| `allowed_methods` | array | [GET,POST,PUT,DELETE,OPTIONS] | 允许的HTTP方法 |
| `allowed_headers` | array | [Content-Type,Authorization] | 允许的请求头 |
| `allow_credentials` | boolean | false | 是否允许发送Cookie等凭证 |
| `max_age` | integer | 86400 | 预检请求缓存时间（秒） |

## 使用方法

### 1. 基本使用

CORS中间件已自动集成到项目中，无需额外配置即可使用。

### 2. 自定义配置

修改 `src/main/resources/config.yml` 中的CORS配置：

```yaml
cors:
  enabled: true
  allowed_origins: "https://example.com"  # 只允许特定域名
  allow_credentials: true                  # 允许发送凭证
```

### 3. 禁用CORS

```yaml
cors:
  enabled: false
```

### 4. 生产环境配置建议

```yaml
cors:
  enabled: true
  allowed_origins: "https://yourdomain.com"  # 设置具体域名
  allowed_methods:
    - GET
    - POST
    - PUT
    - DELETE
  allowed_headers:
    - Content-Type
    - Authorization
  allow_credentials: true
  max_age: 3600  # 1小时
```

## 测试验证

### 1. 启动服务

```bash
./gradlew run
```

### 2. 测试CORS功能

使用浏览器或工具发送跨域请求：

```javascript
// 前端测试代码
fetch('http://localhost:8888/api/test/cors', {
  method: 'GET',
  headers: {
    'Content-Type': 'application/json'
  }
})
.then(response => response.json())
.then(data => console.log(data));
```

### 3. 检查响应头

正确配置的CORS响应应包含以下头部：

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
```

## 扩展开发

### 添加新的中间件

1. 实现 `Middleware` 接口：

```java
public class CustomMiddleware implements Middleware {
    @Override
    public String getName() {
        return "CustomMiddleware";
    }
    
    @Override
    public int getOrder() {
        return 50; // 设置执行顺序
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public void handle(RoutingContext context) {
        // 处理逻辑
        context.next();
    }
}
```

2. 在 `AppModule` 中注册：

```java
@Provides
@Singleton
public CustomMiddleware provideCustomMiddleware() {
    return new CustomMiddleware();
}
```

3. 在 `GlobalMiddleware` 中添加到链中：

```java
middlewareChain.register(customMiddleware);
```

## 常见问题

### Q: CORS不生效怎么办？

A: 检查以下几点：
1. 确认 `cors.enabled` 为 `true`
2. 检查 `allowed_origins` 是否包含请求源
3. 确认中间件注册顺序正确
4. 查看服务器日志是否有错误信息

### Q: 如何支持多个域名？

A: 可以使用正则表达式或在代码中动态判断：

```java
// 在CorsMiddleware中自定义逻辑
String origin = context.request().getHeader("Origin");
if (isAllowedOrigin(origin)) {
    context.response().putHeader("Access-Control-Allow-Origin", origin);
}
```

### Q: 预检请求失败怎么办？

A: 确保：
1. 允许 `OPTIONS` 方法
2. 设置正确的 `allowed_headers`
3. 检查 `max_age` 配置

## 性能优化

1. **合理设置缓存时间**：`max_age` 设置为合适的值，减少预检请求
2. **精确配置域名**：避免使用通配符 `*`，提高安全性
3. **最小化允许的方法和头部**：只允许必要的HTTP方法和请求头

## 安全建议

1. **生产环境不要使用通配符**：`allowed_origins` 应设置为具体域名
2. **谨慎启用凭证**：`allow_credentials` 只在必要时启用
3. **定期审查配置**：确保CORS配置符合安全要求
4. **监控跨域请求**：记录和分析跨域请求日志