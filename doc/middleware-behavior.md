# 中间件行为说明文档

## 概述

根据用户需求，系统已调整为以下行为模式：

1. **限流**：只有添加了 `@RateLimit` 注解的接口才执行限流，没有注解的接口不限流
2. **鉴权**：默认全局启用JWT鉴权，如果添加了 `@RequireAuth` 注解则使用注解指定的鉴权方式

## 系统架构优化

### 简化后的处理机制

```
请求 → 基础中间件层 → 注解处理层 → 控制器方法
       ↓              ↓
   CORS/Body/Log    精细化控制
```

**架构优化说明**：
- ✅ **移除冗余**：删除了 `DefaultAuthMiddleware` 和 `DefaultRateLimitMiddleware`
- ✅ **性能提升**：减少了不必要的方法调用开销
- ✅ **代码简化**：移除了"占位符"代码，降低维护成本
- ✅ **逻辑清晰**：认证和限流完全由注解层处理，职责单一

### 处理层级说明

1. **基础中间件层**：只处理必要的全局功能
   - CORS处理
   - Body解析
   - 请求日志记录

2. **注解处理层**：根据注解进行精细化控制
   - `@RequireAuth` 控制认证行为
   - `@RateLimit` 控制限流行为

## 认证行为

### 默认行为
- **所有接口默认需要JWT认证**
- 如果没有 `@RequireAuth` 注解，系统会要求JWT认证

### 注解控制
```java
// 不需要认证
@RequireAuth(AuthType.NONE)
public Object publicMethod() { ... }

// 使用JWT认证（默认行为，可省略）
@RequireAuth(AuthType.JWT)
public Object jwtMethod() { ... }

// 类级别配置（所有方法都不需要认证）
@RestController
@RequireAuth(AuthType.NONE)
public class PublicController { ... }
```

### 认证优先级
1. **方法级别** `@RequireAuth` 注解（最高优先级）
2. **类级别** `@RequireAuth` 注解
3. **默认行为**：JWT认证

### 实现位置
- **核心逻辑**：`AnnotationRouterHandler.performAuthentication()`
- **认证信息提取**：`ReflectionCache.extractAuthInfo()`
- **认证执行**：`AuthenticationManager.authenticate()`

## 限流行为

### 默认行为
- **没有 `@RateLimit` 注解的接口不执行任何限流**
- 只有明确添加注解的接口才会限流

### 注解控制
```java
// 不限流（默认行为）
public Object noLimitMethod() { ... }

// IP维度限流：每分钟100次
@RateLimit(
    type = RateLimitType.FIXED_WINDOW,
    dimension = RateLimitDimension.IP,
    permits = 100,
    window = 1,
    timeUnit = TimeUnit.MINUTES
)
public Object limitedMethod() { ... }

// 用户维度限流：每秒10次
@RateLimit(
    type = RateLimitType.TOKEN_BUCKET,
    dimension = RateLimitDimension.USER,
    permits = 10,
    window = 1,
    timeUnit = TimeUnit.SECONDS
)
public Object userLimitedMethod() { ... }
```

### 实现位置
- **核心逻辑**：`AnnotationRouterHandler.executeRouteHandler()`
- **限流检查**：`RateLimitInterceptor.performRateLimitCheck()`
- **限流管理**：`RateLimitManager`

## 性能优化

### 优化前 vs 优化后

| 方面       | 优化前                                | 优化后             |
| ---------- | ------------------------------------- | ------------------ |
| 中间件数量 | 4个（CORS + Body + Auth + RateLimit） | 2个（CORS + Body） |
| 每请求开销 | 4次中间件调用                         | 2次中间件调用      |
| 代码复杂度 | 高（冗余占位符代码）                  | 低（职责单一）     |
| 维护成本   | 高                                    | 低                 |

### 性能提升
- **减少50%的全局中间件调用**
- **移除无用的条件判断和方法调用**
- **简化请求处理流程**

## 使用示例

### 完整的控制器示例
```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    // 公开接口：不需要认证，不限流
    @GetMapping("/public")
    @RequireAuth(AuthType.NONE)
    public Object getPublicInfo() {
        return "公开信息";
    }

    // 需要JWT认证，IP限流
    @GetMapping("/profile")
    @RateLimit(
        type = RateLimitType.FIXED_WINDOW,
        dimension = RateLimitDimension.IP,
        permits = 100,
        window = 1,
        timeUnit = TimeUnit.MINUTES
    )
    public Object getUserProfile() {
        return "用户信息";
    }

    // 需要JWT认证，用户维度限流
    @PostMapping("/update")
    @RateLimit(
        type = RateLimitType.TOKEN_BUCKET,
        dimension = RateLimitDimension.USER,
        permits = 10,
        window = 1,
        timeUnit = TimeUnit.MINUTES
    )
    public Object updateUser() {
        return "更新成功";
    }

    // 只需要JWT认证，不限流（默认行为）
    @GetMapping("/settings")
    public Object getUserSettings() {
        return "用户设置";
    }
}
```

## 总结

通过移除冗余的默认中间件，系统变得更加：
- **简洁**：移除了无用的占位符代码
- **高效**：减少了不必要的方法调用
- **清晰**：认证和限流逻辑完全由注解控制
- **易维护**：代码结构更简单，职责更明确

这种设计完全满足了用户的需求：
- ✅ 限流：只有添加了 `@RateLimit` 注解的接口才执行限流
- ✅ 鉴权：默认全局启用JWT鉴权，`@RequireAuth` 注解可以覆盖默认行为
