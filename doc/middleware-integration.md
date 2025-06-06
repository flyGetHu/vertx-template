# 中间件系统集成说明

## 概述

本文档说明了如何在Vert.x模板项目中正确集成和使用中间件系统。中间件系统现在已经完全集成到依赖注入容器中，并在路由处理中自动执行。

## 架构变更

### 1. 依赖注入配置 (AppModule)

**⚠️ 架构调整**: 认证和限流中间件已迁移到注解处理层，不再作为独立中间件。

在 `AppModule.java` 中的中间件相关配置：

```java
// 中间件链
@Provides
@Singleton
public MiddlewareChain provideMiddlewareChain() {
  return new MiddlewareChain();
}

// 认证管理器（用于注解处理）
@Provides
@Singleton
public AuthenticationManager provideAuthenticationManager(Injector injector) {
  return new AuthenticationManager(injector);
}

// 限流管理器（用于注解处理）
@Provides
@Singleton
public RateLimitManager provideRateLimitManager() {
  return new RateLimitManager();
}

// 限流拦截器（用于注解处理）
@Provides
@Singleton
public RateLimitInterceptor provideRateLimitInterceptor(RateLimitManager rateLimitManager) {
  return new RateLimitInterceptor(rateLimitManager);
}

// 核心中间件
@Provides
@Singleton
public CorsMiddleware provideCorsMiddleware(JsonObject config) {
  return new CorsMiddleware(config);
}

@Provides
@Singleton
public BodyHandlerMiddleware provideBodyHandlerMiddleware(JsonObject config) {
  return new BodyHandlerMiddleware(config);
}

@Provides
@Singleton
public RequestLoggerMiddleware provideRequestLoggerMiddleware(JsonObject config) {
  return new RequestLoggerMiddleware(config);
}
```

### 2. 中间件初始化器 (MiddlewareInitializer)

`MiddlewareInitializer` 现在负责：
- 注册核心中间件到中间件链（CORS、Body处理器、请求日志）
- 业务逻辑中间件（认证、限流）已迁移到注解处理层

```java
/**
 * 注册核心中间件
 */
private void registerCoreMiddlewares() {
    // 1. CORS中间件 (order=10)
    if (corsMiddleware.isEnabled()) {
      middlewareChain.register(corsMiddleware);
    }

    // 2. Body处理器中间件 (order=20)
    if (bodyHandlerMiddleware.isEnabled()) {
      middlewareChain.register(bodyHandlerMiddleware);
    }

    // 3. 请求日志中间件 (order=30)
    if (requestLoggerMiddleware.isEnabled()) {
      middlewareChain.register(requestLoggerMiddleware);
    }
}

/**
 * 注册业务中间件
 * 认证和限流逻辑已移至注解处理层(@RequireAuth, @RateLimit)
 */
private void registerBusinessMiddlewares() {
    logger.debug("业务中间件注册跳过 - 认证和限流由注解处理层处理");
}
```

### 3. 路由处理集成 (AnnotationRouterHandler)

在 `AnnotationRouterHandler` 中，路由处理流程包括：
1. 执行核心中间件链（CORS、Body处理器、请求日志）
2. 检查方法级注解（@RequireAuth、@RateLimit）
3. 执行业务逻辑
4. 返回处理结果

```java
private Handler<RoutingContext> createHandler(Object controller, Method method) {
  return middlewareInitializer.createUnifiedHandler(ctx -> {
    // 1. 检查认证注解
    RequireAuth requireAuth = method.getAnnotation(RequireAuth.class);
    if (requireAuth != null) {
      try {
        authenticationManager.authenticate(ctx, requireAuth.value());
      } catch (AuthenticationException e) {
        return Future.failedFuture(e);
      }
    }

    // 2. 检查限流注解
    RateLimit rateLimit = method.getAnnotation(RateLimit.class);
    if (rateLimit != null) {
      try {
        rateLimitInterceptor.checkRateLimit(ctx, rateLimit);
      } catch (RateLimitException e) {
        return Future.failedFuture(e);
      }
    }

    // 3. 执行业务逻辑
    return processRoute(ctx, controller, method);
  });
}
```

## 中间件执行流程

1. **请求到达** → 路由匹配
2. **中间件链执行** → 按order顺序执行所有启用的中间件
3. **中间件检查** → 如果任何中间件返回false，中断执行
4. **路由处理** → 执行控制器方法
5. **响应返回** → 返回处理结果

## 中间件执行顺序

### 核心中间件链（自动执行）
1. **CorsMiddleware** (order: 10) - 跨域处理
2. **BodyHandlerMiddleware** (order: 20) - 请求体解析
3. **RequestLoggerMiddleware** (order: 30) - 请求日志

### 注解驱动的业务逻辑（按需执行）
1. **@RequireAuth** - 认证检查（方法级）
2. **@RateLimit** - 限流检查（方法级）
3. **@CurrentUser** - 用户上下文注入（参数级）

## 如何添加新的中间件

### 1. 创建中间件类

```java
@Singleton
public class CustomMiddleware implements Middleware {

  @Override
  public Future<Boolean> handle(RoutingContext context) {
    // 中间件逻辑
    return Future.succeededFuture(true);
  }

  @Override
  public String getName() {
    return "CustomMiddleware";
  }

  @Override
  public int getOrder() {
    return 150; // 在AuthMiddleware之后，RateLimitMiddleware之前
  }
}
```

### 2. 在AppModule中注册

```java
@Provides
@Singleton
public CustomMiddleware provideCustomMiddleware() {
  return new CustomMiddleware();
}
```

### 3. 在GlobalMiddleware中添加注册

```java
private void registerCustomMiddlewares() {
  middlewareChain.register(authMiddleware);
  middlewareChain.register(customMiddleware); // 添加新中间件
  middlewareChain.register(rateLimitMiddleware);
}
```

## 测试

中间件系统包含完整的单元测试，测试覆盖：
- 中间件注册
- 执行顺序
- 中断机制
- 禁用中间件

运行测试：
```bash
mvn test -Dtest=MiddlewareChainTest
```

## 配置选项

中间件可以通过以下方式进行配置：

- **启用/禁用**：实现 `isEnabled()` 方法
- **执行顺序**：实现 `getOrder()` 方法
- **条件执行**：在 `handle()` 方法中添加条件逻辑

## 性能考虑

- 中间件按顺序同步执行
- 使用Future进行异步处理
- 支持中间件链的早期中断
- 缓存机制减少反射开销

## 故障排除

### 常见问题

1. **中间件未执行**
   - 检查是否在AppModule中正确注册
   - 检查是否在GlobalMiddleware中添加到链中
   - 检查中间件的 `isEnabled()` 方法

2. **执行顺序错误**
   - 检查中间件的 `getOrder()` 返回值
   - 数值越小，优先级越高

3. **请求被意外中断**
   - 检查中间件的 `handle()` 方法返回值
   - 返回false会中断后续处理

### 调试日志

启用调试日志查看中间件执行情况：
```properties
logging.level.com.vertx.template.middleware=DEBUG
```

## 总结

中间件系统已完成重大架构调整并修复了Guice 7.0.0兼容性问题：

### ✅ 架构优化完成
- 核心中间件（CORS、Body处理器、请求日志）使用传统中间件链
- 业务逻辑（认证、限流）迁移到注解处理层
- 简化了依赖注入关系，符合Guice 7.0.0严格检查

### ✅ 依赖注入修复
- 移除了已删除中间件的依赖引用
- 补充了缺失的组件（AuthenticationManager、RateLimitManager等）
- 所有依赖注入错误已解决

### ✅ 功能完整性
- 注解驱动的认证和限流功能正常
- 核心中间件链正确执行
- 统一的错误处理和响应格式

### ✅ 开发体验提升
- 注解使用更加简洁直观
- 中间件配置清晰明确
- 错误信息详细准确

详细修复过程请参考：[依赖注入修复总结](./DEPENDENCY_INJECTION_FIX_SUMMARY.md)
