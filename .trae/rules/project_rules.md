---
description: Vert.x项目开发规范和架构指南
globs:
alwaysApply: false
---

# Vert.x项目开发规范

本文档定义了基于Vert.x + JDK21虚拟线程的响应式Web应用开发规范。

## 📋 目录

1. [项目架构](#项目架构)
2. [编码规范](#编码规范)
3. [技术栈规范](#技术栈规范)
4. [开发流程](#开发流程)

---

## 项目架构

### 核心组件结构

```
src/main/java/com/vertx/template/
├── Run.java                    # 应用入口
├── MainVerticle.java           # 主Verticle
├── config/
│   └── ConfigLoader.java       # 配置加载器
├── controller/                 # 控制器层
├── service/                    # 服务层
├── model/                      # 数据模型
├── router/                     # 路由系统
├── handler/                    # 处理器
└── exception/                  # 异常定义
```

### MVC架构层次

| 层级           | 职责                               | 示例文件                        |
| -------------- | ---------------------------------- | ------------------------------- |
| **Controller** | 接收HTTP请求，参数验证，调用服务层 | `UserController.java`           |
| **Service**    | 业务逻辑处理，数据转换             | `UserService.java`              |
| **Repository** | 数据访问，外部API调用              | `UserRepository.java`           |
| **Model**      | 数据结构定义，DTO对象              | `User.java`, `ApiResponse.java` |
| **Router**     | 路由定义和注册                     | `UserRoutes.java`               |

---

## 编码规范

### 🔧 变量声明规则

#### 不可变性原则
- **默认使用 `final`**：所有变量默认声明为 `final`
- **集合类型**：优先使用不可变集合 `List.of()`, `Set.of()`, `Map.of()`
- **可变集合**：使用线程安全实现 `ConcurrentHashMap`, `CopyOnWriteArrayList`

```java
// ✅ 推荐
final String userId = "123";
final List<String> names = List.of("Alice", "Bob");
final Map<String, Object> config = Map.of("port", 8080);

// ❌ 避免
String userId = "123";  // 缺少final
ArrayList<String> names = new ArrayList<>();  // 非线程安全
```

#### 命名约定
| 类型     | 规则           | 示例                            |
| -------- | -------------- | ------------------------------- |
| 局部变量 | 小驼峰         | `userId`, `productName`         |
| 常量     | 全大写+下划线  | `MAX_RETRY_COUNT`               |
| 成员变量 | 小驼峰，无前缀 | `userService`, `config`         |
| 类名     | 大驼峰         | `UserController`, `ApiResponse` |

### 🏗️ 方法设计规范

#### 设计原则
- **行数限制**：方法最大30行
- **参数限制**：最多3个参数，超过使用DTO对象
- **单一职责**：一个方法只做一件事
- **返回类型**：异步方法返回 `Future<T>`

```java
// ✅ 推荐
public Future<User> getUserById(final String id) {
    return userRepository.findById(id)
        .compose(this::validateUser)
        .map(this::enrichUserData);
}

// ❌ 避免
public void processUser(String id, String name, String email,
                       boolean active, Date created) { // 参数过多
    // 方法过长...
}
```

### 🔄 异步编程规范

#### 异步方法规范
| 规则     | 说明                         | 示例                                         |
| -------- | ---------------------------- | -------------------------------------------- |
| 方法命名 | 异步方法以`Async`结尾        | `getUserAsync()`, `saveDataAsync()`          |
| 返回类型 | 必须返回`Future<T>`          | `Future<User>`, `Future<List<Order>>`        |
| 调用方式 | 使用`Future.await()`同步调用 | `User user = Future.await(getUserAsync(id))` |

#### 异步方法示例
```java
// 异步方法定义
public Future<User> getUserAsync(String id) {
    return vertx.executeBlocking(promise -> {
        User user = userRepository.findById(id);
        promise.complete(user);
    });
}

// 异步方法调用
public User getUser(String id) {
    return Future.await(getUserAsync(id));
}
```

### 🚨 异常处理规范

#### 异常分层策略
| 异常类型                | 用途         | 处理方式                   | HTTP状态码 |
| ----------------------- | ------------ | -------------------------- | ---------- |
| **SystemException**     | 系统技术错误 | 记录详细日志，返回通用错误 | 500        |
| **BusinessException**   | 业务逻辑错误 | 记录简要日志，返回具体错误 | 400-499    |
| **ValidationException** | 参数验证失败 | 返回具体验证错误信息       | 400        |

#### 异常类层次结构
```
RuntimeException
├── SystemException (系统异常)
│   ├── DatabaseException (数据库异常)
│   ├── NetworkException (网络异常)
│   └── ConfigurationException (配置异常)
├── BusinessException (业务异常)
│   ├── UserNotFoundException (用户不存在)
│   ├── InsufficientPermissionException (权限不足)
│   └── DuplicateResourceException (资源重复)
└── ValidationException (验证异常)
    ├── InvalidParameterException (参数无效)
    └── MissingParameterException (参数缺失)
```

#### 异常使用示例
```java
// Service层 - 业务异常
public Future<User> getUserByIdAsync(String id) {
    if (StringUtils.isBlank(id)) {
        throw new ValidationException("用户ID不能为空");
    }

    User user = userRepository.findById(id);
    if (user == null) {
        throw new BusinessException(404, "用户不存在: " + id);
    }

    return Future.succeededFuture(user);
}

// Controller层 - 异常自动处理
@GetMapping("/:id")
public User getUserById(@PathParam("id") String id) {
    return Future.await(userService.getUserByIdAsync(id));
}
```

## 📝 日志记录规范

### 日志级别使用
| 级别      | 用途                   | 示例场景                   |
| --------- | ---------------------- | -------------------------- |
| **ERROR** | 系统错误，需要立即关注 | 数据库连接失败、未捕获异常 |
| **WARN**  | 警告信息，可能的问题   | 配置缺失、性能警告         |
| **INFO**  | 重要的业务信息         | 用户登录、订单创建         |
| **DEBUG** | 调试信息               | 方法调用、参数值           |
| **TRACE** | 详细的跟踪信息         | 详细的执行流程             |

### 日志记录示例
```java
@Slf4j
public class UserService {

    public Future<User> createUserAsync(CreateUserRequest request) {
        // INFO: 记录重要业务操作
        log.info("Creating user with username: {}", request.getUsername());

        try {
            // DEBUG: 记录详细处理步骤
            log.debug("Validating user data: {}", request);

            User user = userRepository.save(request.toUser());

            // INFO: 记录操作结果
            log.info("User created successfully with ID: {}", user.getId());

            return Future.succeededFuture(user);
        } catch (Exception e) {
            // ERROR: 记录错误信息
            log.error("Failed to create user: {}", request.getUsername(), e);
            throw e;
        }
    }
}
```

### 敏感信息处理
```java
// ❌ 避免：记录敏感信息
log.info("User login: username={}, password={}", username, password);

// ✅ 推荐：脱敏处理
log.info("User login: username={}, password=***", username);
```

---

## 技术栈规范

### 🧵 JDK21虚拟线程

#### 核心特性
- **Future.await()**：将异步代码转换为同步风格
- **自动管理**：框架自动处理虚拟线程创建和管理
- **性能优势**：高并发场景下显著提升性能

```java
// ✅ 推荐：使用Future.await()
JsonObject config = Future.await(ConfigLoader.loadConfig(vertx));
List<User> users = Future.await(userService.getUsers());

// ❌ 避免：传统回调方式
userService.getUsers().onSuccess(users -> {
    // 回调嵌套
}).onFailure(error -> {
    // 错误处理
});
```

#### 使用约束
- `Future.await()` 只能在虚拟线程上调用
- 使用 `try/catch` 处理异常，替代 `.onFailure()`
- 所有HTTP处理器都可安全使用 `Future.await()`

### 💉 依赖注入 (Google Guice)

#### 核心注解
| 注解         | 用途       | 示例                                                 |
| ------------ | ---------- | ---------------------------------------------------- |
| `@Inject`    | 标记注入点 | `@Inject public UserController(UserService service)` |
| `@Singleton` | 单例模式   | `@Singleton public class UserServiceImpl`            |
| `@Provides`  | 工厂方法   | `@Provides Router provideRouter()`                   |

#### 配置步骤
1. **创建服务**：定义接口和实现类
2. **配置绑定**：在 `AppModule` 中添加绑定
3. **注入使用**：通过构造函数注入依赖

```java
// 1. 服务定义
public interface UserService {
    Future<List<User>> getUsers();
}

@Singleton
public class UserServiceImpl implements UserService {
    @Inject
    public UserServiceImpl(UserRepository repository) {
        this.repository = repository;
    }
}

// 2. 模块配置
public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(UserService.class).to(UserServiceImpl.class);
    }
}

// 3. 控制器注入
@RestController
@Singleton
public class UserController {
    @Inject
    public UserController(UserService userService) {
        this.userService = userService;
    }
}
```

### 🛣️ 路由系统 (注解驱动)

#### 路由注解
| 注解              | 用途           | 示例                                  |
| ----------------- | -------------- | ------------------------------------- |
| `@RestController` | 标记REST控制器 | `@RestController`                     |
| `@RequestMapping` | 定义基础路径   | `@RequestMapping("/api/users")`       |
| `@GetMapping`     | GET请求映射    | `@GetMapping("/:id")`                 |
| `@PostMapping`    | POST请求映射   | `@PostMapping("")`                    |
| `@PathParam`      | 路径参数       | `@PathParam("id") String id`          |
| `@QueryParam`     | 查询参数       | `@QueryParam("name") String name`     |
| `@RequestBody`    | 请求体         | `@RequestBody User user`              |
| `@Valid`          | 参数校验       | `@Valid @RequestBody Product product` |

#### 控制器示例
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
        return Future.await(userService.getUsers());
    }

    @GetMapping("/:id")
    public User getUserById(@PathParam("id") String id) {
        return Future.await(userService.getUserById(id));
    }

    @PostMapping("")
    public User createUser(@Valid @RequestBody User user) {
        return Future.await(userService.createUser(user));
    }
}
```

### 📡 API响应处理

#### 统一响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": { /* 业务数据 */ },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### 核心组件
- **ResponseHandler**：自动包装返回数据为标准格式
- **GlobalExceptionHandler**：全局异常处理和日志记录
- **ApiResponse**：统一响应模型
- **BusinessException**：业务异常定义

#### 使用方式
```java
// 控制器直接返回业务数据，自动包装
public User getUserById(@PathParam("id") String id) {
    if (StringUtils.isBlank(id)) {
        throw new ValidationException("用户ID不能为空");
    }
    return Future.await(userService.getUserById(id));
}
```

### 📊 数据验证规范

#### Bean Validation注解
| 注解        | 用途           | 示例                                       |
| ----------- | -------------- | ------------------------------------------ |
| `@NotNull`  | 不能为null     | `@NotNull String name`                     |
| `@NotBlank` | 不能为空字符串 | `@NotBlank String username`                |
| `@Size`     | 长度限制       | `@Size(min=3, max=20) String name`         |
| `@Email`    | 邮箱格式       | `@Email String email`                      |
| `@Pattern`  | 正则表达式     | `@Pattern(regexp="^[0-9]+$") String phone` |
| `@Min/@Max` | 数值范围       | `@Min(0) @Max(100) Integer age`            |

#### 验证示例
```java
// 请求DTO
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
    private String username;

    @Email(message = "邮箱格式不正确")
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @Min(value = 18, message = "年龄不能小于18岁")
    private Integer age;
}

// 控制器使用
@PostMapping("")
public User createUser(@Valid @RequestBody CreateUserRequest request) {
    return Future.await(userService.createUserAsync(request));
}
```

### ⚙️ 配置系统 (YAML)

#### 配置文件结构
```yaml
# config.yml
server:
  port: 8888
  host: localhost

logging:
  enabled: true
  level: INFO

cors:
  enabled: true
  allowed_origins: "*"
```

#### 配置加载
```java
// 加载配置
JsonObject config = Future.await(ConfigLoader.loadConfig(vertx));

// 获取配置值
int port = config.getJsonObject("server").getInteger("port", 8888);
```

#### 配置优先级
1. 系统属性 (`-D`参数)
2. 环境变量
3. 配置文件 (`config.yml`)

---

## 🏗️ 项目架构总结

本项目采用现代化的Java技术栈，结合Vert.x的响应式特性和注解驱动的开发模式，实现高性能、易维护的Web应用。

### 核心特性
- **🚀 高性能**：基于Vert.x事件循环和虚拟线程
- **📝 注解驱动**：类似Spring Boot的开发体验
- **🔧 依赖注入**：Google Guice提供IoC容器
- **⚡ 异步编程**：Future.await()简化异步调用
- **🛡️ 统一异常处理**：全局异常处理和响应包装
- **✅ 数据验证**：Bean Validation自动参数校验
- **📊 结构化日志**：完善的日志记录规范
- **⚙️ 配置管理**：YAML配置文件支持

### 开发流程
1. **定义实体模型**：创建带验证注解的POJO类
2. **实现Repository**：数据访问层，处理数据库操作
3. **编写Service**：业务逻辑层，处理核心业务
4. **创建Controller**：控制器层，处理HTTP请求
5. **配置路由**：自动扫描注册路由映射
6. **异常处理**：全局异常处理器自动处理
7. **响应包装**：统一的API响应格式

### 最佳实践
- 遵循单一职责原则，每层专注自己的职责
- 使用依赖注入管理组件依赖关系
- 采用异步编程模式提升性能
- 实施完善的异常处理和日志记录
- 通过Bean Validation确保数据质量
- 使用配置文件管理应用参数
