# 依赖注入使用指南

本项目使用Google Guice 7.0.0结合JSR-330标准注解进行依赖注入管理，简化组件间依赖关系。

## 📋 目录

- [版本信息](#版本信息)
- [核心组件](#核心组件)
- [常用注解](#常用注解)
- [使用示例](#使用示例)
- [添加新服务的步骤](#添加新服务的步骤)
- [路由注册](#路由注册)
- [最佳实践](#最佳实践)

## 版本信息

- **Guice版本**: 7.0.0
- **JSR-330**: 1.0
- **升级说明**: Guice 7.0.0引入了更严格的依赖注入验证，详见[修复总结](./DEPENDENCY_INJECTION_FIX_SUMMARY.md)

## 核心组件

- [AppModule.java](../src/main/java/com/vertx/template/di/AppModule.java) - Guice模块配置，只定义基础依赖绑定
- [RouterRegistry.java](../src/main/java/com/vertx/template/router/RouterRegistry.java) - 创建Injector并注册路由
- [AnnotationRouterHandler.java](../src/main/java/com/vertx/template/router/handler/AnnotationRouterHandler.java) - 处理基于注解的路由

## 常用注解

### 依赖注入注解

#### @Inject (JSR-330)
用于标记依赖注入点，可以用在：
- 构造函数上：`@Inject public UserController(UserService service) {...}`
- 字段上：`@Inject private UserService service;`

> 推荐使用构造函数注入，便于单元测试

#### @Singleton (JSR-330)
将组件标记为单例，确保只创建一个实例：
```java
@Singleton
public class UserServiceImpl implements UserService {...}
```

#### @ImplementedBy
在接口上使用，指定默认实现类：
```java
@ImplementedBy(UserServiceImpl.class)
public interface UserService {...}
```

#### @Provides
在模块中提供工厂方法创建复杂对象：
```java
@Provides
@Singleton
Router provideRouter() {
  return Router.router(vertx);
}
```

### 路由注解 (Spring Boot风格)

#### @RestController
标记一个类为REST控制器，内部方法将自动注册为路由处理器：
```java
@RestController
public class UserController {...}
```

#### @RequestMapping
定义请求映射路径和HTTP方法，可用于类或方法：
```java
@RequestMapping("/api/users")
public class UserController {...}

@RequestMapping(value = "/{id}", method = RequestMethod.GET)
public void getUserById() {...}
```

#### HTTP方法注解
简化特定HTTP方法的路由定义：
- `@GetMapping` - GET请求
- `@PostMapping` - POST请求
- `@PutMapping` - PUT请求
- `@DeleteMapping` - DELETE请求

## 使用示例

### 依赖注入示例
- [UserService.java](../src/main/java/com/vertx/template/service/UserService.java) - 使用@ImplementedBy注解
- [UserServiceImpl.java](../src/main/java/com/vertx/template/service/impl/UserServiceImpl.java) - 使用@Singleton注解

### 路由定义示例
- [UserController.java](../src/main/java/com/vertx/template/controller/UserController.java) - 使用Spring Boot风格注解

## 添加新服务的步骤

1. 创建服务接口，使用@ImplementedBy注解指定实现类：
    ```java
    @ImplementedBy(NewServiceImpl.class)
    public interface NewService {...}
    ```

2. 创建服务实现类，添加@Singleton和@Inject注解：
    ```java
    @Singleton
    public class NewServiceImpl implements NewService {
        @Inject
        public NewServiceImpl(DependencyService dependency) {...}
    }
    ```

3. 在需要使用的地方通过构造函数注入：
    ```java
    @Inject
    public MyClass(NewService service) {...}
    ```

## 路由注册 (Spring Boot风格)

项目支持Spring Boot风格的路由注解，通过`AnnotationRouterHandler`自动扫描并注册路由。

### 自动注册流程
1. 扫描所有带`@RestController`注解的类
2. 解析类和方法上的`@RequestMapping`注解
3. 自动注册到Vert.x Router

示例：
```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    private final ProductService productService;
    
    @Inject
    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    
    @GetMapping
    public void getAllProducts(RoutingContext context) {
        // 处理逻辑
    }
    
    @GetMapping("/{id}")
    public void getProductById(RoutingContext context) {
        // 处理逻辑
    }
    
    @PostMapping
    public void createProduct(RoutingContext context) {
        // 处理逻辑
    }
}
```

## 🎯 最佳实践

### 1. 构造函数注入优于字段注入

```java
// ✅ 推荐：构造函数注入
@Singleton
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    
    @Inject
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

// ❌ 避免：字段注入
@Singleton
public class UserServiceImpl implements UserService {
    @Inject
    private UserRepository userRepository;
}
```

### 2. 使用接口而非具体实现

```java
// ✅ 推荐：依赖接口
@Inject
public UserController(UserService userService) {
    this.userService = userService;
}

// ❌ 避免：依赖具体实现
@Inject
public UserController(UserServiceImpl userServiceImpl) {
    this.userServiceImpl = userServiceImpl;
}
```

### 3. 合理使用单例模式

```java
// ✅ 推荐：无状态服务使用单例
@Singleton
public class UserServiceImpl implements UserService {
    // 无状态服务，可以安全地使用单例
}

// ❌ 避免：有状态对象使用单例
@Singleton
public class UserSession {
    private String currentUserId; // 有状态，不应该使用单例
}
```

### 4. 模块化配置

```java
// AppModule.java - 主模块
public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        // 安装其他模块
        install(new DatabaseModule());
        install(new SecurityModule());
    }
}

// DatabaseModule.java - 数据库模块
public class DatabaseModule extends AbstractModule {
    @Override
    protected void configure() {
        // 数据库相关配置
    }
}
```

## 🔧 常见问题解决

### 1. 循环依赖问题

```java
// 问题：A依赖B，B依赖A
public class ServiceA {
    @Inject
    public ServiceA(ServiceB serviceB) { ... }
}

public class ServiceB {
    @Inject
    public ServiceB(ServiceA serviceA) { ... }
}

// 解决方案：使用Provider延迟注入
public class ServiceA {
    @Inject
    public ServiceA(Provider<ServiceB> serviceBProvider) { ... }
}
```

### 2. 可选依赖

```java
// 使用@Nullable注解标记可选依赖
public class UserService {
    @Inject
    public UserService(UserRepository repository, 
                      @Nullable CacheService cacheService) {
        this.repository = repository;
        this.cacheService = cacheService; // 可能为null
    }
}
```

### 3. 配置注入

```java
// 在AppModule中提供配置
@Provides
@Singleton
public DatabaseConfig provideDatabaseConfig() {
    return ConfigLoader.loadDatabaseConfig();
}

// 在服务中注入配置
@Singleton
public class DatabaseService {
    @Inject
    public DatabaseService(DatabaseConfig config) {
        this.config = config;
    }
}
```

---

**📝 相关文档**:
- [项目结构文档](PROJECT_STRUCTURE.md) - 了解项目整体结构
- [注解使用指南](ANNOTATION_USAGE.md) - 学习路由注解使用
- [依赖注入修复总结](DEPENDENCY_INJECTION_FIX_SUMMARY.md) - 了解历史问题和解决方案

**🕒 最后更新**: 2024年12月
