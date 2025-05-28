---
description:
globs:
alwaysApply: false
---
# Vert.x模板项目结构

本项目是基于Vert.x的模板项目，使用JDK21虚拟线程构建的响应式Web应用。

## 项目入口

- [Run.java](mdc:src/main/java/com/vertx/template/Run.java) - 应用程序主入口，使用JDK21虚拟线程部署Verticle
- [MainVerticle.java](mdc:src/main/java/com/vertx/template/MainVerticle.java) - 主Verticle，负责初始化配置和HTTP服务器

## 核心模块

### 配置系统
- [ConfigLoader.java](mdc:src/main/java/com/vertx/template/config/ConfigLoader.java) - 配置加载器，从YAML加载配置
- [config.yml](mdc:src/main/resources/config.yml) - YAML配置文件

### 路由系统
- [RouterRegistry.java](mdc:src/main/java/com/vertx/template/router/RouterRegistry.java) - 路由注册中心，集中管理所有路由
- [RouteGroup.java](mdc:src/main/java/com/vertx/template/router/RouteGroup.java) - 路由组接口，定义统一的路由注册规范
- [GlobalMiddleware.java](mdc:src/main/java/com/vertx/template/router/GlobalMiddleware.java) - 全局中间件，处理跨域、请求日志等

### MVC组件
- [UserController.java](mdc:src/main/java/com/vertx/template/controller/UserController.java) - 用户控制器
- [UserService.java](mdc:src/main/java/com/vertx/template/service/UserService.java) - 用户服务接口
- [UserServiceImpl.java](mdc:src/main/java/com/vertx/template/service/UserServiceImpl.java) - 用户服务实现

### 路由定义
- [UserRoutes.java](mdc:src/main/java/com/vertx/template/routes/UserRoutes.java) - 用户相关路由

### 其他组件
- [ApiResponse.java](mdc:src/main/java/com/vertx/template/model/ApiResponse.java) - API响应模型
- [BusinessException.java](mdc:src/main/java/com/vertx/template/exception/BusinessException.java) - 业务异常类
- [logback.xml](mdc:src/main/resources/logback.xml) - 日志配置文件

## 启动脚本
- [run.bat](mdc:run.bat) - Windows启动脚本
- [run.sh](mdc:run.sh) - Linux/Mac启动脚本

# JDK21虚拟线程与Future.await使用指南

本项目充分利用JDK21虚拟线程特性，使用`Future.await()`方法简化异步代码。

## 核心用法

在项目中，我们使用虚拟线程和`Future.await()`方法将异步代码转换为同步风格：

```java
// 使用await直接获取结果，而不是使用回调
JsonObject config = Future.await(ConfigLoader.loadConfig(vertx));

// 直接获取服务结果
List<User> users = Future.await(userService.getUsers());
```

## 关键文件

以下文件展示了不同情境下的`Future.await()`使用方式：

- [MainVerticle.java](mdc:src/main/java/com/vertx/template/MainVerticle.java) - 在Verticle启动流程中使用await
- [ConfigLoader.java](mdc:src/main/java/com/vertx/template/config/ConfigLoader.java) - 在配置加载中使用await
- [UserController.java](mdc:src/main/java/com/vertx/template/controller/UserController.java) - 在HTTP处理器中使用await
- [Run.java](mdc:src/main/java/com/vertx/template/Run.java) - 应用程序入口使用虚拟线程模式

## 注意事项

1. `Future.await()`只能在虚拟线程上调用，否则会抛出异常
2. 项目已配置使用虚拟线程启动Verticle，所有处理器都可以安全使用`Future.await()`
3. 使用try/catch处理异常，替代原有的`.onFailure()`处理方式
4. 不需要显式创建或管理虚拟线程，框架已自动处理

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

# Vert.x 项目编码规范

本规范定义了项目的Java代码风格、结构和最佳实践。

## 变量声明规则

### 变量不可变性
- 所有变量默认声明为 `final`，例如：`final String id = "123";`
- 只有在确实需要修改变量值时才省略 `final`
- 示例见 [CodeStyleExample.java](mdc:src/main/java/com/vertx/template/examples/CodeStyleExample.java)

### 集合类型
- 优先使用不可变集合：`List.of()`, `Set.of()`, `Map.of()`
- 需要可修改的集合时使用线程安全实现：`ConcurrentHashMap`, `CopyOnWriteArrayList`

### 命名约定
- 局部变量：驼峰式，如 `userId`, `productName`
- 常量：全大写+下划线，如 `MAX_RETRY_COUNT`
- 成员变量：不使用前缀，直接驼峰式

## 方法设计规范

### 方法设计原则
- 方法应当短小精悍，最大行数控制在30行以内
- 单一职责原则：一个方法只做一件事
- 参数数量控制在3个以内，超过时使用DTO对象

### 异步方法规范
- 返回 `Future<T>` 而非使用回调
- 使用 `Promise` 创建 `Future`
- 参考 [AnnotationRouterHandler.java](mdc:src/main/java/com/vertx/template/router/handler/AnnotationRouterHandler.java) 中的异步处理模式

## 异常处理

### 异常基类
- 业务异常统一继承 [BusinessException](mdc:src/main/java/com/vertx/template/exception/BusinessException.java)
- 校验异常使用 [ValidationException](mdc:src/main/java/com/vertx/template/exception/ValidationException.java)

### 异常处理原则
- 不要捕获后不处理异常（避免空catch块）
- 异常处理集中在 [ResponseHandler](mdc:src/main/java/com/vertx/template/handler/ResponseHandler.java)
- 错误信息统一格式化并返回，客户端得到一致的错误响应

## 路由与控制器规范

### 路由定义
- 使用注解定义路由，如 `@RestController`, `@GetMapping`
- 路由分组参考 [RouterRegistry](mdc:src/main/java/com/vertx/template/router/RouterRegistry.java)
- 参数注解：`@PathParam`, `@QueryParam`, `@RequestBody` 等

### 控制器设计
- 控制器使用 `@RestController` 和 `@Singleton` 注解
- 依赖注入使用 `@Inject`
- 参考 [ProductController](mdc:src/main/java/com/vertx/template/controller/ProductController.java)

## 响应式编程规范

### Vert.x Best Practices
- 避免阻塞操作，必要时使用 `vertx.executeBlocking()`
- 利用 `Future` 的组合功能：`compose()`, `map()`, `flatMap()`
- 避免嵌套 Future 回调，优先使用链式调用

### 事件循环保护
- 长时间操作必须放在专门的工作线程执行
- 不要在事件循环中使用阻塞I/O或CPU密集型计算

## 数据校验

### Bean Validation
- 实体类使用Jakarta Bean Validation注解
- 示例参考 [Product](mdc:src/main/java/com/vertx/template/model/Product.java)
- 校验执行由 [ValidationUtils](mdc:src/main/java/com/vertx/template/router/validation/ValidationUtils.java) 统一处理

## 日志规范

### 日志级别使用
- ERROR: 影响系统运行的错误
- WARN: 不影响系统但需要关注的异常情况
- INFO: 重要业务事件和状态变化
- DEBUG: 调试信息，生产环境通常不开启

### 日志内容
- 包含关键标识信息如用户ID、请求ID
- 敏感信息（如密码、令牌）必须脱敏后记录

# MVC架构模式

本项目采用扩展的MVC(Model-View-Controller)架构模式，结合响应式编程和依赖注入实现可维护、可测试的代码结构。

## 架构层次

### 1. 控制器层 (Controller)
- 负责接收HTTP请求并调用服务层
- 处理参数验证和请求路由
- 不包含业务逻辑，只负责协调
- 示例：[UserController.java](mdc:src/main/java/com/vertx/template/controller/UserController.java)

### 2. 服务层 (Service)
- 包含核心业务逻辑
- 处理业务规则和数据转换
- 返回Future对象实现异步操作
- 接口与实现分离，便于测试和替换
- 示例：
  - [UserService.java](mdc:src/main/java/com/vertx/template/service/UserService.java) (接口)
  - [UserServiceImpl.java](mdc:src/main/java/com/vertx/template/service/impl/UserServiceImpl.java) (实现)

### 3. 数据访问层 (Repository)
- 负责与数据源交互（数据库、外部API等）
- 提供数据的CRUD操作
- 返回Future对象实现异步操作
- 示例：`UserRepository.java`

### 4. 模型层 (Model)
- 定义数据结构和业务对象
- POJO类、数据传输对象(DTO)
- 示例：[User.java](mdc:src/main/java/com/vertx/template/model/User.java)

### 5. 路由层 (Routes)
- 定义API端点和HTTP方法
- 将请求映射到控制器方法
- 实现了RouteGroup接口的模块化设计
- 示例：[UserRoutes.java](mdc:src/main/java/com/vertx/template/routes/UserRoutes.java)

## 数据流程

1. 请求进入路由层（Routes）
2. 路由将请求传递给控制器（Controller）
3. 控制器验证请求并调用服务（Service）
4. 服务执行业务逻辑，可能调用仓库（Repository）
5. 服务返回结果（通过Future）给控制器
6. 控制器通过ResponseHandler包装响应
7. 响应返回给客户端

## 最佳实践

- 控制器方法应该简短，主要负责参数验证和服务调用
- 所有业务逻辑都应该放在服务层
- 使用接口定义服务契约，实现依赖倒置原则
- 使用Future处理异步操作，避免阻塞事件循环
- 使用BusinessException表示可预期的业务错误

# 路由模块设计指南

本项目采用基于注解的路由设计，类似Spring Boot的路由定义方式，简化开发并提高代码可读性。

## 核心概念

### 1. 路由注解

使用以下注解定义路由：

- `@RestController` - 标记一个类为REST控制器
- `@RequestMapping` - 定义基础URL路径和HTTP方法
- `@GetMapping`, `@PostMapping` 等 - 定义特定HTTP方法的路由

### 2. 参数注解

使用以下注解定义方法参数：

- `@PathParam` - 获取路径参数，例如：`@PathParam("id") String id`
- `@QueryParam` - 获取查询参数，例如：`@QueryParam("name") String name`
- `@RequestBody` - 获取请求体并转换为对象，例如：`@RequestBody User user`
- `@HeaderParam` - 获取请求头参数，例如：`@HeaderParam("Authorization") String token`

### 3. 参数校验

使用Jakarta Bean Validation (JSR 380)进行参数校验：

- `@Valid` - 标记需要校验的参数，例如：`@Valid @RequestBody Product product`
- `@NotNull`, `@NotBlank`, `@Size`, `@Min` 等 - 定义校验规则

### 4. 注解路由处理器

[AnnotationRouterHandler](mdc:src/main/java/com/vertx/template/router/handler/AnnotationRouterHandler.java)负责：

- 自动扫描带有@RestController注解的类
- 处理各种路由注解并注册对应的处理器
- 支持方法参数自动注入和类型转换
- 支持请求参数校验
- 自动处理返回值，支持直接返回数据对象

### 5. 路由注册中心

[RouterRegistry](mdc:src/main/java/com/vertx/template/router/RouterRegistry.java)负责集中管理路由注册：

- 创建并存储Guice注入器和主路由器
- 注册全局中间件
- 通过AnnotationRouterHandler注册基于注解的路由
- 注册全局异常处理器
- 提供统一的Router实例给HTTP服务器

### 6. 全局中间件

[GlobalMiddleware](mdc:src/main/java/com/vertx/template/router/GlobalMiddleware.java)负责处理通用中间件：

- CORS配置
- 请求体解析
- 请求日志和计时
- 其他全局处理逻辑

### 7. 响应处理器

[ResponseHandler](mdc:src/main/java/com/vertx/template/handler/ResponseHandler.java)负责统一处理响应：

- 自动将返回数据包装成标准ApiResponse格式
- 统一处理异常和错误响应，包括参数校验错误
- 设置响应头和状态码

## 实现示例

### 基础控制器示例

```java
@RestController
@RequestMapping("/api/users")
@Singleton
public class UserController {

    @Inject
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("")
    public List<User> getUsers() {
        // 直接返回数据对象，而不是Future
        return Future.await(userService.getUsers());
    }

    @GetMapping("/:id")
    public User getUserById(@PathParam("id") String id) {
        // 使用PathParam注解获取路径参数
        return Future.await(userService.getUserById(id));
    }
}
```

### 高级参数解析和校验示例

```java
@RestController
@RequestMapping("/api/products")
@Singleton
public class ProductController {

    @GetMapping("")
    public List<Product> getAllProducts(
            @QueryParam(value = "minPrice", required = false) Double minPrice,
            @QueryParam(value = "maxPrice", required = false) Double maxPrice) {
        // 支持多个查询参数，自动类型转换
        // ...
    }

    @PostMapping("")
    public Product createProduct(@Valid @RequestBody Product product) {
        // 请求体对象并进行校验
        // ...
    }
}
```

## 实体校验规则示例

```java
public class Product {

    private String id;

    @NotBlank(message = "产品名称不能为空")
    @Size(min = 2, max = 50, message = "产品名称长度必须在2-50之间")
    private String name;

    @NotNull(message = "产品价格不能为空")
    @Min(value = 0, message = "产品价格必须大于等于0")
    private Double price;

    // ...getter和setter
}
```

## 添加新路由

添加新路由的步骤：

1. 创建控制器类并添加`@RestController`注解
2. 添加`@RequestMapping`注解指定基础路径
3. 使用`@GetMapping`、`@PostMapping`等注解定义具体路由方法
4. 控制器方法直接返回数据对象，系统会自动包装成标准响应格式
5. 添加`@Singleton`注解确保单例模式
6. 使用`@Inject`注入所需依赖
7. 路由将被自动扫描并注册

示例：
```java
@RestController
@RequestMapping("/api/products")
@Singleton
public class ProductController {

    private final ProductService productService;

    @Inject
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("")
    public List<Product> getAllProducts() {
        // 直接返回数据，无需包装Future
        return Future.await(productService.getAllProducts());
    }

    @PostMapping("")
    public Product createProduct(RoutingContext ctx) {
        // 解析请求体并直接返回创建的产品
        Product product = ctx.getBodyAsJson().mapTo(Product.class);
        return Future.await(productService.create(product));
    }

    @GetMapping("/:id")
    public Product getProductById(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        return Future.await(productService.getById(id));
    }
}
```

# YAML配置系统指南

本项目使用Vert.x Config模块从YAML文件加载配置。

## 配置文件结构

主配置文件[config.yml](mdc:src/main/resources/config.yml)组织为多个部分：

```yaml
# 服务器配置
server:
  port: 8888
  host: localhost

# 日志配置
logging:
  enabled: true
  request_log: true
  level: INFO

# CORS配置
cors:
  enabled: true
  allowed_origins: "*"
  allowed_methods:
    - GET
    - POST
    - PUT
    - DELETE
  allowed_headers:
    - Content-Type
    - Authorization
```

## 配置加载

[ConfigLoader](mdc:src/main/java/com/vertx/template/config/ConfigLoader.java)类负责加载和缓存配置：

```java
// 配置加载示例
JsonObject config = Future.await(ConfigLoader.loadConfig(vertx));

// 获取缓存的配置
JsonObject cachedConfig = ConfigLoader.getConfig();
```

## 配置使用

在代码中访问配置的示例：

```java
// 获取服务器配置
JsonObject serverConfig = config.getJsonObject("server", new JsonObject());
int port = serverConfig.getInteger("port", 8888);
String host = serverConfig.getString("host", "localhost");

// 获取嵌套配置并提供默认值
JsonObject corsConfig = config.getJsonObject("cors", new JsonObject());
boolean corsEnabled = corsConfig.getBoolean("enabled", true);
```

## 配置优先级

配置加载过程遵循以下优先级（从高到低）：

1. 系统属性 (`-D`参数)
2. 环境变量
3. 配置文件 (config.yml)

可以通过环境变量或系统属性覆盖任何YAML配置。
