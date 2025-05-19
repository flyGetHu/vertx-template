---
description:
globs:
alwaysApply: false
---
# API响应处理机制

本项目实现了统一的API响应处理机制，简化了控制器代码并确保一致的响应格式。

## 核心组件

### 响应处理器
- [ResponseHandler.java](mdc:src/main/java/com/vertx/template/handler/ResponseHandler.java) - 统一响应处理器，自动将返回数据包装为标准响应格式
  - 支持直接返回业务数据，自动包装为`ApiResponse`
  - 自动处理异常，转换为友好的错误响应
  - 简化控制器代码，减少模板代码

### 全局异常处理器
- [GlobalExceptionHandler.java](mdc:src/main/java/com/vertx/template/handler/GlobalExceptionHandler.java) - 全局异常处理，确保所有未捕获的异常都能得到妥善处理
  - 区分业务异常和系统异常
  - 统一异常日志记录
  - 返回友好的错误消息

### 响应模型
- [ApiResponse.java](mdc:src/main/java/com/vertx/template/model/ApiResponse.java) - 统一的API响应模型
  - 包含状态码、消息和数据
  - 提供了便捷的静态工厂方法创建成功/失败响应

### 业务异常
- [BusinessException.java](mdc:src/main/java/com/vertx/template/exception/BusinessException.java) - 业务异常类
  - 包含错误码和错误消息
  - 用于表示可预期的业务逻辑错误

## 使用方式

在控制器中使用`ResponseHandler`处理响应：

```java
public Handler<RoutingContext> getUsers() {
  return responseHandler.handle(ctx -> {
    // 直接返回业务数据，ResponseHandler会自动包装和序列化
    return Future.await(userService.getUsers());
  });
}
```

抛出业务异常示例：

```java
if (id == null || id.trim().isEmpty()) {
  throw new BusinessException(400, "User ID is required");
}
```
