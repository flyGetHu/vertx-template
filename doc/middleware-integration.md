# 中间件系统集成说明

## 概述

本文档说明了如何在Vert.x模板项目中正确集成和使用中间件系统。中间件系统现在已经完全集成到依赖注入容器中，并在路由处理中自动执行。

## 架构变更

### 1. 依赖注入配置 (AppModule)

在 `AppModule.java` 中添加了以下中间件相关的Bean配置：

```java
@Provides
@Singleton
public MiddlewareChain provideMiddlewareChain() {
  return new MiddlewareChain();
}

@Provides
@Singleton
public AuthMiddleware provideAuthMiddleware() {
  return new AuthMiddleware();
}

@Provides
@Singleton
public RateLimitMiddleware provideRateLimitMiddleware(RateLimitManager rateLimitManager) {
  return new RateLimitMiddleware(rateLimitManager);
}
```

### 2. 全局中间件注册 (GlobalMiddleware)

`GlobalMiddleware` 现在负责：
- 注册传统的Vert.x处理器（CORS、BodyHandler等）
- 将自定义中间件注册到中间件链
- 提供中间件链的访问接口

```java
private void registerCustomMiddlewares() {
  // 注册认证中间件
  middlewareChain.register(authMiddleware);

  // 注册限流中间件
  middlewareChain.register(rateLimitMiddleware);
}
```

### 3. 路由处理集成 (AnnotationRouterHandler)

在 `AnnotationRouterHandler` 中，每个路由处理器现在都会：
1. 首先执行中间件链
2. 如果中间件链执行成功，继续处理路由逻辑
3. 如果中间件链中断请求，直接返回

```java
private Handler<RoutingContext> createHandler(Object controller, Method method) {
  return responseHandler.handle(
      ctx -> {
        // 首先执行中间件链
        return middlewareChain.execute(ctx)
            .compose(middlewareResult -> {
              if (!middlewareResult) {
                // 中间件链中断了请求，直接返回
                return Future.succeededFuture(null);
              }

              // 中间件链执行成功，继续处理路由
              return processRoute(ctx, controller, method);
            });
      });
}
```

## 中间件执行流程

1. **请求到达** → 路由匹配
2. **中间件链执行** → 按order顺序执行所有启用的中间件
3. **中间件检查** → 如果任何中间件返回false，中断执行
4. **路由处理** → 执行控制器方法
5. **响应返回** → 返回处理结果

## 中间件顺序

当前注册的中间件按以下顺序执行：

1. **AuthMiddleware** (order: 100) - 认证检查
2. **RateLimitMiddleware** (order: 200) - 限流检查

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

中间件系统现在已经完全集成到项目中：
- ✅ MiddlewareChain 已在AppModule中注册
- ✅ 中间件在GlobalMiddleware中正确注册
- ✅ 路由处理器中集成了中间件链执行
- ✅ 包含完整的测试覆盖
- ✅ 支持依赖注入和配置管理

系统现在可以正确处理中间件的注册、执行和管理。
