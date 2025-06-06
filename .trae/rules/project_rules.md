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

### 核心组件结构（符合阿里巴巴Java开发规范）

```
src/main/java/com/vertx/template/
├── Run.java                    # 应用入口
├── config/                     # 配置模块
│   ├── ConfigLoader.java       # 配置加载器
│   ├── DatabaseConfig.java     # 数据库配置
│   ├── JacksonConfig.java      # JSON序列化配置
│   └── RouterConfig.java       # 路由配置
├── controller/                 # 控制器层（Web层）
│   ├── AuthController.java     # 认证控制器
│   ├── ProductController.java  # 产品控制器
│   ├── PublicController.java   # 公开接口控制器
│   ├── TestController.java     # 测试控制器
│   └── UserController.java     # 用户控制器
├── service/                    # 服务层（业务逻辑层）
│   ├── impl/                   # 服务实现类
│   └── UserService.java        # 用户服务接口
├── repository/                 # 数据访问层（持久层）
│   ├── common/                 # 通用仓储接口
│   │   └── BaseRepository.java # 基础仓储接口
│   ├── impl/                   # 数据访问实现类
│   └── UserRepository.java     # 用户仓储接口
├── model/                      # 数据模型层
│   ├── annotation/             # 自定义注解
│   │   ├── Column.java         # 列映射注解
│   │   ├── Id.java             # 主键注解
│   │   └── Table.java          # 表映射注解
│   ├── dto/                    # 数据传输对象（Data Transfer Object）
│   │   ├── ApiResponse.java    # 统一API响应格式
│   │   └── UserDto.java        # 用户DTO
│   ├── entity/                 # 数据库实体对象
│   │   ├── BaseEntity.java     # 基础实体类
│   │   ├── Product.java        # 产品实体
│   │   └── User.java           # 用户实体
│   ├── vo/                     # 视图对象（View Object）
│   └── bo/                     # 业务对象（Business Object）
├── router/                     # 路由系统
│   ├── annotation/             # 路由注解
│   │   ├── GetMapping.java     # GET请求映射
│   │   ├── PostMapping.java    # POST请求映射
│   │   ├── RequestMapping.java # 请求映射基础注解
│   │   └── RestController.java # REST控制器注解
│   ├── cache/                  # 路由缓存
│   │   ├── MethodMetadata.java # 方法元数据
│   │   └── ReflectionCache.java# 反射缓存
│   └── handler/                # 路由处理器
│       └── AnnotationRouterHandler.java # 注解路由处理器
├── middleware/                 # 中间件系统
│   ├── auth/                   # 认证中间件
│   │   ├── annotation/         # 认证注解
│   │   │   ├── AuthType.java   # 认证类型枚举
│   │   │   └── RequireAuth.java# 认证注解
│   │   ├── authenticator/      # 认证器实现
│   │   └── AuthenticationManager.java # 认证管理器
│   ├── ratelimit/              # 限流中间件
│   │   ├── annotation/         # 限流注解
│   │   │   ├── RateLimit.java  # 限流注解
│   │   │   ├── RateLimitDimension.java # 限流维度
│   │   │   └── RateLimitType.java # 限流算法类型
│   │   ├── core/               # 限流核心实现
│   │   │   ├── RateLimiter.java# 限流器接口
│   │   │   ├── RateLimitManager.java # 限流管理器
│   │   │   ├── RateLimitResult.java # 限流结果
│   │   │   └── RateLimitKeyGenerator.java # 限流键生成器
│   │   ├── interceptor/        # 限流拦截器
│   │   │   └── RateLimitInterceptor.java # 限流拦截器
│   │   └── impl/               # 限流算法实现
│   ├── core/                   # 核心中间件
│   │   ├── impl/               # 中间件实现
│   │   │   └── CorsMiddleware.java # CORS中间件
│   │   └── MiddlewareChain.java# 中间件链
│   └── GlobalMiddleware.java   # 全局中间件管理器
├── di/                         # 依赖注入模块
│   └── AppModule.java          # 应用模块配置
├── exception/                  # 异常定义
│   ├── BusinessException.java  # 业务异常
│   ├── RateLimitException.java # 限流异常
│   ├── SystemException.java    # 系统异常
│   └── ValidationException.java# 验证异常
├── constants/                  # 常量定义
│   ├── HttpConstants.java      # HTTP常量
│   └── RouterConstants.java    # 路由常量
├── utils/                      # 工具类
├── examples/                   # 示例代码
│   └── CodeStyleExample.java  # 代码风格示例
└── verticle/                   # Verticle组件
    └── MainVerticle.java       # 主Verticle
```

### 阿里巴巴分层架构规范

#### 分层领域模型规约

| 层级                     | 说明                                                                                                                                                                               |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **开放API层**            | 可直接封装Service接口暴露成RPC接口；通过Web封装成http接口；网关控制层等                                                                                                            |
| **终端显示层**           | 各个端的模板渲染并执行显示的层。当前主要是velocity渲染，JS渲染，JSP渲染，移动端展示等                                                                                              |
| **Web层**                | 主要是对访问控制进行转发，各类基本参数校验，或者不复用的业务简单处理等                                                                                                             |
| **Service层**            | 相对具体的业务逻辑服务层                                                                                                                                                           |
| **Manager层**            | 通用业务处理层，它有如下特征：1）对第三方平台封装的层，预处理返回结果及转化异常信息；2）对Service层通用能力的下沉，如缓存方案、中间件通用处理；3）与DAO层交互，对多个DAO的组合复用 |
| **DAO层**                | 数据访问层，与底层MySQL、Oracle、Hbase等进行数据交互                                                                                                                               |
| **外部接口或第三方平台** | 包括其它部门RPC开放接口，基础平台，其它公司的HTTP接口                                                                                                                              |

### 分层架构职责（符合阿里巴巴规范）

| 层级           | 职责                               | 示例文件                           | 阿里规范对应层 |
| -------------- | ---------------------------------- | ---------------------------------- | -------------- |
| **Controller** | 接收HTTP请求，参数验证，调用服务层 | `UserController.java`              | Web层          |
| **Service**    | 业务逻辑处理，数据转换             | `UserService.java`                 | Service层      |
| **Repository** | 数据访问，外部API调用              | `UserRepository.java`              | DAO层          |
| **DTO**        | 数据传输对象，用于层间数据传递     | `UserDto.java`, `ApiResponse.java` | 领域模型       |
| **Entity**     | 数据库实体对象，与数据表对应       | `User.java`, `Product.java`        | 领域模型       |
| **VO**         | 视图对象，用于前端展示             | `UserVo.java`                      | 领域模型       |
| **BO**         | 业务对象，封装业务逻辑             | `UserBo.java`                      | 领域模型       |
| **Router**     | 路由定义和注册                     | `UserRoutes.java`                  | Web层          |

### 领域模型命名规范

#### 数据对象命名约定
| 对象类型     | 命名规则    | 说明                             | 示例                                  |
| ------------ | ----------- | -------------------------------- | ------------------------------------- |
| **DTO**      | XxxDto      | 数据传输对象，用于接口间数据传递 | `UserDto.java`, `ProductDto.java`     |
| **Entity**   | Xxx         | 数据库实体对象，与数据表一一对应 | `User.java`, `Product.java`           |
| **VO**       | XxxVo       | 视图对象，用于前端展示           | `UserVo.java`, `ProductVo.java`       |
| **BO**       | XxxBo       | 业务对象，封装业务逻辑的对象     | `UserBo.java`, `ProductBo.java`       |
| **Query**    | XxxQuery    | 查询参数对象                     | `UserQuery.java`, `ProductQuery.java` |
| **Request**  | XxxRequest  | 请求参数对象                     | `CreateUserRequest.java`              |
| **Response** | XxxResponse | 响应结果对象                     | `UserResponse.java`                   |

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

#### 命名约定（符合阿里巴巴Java开发规范）

##### 基础命名规则
| 类型     | 规则           | 示例                            | 阿里规范说明                                         |
| -------- | -------------- | ------------------------------- | ---------------------------------------------------- |
| 局部变量 | 小驼峰         | `userId`, `productName`         | 必须使用lowerCamelCase风格                           |
| 常量     | 全大写+下划线  | `MAX_RETRY_COUNT`               | 常量命名全部大写，单词间用下划线隔开                 |
| 成员变量 | 小驼峰，无前缀 | `userService`, `config`         | 不允许任何未定义规范的前缀                           |
| 类名     | 大驼峰         | `UserController`, `ApiResponse` | 必须使用UpperCamelCase风格                           |
| 方法名   | 小驼峰         | `getUserById`, `createUser`     | 必须使用lowerCamelCase风格                           |
| 包名     | 全小写         | `com.vertx.template.service`    | 全部小写，点分隔符之间有且仅有一个自然语义的英语单词 |

##### 包命名规范
| 包类型         | 命名规则                | 示例                              | 说明                |
| -------------- | ----------------------- | --------------------------------- | ------------------- |
| **基础包**     | `com.{公司}.{项目}`     | `com.vertx.template`              | 公司域名倒置+项目名 |
| **控制器包**   | `{基础包}.controller`   | `com.vertx.template.controller`   | Web层控制器         |
| **服务包**     | `{基础包}.service`      | `com.vertx.template.service`      | 业务逻辑层          |
| **服务实现包** | `{基础包}.service.impl` | `com.vertx.template.service.impl` | 服务实现类          |
| **数据访问包** | `{基础包}.repository`   | `com.vertx.template.repository`   | 数据访问层          |
| **实体包**     | `{基础包}.model.entity` | `com.vertx.template.model.entity` | 数据库实体          |
| **DTO包**      | `{基础包}.model.dto`    | `com.vertx.template.model.dto`    | 数据传输对象        |
| **VO包**       | `{基础包}.model.vo`     | `com.vertx.template.model.vo`     | 视图对象            |
| **BO包**       | `{基础包}.model.bo`     | `com.vertx.template.model.bo`     | 业务对象            |
| **枚举包**     | `{基础包}.enums`        | `com.vertx.template.enums`        | 枚举类              |
| **常量包**     | `{基础包}.constants`    | `com.vertx.template.constants`    | 常量定义            |
| **工具包**     | `{基础包}.utils`        | `com.vertx.template.utils`        | 工具类              |
| **异常包**     | `{基础包}.exception`    | `com.vertx.template.exception`    | 异常定义            |

##### 类命名特殊规范
| 类型       | 命名规则               | 示例                                         | 说明                                           |
| ---------- | ---------------------- | -------------------------------------------- | ---------------------------------------------- |
| **抽象类** | Abstract开头或Base开头 | `AbstractUserService`, `BaseEntity`          | 抽象类命名使用Abstract或Base开头               |
| **异常类** | Exception结尾          | `UserNotFoundException`, `BusinessException` | 异常类命名使用Exception结尾                    |
| **测试类** | Test结尾               | `UserServiceTest`, `UserControllerTest`      | 测试类命名以它要测试的类的名称开始，以Test结尾 |
| **工具类** | Utils或Helper结尾      | `StringUtils`, `DateHelper`                  | 工具类命名使用Utils或Helper结尾                |
| **配置类** | Config结尾             | `DatabaseConfig`, `RedisConfig`              | 配置类命名使用Config结尾                       |
| **常量类** | Constants结尾          | `UserConstants`, `SystemConstants`           | 常量类命名使用Constants结尾                    |

##### 代码格式规范（阿里巴巴规范）

###### 缩进与空格
- **缩进**：使用4个空格，禁止使用tab字符
- **大括号**：左大括号前不换行，左大括号后换行；右大括号前换行，右大括号后还有else等代码则不换行
- **小括号**：左小括号和字符之间不出现空格；右小括号和字符之间也不出现空格
- **运算符**：任何二目、三目运算符的左右两边都需要加一个空格

```java
// 正确示例
if (condition) {
    doSomething();
} else {
    doOtherThing();
}

// 运算符空格
int result = a + b * c;
boolean flag = (x > 0) && (y < 10);
```

###### 换行规范
- **方法参数**：在逗号后进行换行，在运算符前换行
- **点号**：在点号前换行，如：`StringBuffer.append(str).append(str2)`
- **方法调用**：超过120个字符需要换行

```java
// 方法参数换行
public void method(String param1,
                  String param2,
                  String param3) {
    // 方法体
}

// 链式调用换行
StringBuffer sb = new StringBuffer()
    .append("Hello")
    .append(" ")
    .append("World");
```

##### 注释规范（阿里巴巴规范）

###### 类注释
```java
/**
 * 用户服务实现类
 *
 * @author 开发者姓名
 * @since 1.0.0
 */
public class UserServiceImpl implements UserService {
    // 类实现
}
```

###### 方法注释
```java
/**
 * 根据用户ID获取用户信息
 *
 * @param userId 用户ID，不能为空
 * @return 用户信息，如果用户不存在返回null
 * @throws IllegalArgumentException 当userId为空时抛出
 */
public UserDto getUserById(String userId) {
    // 方法实现
}
```

###### 字段注释
```java
/**
 * 用户服务，用于处理用户相关业务逻辑
 */
private final UserService userService;

/** 最大重试次数 */
private static final int MAX_RETRY_COUNT = 3;
```

###### 特殊注释规范
- **TODO注释**：标记待办事项，格式：`// TODO: [日期][处理人] 具体描述`
- **FIXME注释**：标记需要修复的问题，格式：`// FIXME: [日期][处理人] 问题描述`
- **废弃注释**：使用`@Deprecated`注解，并说明替代方案

```java
// TODO: 2024-01-15 张三 需要添加参数验证
public void createUser(UserDto user) {
    // 实现
}

/**
 * @deprecated 该方法已废弃，请使用 {@link #getUserById(String)} 替代
 */
@Deprecated
public User getUser(String id) {
    return getUserById(id);
}
```

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
| `@PutMapping`     | PUT请求映射    | `@PutMapping("/:id")`                 |
| `@DeleteMapping`  | DELETE请求映射 | `@DeleteMapping("/:id")`              |
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

### 🗄️ 数据库映射注解

#### 设计原则
本项目采用**约定优于配置**的数据库映射策略：
- **表名**：直接使用 `@Table` 注解定义的名称
- **列名**：默认使用驼峰转蛇形命名（如：`userName` → `user_name`），特殊情况才使用 `@Column` 注解
- **简洁性**：减少注解使用，提高代码可读性

#### 映射注解
| 注解      | 用途                     | 示例                                               |
| --------- | ------------------------ | -------------------------------------------------- |
| `@Table`  | 标识实体对应的数据库表名 | `@Table("users")`                                  |
| `@Column` | 特殊情况下指定列名或属性 | `@Column(value = "created_at", updatable = false)` |
| `@Id`     | 标识主键字段             | `@Id private Long id;`                             |

### 🔐 认证与授权系统

#### 认证注解
| 注解          | 用途               | 示例                                    |
| ------------- | ------------------ | --------------------------------------- |
| `@RequireAuth`| 标记需要认证的接口 | `@RequireAuth(AuthType.JWT)`            |
| `AuthType`    | 认证类型枚举       | `JWT`, `BASIC`, `NONE`                  |

#### 认证类型
| 类型    | 说明           | 使用场景                 |
| ------- | -------------- | ------------------------ |
| `JWT`   | JWT令牌认证    | 标准API认证（默认）      |
| `BASIC` | 基础认证       | 简单的用户名密码认证     |
| `NONE`  | 无需认证       | 公开接口                 |

#### 认证使用示例
```java
// 类级别认证 - 所有方法都需要JWT认证
@RestController
@RequestMapping("/api/users")
@RequireAuth(AuthType.JWT)
public class UserController {
    // 所有方法都需要JWT认证
}

// 方法级别认证 - 覆盖类级别配置
@RestController
@RequestMapping("/api/public")
@RequireAuth(AuthType.NONE) // 类级别：无需认证
public class PublicController {
    
    @GetMapping("/info")
    public String getInfo() {
        // 继承类级别：无需认证
        return "公开信息";
    }
    
    @PostMapping("/sensitive")
    @RequireAuth(AuthType.JWT) // 方法级别：需要JWT认证
    public String getSensitiveData() {
        // 覆盖类级别：需要JWT认证
        return "敏感数据";
    }
}
```

### ⚡ 限流系统

#### 限流注解
| 注解        | 用途           | 示例                                           |
| ----------- | -------------- | ---------------------------------------------- |
| `@RateLimit`| 标记需要限流的接口 | `@RateLimit(limit=100, window=60)`        |

#### 限流配置参数
| 参数        | 类型           | 说明                     | 默认值        |
| ----------- | -------------- | ------------------------ | ------------- |
| `limit`     | int            | 限流阈值（请求数量）     | 100           |
| `window`    | int            | 时间窗口（秒）           | 60            |
| `timeUnit`  | TimeUnit       | 时间单位                 | SECONDS       |
| `type`      | RateLimitType  | 限流算法类型             | FIXED_WINDOW  |
| `dimension` | RateLimitDimension | 限流维度             | IP            |
| `message`   | String         | 限流提示信息             | "请求过于频繁" |

#### 限流算法类型
| 类型           | 说明                     | 适用场景               |
| -------------- | ------------------------ | ---------------------- |
| `FIXED_WINDOW` | 固定窗口算法             | 简单限流场景           |
| `SLIDING_WINDOW` | 滑动窗口算法           | 精确限流场景           |
| `TOKEN_BUCKET` | 令牌桶算法               | 允许突发流量           |
| `LEAKY_BUCKET` | 漏桶算法                 | 平滑限流               |

#### 限流维度
| 维度       | 说明                     | 使用场景               |
| ---------- | ------------------------ | ---------------------- |
| `IP`       | 基于客户端IP地址限流     | 防止单个IP恶意请求     |
| `USER`     | 基于用户ID限流           | 防止单个用户过度使用   |
| `API`      | 基于API接口限流          | 保护特定接口           |
| `GLOBAL`   | 全局限流                 | 保护整体系统资源       |

#### 限流使用示例
```java
@RestController
@RequestMapping("/api/users")
@RateLimit(limit = 1000, window = 3600) // 类级别：每小时1000次
public class UserController {
    
    @GetMapping("")
    public List<User> getUsers() {
        // 继承类级别限流：每小时1000次
        return userService.getUsers();
    }
    
    @PostMapping("")
    @RateLimit(
        limit = 10,
        window = 60,
        dimension = RateLimitDimension.USER,
        message = "创建用户过于频繁，请稍后再试"
    )
    public User createUser(@RequestBody User user) {
        // 方法级别限流：每分钟10次，按用户限流
        return userService.createUser(user);
    }
    
    @PostMapping("/batch")
    @RateLimit(
        limit = 5,
        window = 300,
        type = RateLimitType.TOKEN_BUCKET,
        dimension = RateLimitDimension.IP
    )
    public List<User> batchCreateUsers(@RequestBody List<User> users) {
        // 批量操作：5分钟5次，令牌桶算法，按IP限流
        return userService.batchCreateUsers(users);
    }
}
```

#### 命名转换规则
| Java字段名  | 数据库列名   | 是否需要注解 | 说明                      |
| ----------- | ------------ | ------------ | ------------------------- |
| `id`        | `id`         | 否           | 简单字段，直接映射        |
| `username`  | `username`   | 否           | 简单字段，直接映射        |
| `firstName` | `first_name` | 否           | 驼峰自动转蛇形            |
| `createdAt` | `created_at` | 是           | 需要@Column指定下划线命名 |
| `isActive`  | `is_active`  | 否           | 驼峰自动转蛇形            |

#### 实体类示例
```java
@Data
@EqualsAndHashCode(callSuper = false)
@Table("users")  // 指定表名
public class User extends BaseEntity {

  @Id  // 主键标识，自动映射为 id 列
  private Long id;

  @NotBlank(message = "用户名不能为空")
  private String username;  // 自动映射为 username 列

  @Email(message = "邮箱格式不正确")
  private String email;  // 自动映射为 email 列

  @Column(value = "created_at", updatable = false)  // 特殊情况：需要下划线命名且不可更新
  private LocalDateTime createdAt;

  @Column("updated_at")  // 特殊情况：需要下划线命名
  private LocalDateTime updatedAt;

  private boolean active;  // 自动映射为 active 列
}
```

#### 注解属性说明
**@Table 属性：**
- `value()`: 表名（主要属性）
- `name()`: 表名（备用属性，与value等效）

**@Column 属性：**
- `value()`: 列名（主要属性）
- `name()`: 列名（备用属性，与value等效）
- `insertable()`: 是否可插入（默认true）
- `updatable()`: 是否可更新（默认true）
- `primaryKey()`: 是否为主键（默认false）

**@Id 属性：**
- `generated()`: 主键是否自动生成（默认true）

#### 使用优势
1. **约定优于配置**：减少注解使用，大部分字段无需手动配置
2. **自动命名转换**：智能的驼峰转蛇形命名，符合数据库规范
3. **代码简洁性**：只在特殊情况下使用注解，保持代码整洁
4. **动态SQL生成**：根据注解信息和命名约定自动生成SQL语句
5. **字段级控制**：支持字段级别的插入和更新控制
6. **类型安全**：编译时检查，避免运行时错误

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
- **🚀 高性能**：基于Vert.x事件循环和JDK21虚拟线程
- **📝 注解驱动**：类似Spring Boot的开发体验
- **🔧 依赖注入**：Google Guice提供IoC容器
- **⚡ 异步编程**：Future.await()简化异步调用
- **🛡️ 统一异常处理**：全局异常处理和响应包装
- **✅ 数据验证**：Bean Validation自动参数校验
- **🗄️ 数据库映射**：基于注解的ORM映射，约定优于配置
- **🔐 认证授权**：基于注解的多类型认证系统
- **⚡ 智能限流**：多算法、多维度的限流保护
- **🌐 中间件系统**：模块化的中间件架构
- **📊 结构化日志**：完善的日志记录规范
- **⚙️ 配置管理**：YAML配置文件支持
- **📋 阿里巴巴规范**：严格遵循阿里巴巴Java开发手册

### 开发流程
1. **定义实体模型**：创建带验证注解的POJO类
2. **实现Repository**：数据访问层，处理数据库操作
3. **编写Service**：业务逻辑层，处理核心业务
4. **创建Controller**：控制器层，处理HTTP请求
5. **配置认证**：使用@RequireAuth注解配置接口认证
6. **配置限流**：使用@RateLimit注解配置接口限流
7. **配置路由**：自动扫描注册路由映射
8. **异常处理**：全局异常处理器自动处理
9. **响应包装**：统一的API响应格式
10. **中间件配置**：根据需要配置CORS、日志等中间件

### 阿里巴巴Java开发规范最佳实践

#### 编程规约
- **命名风格**：严格遵循驼峰命名法，包名全小写，类名大驼峰，方法名小驼峰
- **常量定义**：不允许任何魔法值直接出现在代码中，必须定义有意义的常量
- **代码格式**：使用4个空格缩进，禁用tab字符，行宽不超过120字符
- **OOP规约**：避免通过一个类的对象引用访问此类的静态变量或静态方法
- **集合处理**：使用entrySet()遍历Map类集合，不要使用keySet()方式遍历

#### 异常处理
- **异常设计**：异常不要用来做流程控制，条件控制
- **异常捕获**：有try块放到了事务代码中，catch异常后，如果需要回滚事务，一定要注意手动回滚事务
- **异常抛出**：方法的返回值可以为null，不强制返回空集合，或者空对象等，必须添加注释充分说明什么情况下会返回null值

#### 日志规约
- **日志级别**：应用中不可直接使用日志系统（Log4j、Logback）中的API，而应依赖使用日志框架SLF4J中的API
- **日志格式**：日志格式统一，便于日志分析和问题排查
- **敏感信息**：避免重复打印日志，浪费磁盘空间，务必在log4j.xml中设置additivity=false

#### 单元测试
- **测试覆盖率**：单元测试代码必须写在如下工程目录：src/test/java，不允许写在业务代码目录下
- **测试方法**：单元测试方法名要求：test[Method]_[Scenario]_[ExpectedBehavior]
- **断言使用**：单元测试中不准使用System.out来进行人肉验证，必须使用assert来验证

#### 安全规约
- **SQL注入**：页面传递参数必须进行校验，因为SQL注入不仅仅是web安全问题，也是数据库安全问题
- **XSS防护**：在使用平台资源，譬如短信、邮件、电话、下单、支付，必须实现正确的防重放的机制
- **权限控制**：表单、AJAX提交必须执行CSRF安全验证

#### MySQL数据库规约
- **建表规约**：表达是与否概念的字段，必须使用is_xxx的方式命名，数据类型是unsigned tinyint
- **索引规约**：业务上具有唯一特性的字段，即使是多个字段的组合，也必须建成唯一索引
- **SQL语句**：不要使用count(列名)或count(常量)来替代count(*)，count(*)是SQL92定义的标准统计行数的语法

### 项目最佳实践
- **遵循单一职责原则**：每个类和方法只负责一个功能
- **使用异步编程模式**：充分利用Vert.x的异步特性
- **实施统一的错误处理**：全局异常处理和统一响应格式
- **保持代码简洁和可读性**：遵循阿里巴巴代码格式规范
- **编写完整的文档和注释**：按照JavaDoc规范编写注释
- **严格的代码审查**：确保代码质量和规范遵循
- **持续集成和部署**：自动化测试和部署流程

### 开发工具推荐
- **IDE插件**：Alibaba Java Coding Guidelines（阿里巴巴Java开发规约插件）
- **代码检查**：SonarQube进行代码质量检查
- **格式化工具**：使用统一的代码格式化配置
- **静态分析**：SpotBugs、PMD等静态代码分析工具
