# 单机限流器使用指南

本项目实现了一个基于注解的单机限流器，支持多种限流算法和维度，可以有效防止接口被恶意调用或过度使用。

## 功能特性

### 限流算法
- **固定窗口限流** (`FIXED_WINDOW`): 在固定时间窗口内限制请求数量
- **滑动窗口限流** (`SLIDING_WINDOW`): 基于滑动时间窗口的更精确限流
- **令牌桶限流** (`TOKEN_BUCKET`): 支持突发流量的令牌桶算法（预留接口）
- **漏桶限流** (`LEAKY_BUCKET`): 平滑流量的漏桶算法（预留接口）

### 限流维度
- **IP限流** (`IP`): 基于客户端IP地址限流
- **用户限流** (`USER`): 基于用户ID限流（需要在请求头中提供 `X-User-Id`）
- **全局限流** (`GLOBAL`): 所有请求共享限流配额
- **自定义限流** (`CUSTOM`): 基于自定义键进行限流，支持SpEL表达式

### 核心组件
- **@RateLimit注解**: 声明式限流配置
- **RateLimitInterceptor**: 限流拦截器，自动处理限流逻辑
- **RateLimitManager**: 限流管理器，统一管理不同类型的限流器
- **RateLimitException**: 限流异常，当请求超过限制时抛出
- **GlobalExceptionHandler**: 全局异常处理器，统一处理限流异常

## 使用方法

### 1. 基础使用

在控制器方法上添加 `@RateLimit` 注解：

```java
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @GetMapping("/test")
    @RateLimit(
        limit = 10,                    // 限制数量
        window = 1,                    // 时间窗口
        timeUnit = TimeUnit.MINUTES,   // 时间单位
        dimension = RateLimitDimension.IP,  // 限流维度
        message = "访问过于频繁，请稍后重试"    // 错误消息
    )
    public ApiResponse<String> test() {
        return ApiResponse.success("测试成功");
    }
}
```

### 2. 不同限流维度示例

#### IP限流
```java
@RateLimit(
    limit = 100,
    window = 1,
    timeUnit = TimeUnit.HOURS,
    dimension = RateLimitDimension.IP
)
```

#### 用户限流
```java
@RateLimit(
    limit = 1000,
    window = 1,
    timeUnit = TimeUnit.HOURS,
    dimension = RateLimitDimension.USER
)
```

#### 全局限流
```java
@RateLimit(
    limit = 10000,
    window = 1,
    timeUnit = TimeUnit.HOURS,
    dimension = RateLimitDimension.GLOBAL
)
```

#### 自定义限流
```java
@GetMapping("/business/:id")
@RateLimit(
    limit = 50,
    window = 10,
    timeUnit = TimeUnit.MINUTES,
    dimension = RateLimitDimension.CUSTOM,
    customKey = "business_#arg0"  // 使用第一个参数作为限流键
)
public ApiResponse<String> businessApi(String id) {
    // 业务逻辑
}
```

### 3. 不同限流算法示例

#### 固定窗口限流
```java
@RateLimit(
    type = RateLimitType.FIXED_WINDOW,
    limit = 100,
    window = 1,
    timeUnit = TimeUnit.MINUTES
)
```

#### 滑动窗口限流
```java
@RateLimit(
    type = RateLimitType.SLIDING_WINDOW,
    limit = 100,
    window = 1,
    timeUnit = TimeUnit.MINUTES
)
```

### 4. 禁用限流

```java
@RateLimit(
    enabled = false,  // 禁用限流
    limit = 1,
    window = 1,
    timeUnit = TimeUnit.SECONDS
)
```

## 注解参数说明

| 参数        | 类型               | 默认值                     | 说明                           |
| ----------- | ------------------ | -------------------------- | ------------------------------ |
| `key`       | String             | ""                         | 限流键前缀，为空时自动生成     |
| `type`      | RateLimitType      | SLIDING_WINDOW             | 限流算法类型                   |
| `window`    | long               | 60                         | 时间窗口大小                   |
| `timeUnit`  | TimeUnit           | SECONDS                    | 时间单位                       |
| `limit`     | int                | 100                        | 限流数量                       |
| `dimension` | RateLimitDimension | IP                         | 限流维度                       |
| `customKey` | String             | ""                         | 自定义限流键（支持SpEL表达式） |
| `message`   | String             | "请求过于频繁，请稍后重试" | 限流提示消息                   |
| `enabled`   | boolean            | true                       | 是否启用限流                   |

## 自定义键表达式

当使用 `RateLimitDimension.CUSTOM` 时，可以通过 `customKey` 参数指定自定义的限流键，支持以下表达式：

- `#arg0`, `#arg1`, ... : 方法参数
- `#ip` : 客户端IP地址
- `#userId` : 用户ID（来自请求头 X-User-Id）
- 字符串常量 : 直接使用字符串作为键的一部分

示例：
```java
// 基于用户ID和业务类型的组合键
customKey = "user_#userId_type_#arg0"

// 基于IP和特定业务标识
customKey = "ip_#ip_business_order"

// 纯字符串键
customKey = "global_special_api"
```

## 异常处理

当请求超过限流阈值时，系统会抛出 `RateLimitException` 异常，该异常会被全局异常处理器捕获并返回HTTP 429状态码。

响应格式：
```json
{
  "success": false,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "请求过于频繁，请稍后重试",
  "data": null,
  "timestamp": "2024-01-01T12:00:00",
  "traceId": "trace-12345",
  "rateLimitKey": "ip_192.168.1.1_/api/test",
  "rateLimitInfo": {
    "limit": 10,
    "window": 60,
    "timeUnit": "SECONDS",
    "dimension": "IP"
  },
  "retryAfter": 30
}
```

响应头：
- `X-RateLimit-Limit`: 限流配额
- `X-RateLimit-Remaining`: 剩余配额
- `X-RateLimit-Reset`: 配额重置时间
- `Retry-After`: 建议重试等待时间（秒）

## 测试示例

项目提供了 `RateLimitDemoController` 控制器，包含多种限流场景的示例：

- `/api/ratelimit/basic` - 基础IP限流
- `/api/ratelimit/strict` - 严格滑动窗口限流
- `/api/ratelimit/global` - 全局限流
- `/api/ratelimit/user` - 用户级限流
- `/api/ratelimit/custom/:businessId` - 自定义键限流
- `/api/ratelimit/unlimited` - 禁用限流示例
- `/api/ratelimit/status` - 获取限流状态

## 性能特性

- **内存高效**: 使用ConcurrentHashMap存储限流数据，支持高并发访问
- **自动清理**: 定时清理过期的限流记录，防止内存泄漏
- **线程安全**: 所有限流器实现都是线程安全的
- **低延迟**: 限流检查操作的时间复杂度为O(1)或O(log n)

## 注意事项

1. **单机限流**: 本限流器仅在单个应用实例内生效，不支持分布式限流
2. **内存存储**: 限流数据存储在内存中，应用重启后限流状态会重置
3. **用户识别**: 用户级限流需要在请求头中提供 `X-User-Id`
4. **IP获取**: 支持从 `X-Forwarded-For`、`X-Real-IP` 等头部获取真实IP
5. **时间精度**: 限流时间窗口的精度为毫秒级

## 扩展开发

如需实现自定义限流算法，可以：

1. 实现 `RateLimiter` 接口
2. 在 `RateLimitManager` 中注册新的限流器
3. 在 `RateLimitType` 枚举中添加新的类型

示例：
```java
public class CustomRateLimiter implements RateLimiter {
    @Override
    public RateLimitResult tryAcquire(String key, RateLimit config) {
        // 自定义限流逻辑
    }

    @Override
    public RateLimitType getType() {
        return RateLimitType.CUSTOM;
    }

    @Override
    public void cleanup() {
        // 清理逻辑
    }
}
```
