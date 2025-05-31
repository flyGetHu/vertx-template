# 中间件模块设计文档

## 概述

本模块提供了一个灵活、可扩展的中间件系统，用于在Vert.x应用中处理横切关注点，如认证、日志记录、限流等。

## 核心设计

### 1. MiddlewareResult 结果类

新的设计使用 `MiddlewareResult` 类来表示中间件执行结果，解决了原有设计中无法准确获取错误信息的问题。

```java
public class MiddlewareResult {
    private final boolean success;        // 是否成功
    private final String statusCode;      // HTTP状态码
    private final String message;         // 响应消息
    private final boolean continueChain;  // 是否继续执行后续中间件
    private final Object data;            // 额外数据
}
```

### 2. Middleware 接口

```java
public interface Middleware {
    /**
     * 处理请求
     * @param context 路由上下文
     * @return 处理结果，包含执行状态、状态码、消息和是否继续执行后续中间件的标识
     */
    Future<MiddlewareResult> handle(RoutingContext context);

    String getName();
    default int getOrder() { return 100; }
    default boolean isEnabled() { return true; }
}
```

## 使用方法

### 1. 创建中间件

```java
public class AuthMiddleware implements Middleware {
    @Override
    public Future<MiddlewareResult> handle(RoutingContext context) {
        String token = context.request().getHeader("Authorization");

        if (token == null) {
            return Future.succeededFuture(
                MiddlewareResult.failure("401", "缺少认证令牌")
            );
        }

        // 认证成功
        return Future.succeededFuture(MiddlewareResult.success("认证成功"));
    }

    @Override
    public String getName() {
        return "AuthMiddleware";
    }
}
```

### 2. 注册和使用中间件

```java
// 创建中间件管理器
MiddlewareManager middlewareManager = new MiddlewareManager();

// 注册中间件
middlewareManager.register(new LoggingMiddleware());
middlewareManager.register(new AuthMiddleware());
middlewareManager.register(new RateLimitMiddleware());

// 在路由中使用
router.route().handler(context -> {
    middlewareManager.execute(context)
        .onSuccess(result -> {
            if (result.isSuccess() && result.shouldContinueChain()) {
                context.next(); // 继续处理请求
            } else {
                handleMiddlewareResult(context, result); // 返回错误响应
            }
        })
        .onFailure(throwable -> {
            // 处理异常
            handleException(context, throwable);
        });
});
```

## 错误处理机制

### 1. 中间件失败处理

当中间件执行失败时，可以通过以下方式处理：

```java
// 方式1：返回失败结果（推荐）
return Future.succeededFuture(
    MiddlewareResult.failure("401", "认证失败")
);

// 方式2：抛出异常（用于严重错误）
return Future.failedFuture(new RuntimeException("系统错误"));
```

### 2. 统一错误响应格式

```json
{
    "success": false,
    "code": "401",
    "message": "认证失败",
    "data": null
}
```

## 中间件执行顺序

中间件按照 `getOrder()` 方法返回的数值升序执行：

- LoggingMiddleware: order = 5 (最先执行)
- AuthMiddleware: order = 10
- RateLimitMiddleware: order = 20

## 便利方法

### MiddlewareResult 静态方法

```java
// 成功并继续
MiddlewareResult.success()
MiddlewareResult.success("操作成功")
MiddlewareResult.success("操作成功", userData)

// 失败并中断
MiddlewareResult.failure("400", "参数错误")
MiddlewareResult.failure("401", "认证失败", errorData)

// 成功但停止执行链条
MiddlewareResult.stop("请求已处理")
MiddlewareResult.stop("200", "缓存命中")
```

## 最佳实践

### 1. 异常处理

```java
@Override
public Future<MiddlewareResult> handle(RoutingContext context) {
    try {
        // 业务逻辑
        return Future.succeededFuture(MiddlewareResult.success());
    } catch (Exception e) {
        logger.error("中间件执行异常", e);
        return Future.succeededFuture(
            MiddlewareResult.failure("500", "内部错误: " + e.getMessage())
        );
    }
}
```

### 2. 异步操作

```java
@Override
public Future<MiddlewareResult> handle(RoutingContext context) {
    return validateTokenAsync(token)
        .compose(isValid -> {
            if (isValid) {
                return Future.succeededFuture(MiddlewareResult.success());
            } else {
                return Future.succeededFuture(
                    MiddlewareResult.failure("401", "令牌无效")
                );
            }
        })
        .recover(throwable -> {
            return Future.succeededFuture(
                MiddlewareResult.failure("500", "验证失败")
            );
        });
}
```

### 3. 数据传递

```java
// 在中间件中设置数据
context.put("userId", "user123");
context.put("userRole", "admin");

// 在后续处理器中获取数据
String userId = context.get("userId");
String userRole = context.get("userRole");
```

## 架构优势

1. **清晰的错误处理**：通过 `MiddlewareResult` 可以准确获取错误状态码和消息
2. **灵活的执行控制**：支持成功继续、失败中断、成功停止等多种执行模式
3. **异步支持**：完全支持异步操作，符合Vert.x设计理念
4. **可扩展性**：易于添加新的中间件类型
5. **统一的响应格式**：提供一致的API响应结构

## 迁移指南

从旧的 `Future<Pair<String, String>>` 迁移到新设计：

```java
// 旧设计
return Future.succeededFuture(Pair.of("200", "成功"));

// 新设计
return Future.succeededFuture(MiddlewareResult.success("成功"));
```

```java
// 旧设计
return Future.succeededFuture(Pair.of("401", "认证失败"));

// 新设计
return Future.succeededFuture(MiddlewareResult.failure("401", "认证失败"));
```
