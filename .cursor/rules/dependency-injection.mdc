---
description:
globs:
alwaysApply: false
---
# 依赖注入使用指南

本项目使用Google Guice进行依赖注入管理，简化组件间依赖关系。

## 核心组件

- [AppModule.java](mdc:src/main/java/com/vertx/template/di/AppModule.java) - Guice模块配置，定义所有依赖绑定
- [RouterRegistry.java](mdc:src/main/java/com/vertx/template/router/RouterRegistry.java) - 创建Injector并获取组件实例

## 常见注解

在代码中使用的主要注解：

### @Inject
用于标记依赖注入点，可以用在：
- 构造函数上：`@Inject public UserController(UserService service) {...}`
- 字段上：`@Inject private UserService service;`

### @Singleton
将组件标记为单例，确保只创建一个实例：
```java
@Singleton
public class UserServiceImpl implements UserService {...}
```

或在绑定时指定：
```java
bind(UserService.class).to(UserServiceImpl.class).in(Singleton.class);
```

### @Provides
在模块中提供工厂方法创建复杂对象：
```java
@Provides
@Singleton
Router provideRouter() {
  return Router.router(vertx);
}
```

## 依赖注入示例

- [UserController.java](mdc:src/main/java/com/vertx/template/controller/UserController.java) - 注入UserService接口
- [UserRoutes.java](mdc:src/main/java/com/vertx/template/routes/UserRoutes.java) - 注入UserController
- [GlobalMiddleware.java](mdc:src/main/java/com/vertx/template/router/GlobalMiddleware.java) - 注入Vertx、Router和配置

## 添加新服务步骤

1. 创建服务接口和实现类，并在实现类上添加`@Inject`构造函数
2. 在AppModule中添加绑定：`bind(NewService.class).to(NewServiceImpl.class).in(Singleton.class);`
3. 在需要使用的地方通过构造函数注入：`@Inject public MyClass(NewService service) {...}`
