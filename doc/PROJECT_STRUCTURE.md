# Vert.x Template 项目结构文档

本文档详细描述了 Vert.x Template 项目的实际代码结构和组织方式。

## 📋 目录

- [项目概述](#项目概述)
- [目录结构](#目录结构)
- [核心模块详解](#核心模块详解)
- [分层架构](#分层架构)
- [命名规范](#命名规范)

## 🎯 项目概述

Vert.x Template 是一个基于 Vert.x 4.x 和 JDK21 虚拟线程的企业级 Web 应用模板，采用标准的分层架构设计，提供完整的 Web 服务开发基础设施。

### 技术栈

- **核心框架**: Vert.x 4.x
- **JDK版本**: JDK 21 (支持虚拟线程)
- **依赖注入**: Google Guice
- **JSON处理**: Jackson
- **参数验证**: Jakarta Validation
- **日志框架**: SLF4J + Logback

## 🏗️ 目录结构

### 完整项目结构

```
vertx-template/
├── src/main/java/com/vertx/template/
│   ├── Run.java                           # 应用程序入口
│   ├── config/                            # 配置模块
│   │   ├── ConfigLoader.java              # 配置加载器
│   │   ├── DatabaseConfig.java            # 数据库配置
│   │   ├── JacksonConfig.java             # Jackson配置
│   │   └── RouterConfig.java              # 路由配置
│   ├── constants/                         # 常量定义
│   │   ├── HttpConstants.java             # HTTP常量
│   │   └── RouterConstants.java           # 路由常量
│   ├── controller/                        # 控制器层 (Web层)
│   │   ├── AuthController.java            # 认证控制器
│   │   ├── ProductController.java         # 产品控制器
│   │   ├── PublicController.java          # 公共控制器
│   │   ├── RateLimitDemoController.java   # 限流演示控制器
│   │   ├── TestController.java            # 测试控制器
│   │   └── UserController.java            # 用户控制器
│   ├── di/                                # 依赖注入模块
│   │   └── AppModule.java                 # Guice应用模块
│   ├── examples/                          # 示例代码
│   │   └── CodeStyleExample.java         # 代码风格示例
│   ├── exception/                         # 异常定义
│   │   ├── BusinessException.java         # 业务异常
│   │   ├── RateLimitException.java        # 限流异常
│   │   ├── RouteRegistrationException.java # 路由注册异常
│   │   └── ValidationException.java       # 验证异常
│   ├── middleware/                        # 中间件模块
│   │   ├── GlobalMiddleware.java          # 全局中间件
│   │   ├── auth/                          # 认证中间件
│   │   │   ├── AuthenticationException.java
│   │   │   ├── AuthenticationManager.java
│   │   │   ├── Authenticator.java
│   │   │   ├── annotation/                # 认证注解
│   │   │   ├── authenticator/             # 认证器实现
│   │   │   └── impl/                      # 认证实现
│   │   ├── core/                          # 核心中间件
│   │   │   ├── Middleware.java            # 中间件接口
│   │   │   ├── MiddlewareChain.java       # 中间件链
│   │   │   └── impl/                      # 核心实现
│   │   ├── exception/                     # 异常处理中间件
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── ratelimit/                     # 限流中间件
│   │   │   ├── annotation/                # 限流注解
│   │   │   ├── core/                      # 限流核心
│   │   │   ├── impl/                      # 限流实现
│   │   │   └── interceptor/               # 限流拦截器
│   │   ├── response/                      # 响应处理中间件
│   │   │   └── ResponseHandler.java
│   │   └── validation/                    # 验证中间件
│   │       ├── ValidationUtils.java
│   │       └── ValidatorFactory.java
│   ├── model/                             # 数据模型层
│   │   ├── annotation/                    # 模型注解
│   │   │   ├── Column.java                # 列注解
│   │   │   ├── Id.java                    # ID注解
│   │   │   └── Table.java                 # 表注解
│   │   ├── context/                       # 上下文对象
│   │   │   └── UserContext.java          # 用户上下文
│   │   ├── dto/                           # 数据传输对象
│   │   │   ├── ApiResponse.java           # API响应对象
│   │   │   ├── ProductDto.java            # 产品DTO
│   │   │   └── UserDto.java               # 用户DTO
│   │   └── entity/                        # 数据库实体
│   │       ├── BaseEntity.java           # 基础实体
│   │       ├── Product.java               # 产品实体
│   │       └── User.java                  # 用户实体
│   ├── repository/                        # 数据访问层 (DAO层)
│   │   ├── UserRepository.java            # 用户仓储接口
│   │   ├── common/                        # 通用仓储
│   │   │   ├── AbstractBaseRepository.java # 抽象基础仓储
│   │   │   └── BaseRepository.java        # 基础仓储接口
│   │   └── impl/                          # 仓储实现
│   │       └── UserRepositoryImpl.java   # 用户仓储实现
│   ├── router/                            # 路由系统
│   │   ├── RouterRegistry.java            # 路由注册器
│   │   ├── annotation/                    # 路由注解
│   │   │   ├── DeleteMapping.java         # DELETE映射
│   │   │   ├── GetMapping.java            # GET映射
│   │   │   ├── HeaderParam.java           # 请求头参数
│   │   │   ├── HttpMethod.java            # HTTP方法
│   │   │   ├── PathParam.java             # 路径参数
│   │   │   ├── PostMapping.java           # POST映射
│   │   │   ├── PutMapping.java            # PUT映射
│   │   │   ├── QueryParam.java            # 查询参数
│   │   │   ├── RequestBody.java           # 请求体
│   │   │   ├── RequestMapping.java        # 请求映射
│   │   │   └── RestController.java        # REST控制器
│   │   ├── cache/                         # 路由缓存
│   │   │   ├── MethodMetadata.java        # 方法元数据
│   │   │   └── ReflectionCache.java       # 反射缓存
│   │   ├── executor/                      # 请求执行器
│   │   │   └── RequestExecutor.java       # 请求执行器
│   │   ├── handler/                       # 路由处理器
│   │   │   └── AnnotationRouterHandler.java # 注解路由处理器
│   │   ├── resolver/                      # 参数解析器
│   │   │   └── ParameterResolver.java     # 参数解析器
│   │   └── scanner/                       # 路由扫描器
│   │       └── RouteScanner.java          # 路由扫描器
│   ├── service/                           # 服务层 (业务逻辑层)
│   │   ├── UserService.java               # 用户服务接口
│   │   └── impl/                          # 服务实现
│   │       └── UserServiceImpl.java      # 用户服务实现
│   ├── utils/                             # 工具类
│   │   ├── FlexIDGenerator.java           # 灵活ID生成器
│   │   └── JwtUtils.java                  # JWT工具类
│   └── verticle/                          # Verticle模块
│       └── MainVerticle.java              # 主Verticle
├── src/main/resources/                    # 资源文件
├── src/test/java/                         # 测试代码
└── doc/                                   # 项目文档
```

## 🏛️ 核心模块详解

### 1. 路由系统 (Router Module)

**位置**: `src/main/java/com/vertx/template/router/`

路由系统采用注解驱动的设计，支持 Spring Boot 风格的路由映射。

#### 核心组件

| 组件                      | 职责                           | 文件位置                                |
| ------------------------- | ------------------------------ | --------------------------------------- |
| **AnnotationRouterHandler** | 路由处理协调器                 | `router/handler/AnnotationRouterHandler.java` |
| **RouteScanner**          | 控制器扫描和路由发现           | `router/scanner/RouteScanner.java`     |
| **ParameterResolver**     | HTTP请求参数解析               | `router/resolver/ParameterResolver.java` |
| **RequestExecutor**       | 方法调用执行器                 | `router/executor/RequestExecutor.java` |
| **ReflectionCache**       | 反射操作缓存                   | `router/cache/ReflectionCache.java`    |

#### 路由注解

| 注解              | 用途                 | 示例                          |
| ----------------- | -------------------- | ----------------------------- |
| `@RestController` | 标记REST控制器       | `@RestController`             |
| `@RequestMapping` | 基础路由映射         | `@RequestMapping("/api/v1")` |
| `@GetMapping`     | GET请求映射          | `@GetMapping("/users")`      |
| `@PostMapping`    | POST请求映射         | `@PostMapping("/users")`     |
| `@PutMapping`     | PUT请求映射          | `@PutMapping("/users/:id")`  |
| `@DeleteMapping`  | DELETE请求映射       | `@DeleteMapping("/users/:id")` |
| `@PathParam`      | 路径参数注入         | `@PathParam("id")`            |
| `@QueryParam`     | 查询参数注入         | `@QueryParam("name")`         |
| `@RequestBody`    | 请求体参数注入       | `@RequestBody`                |
| `@HeaderParam`    | 请求头参数注入       | `@HeaderParam("Authorization")` |

### 2. 中间件系统 (Middleware Module)

**位置**: `src/main/java/com/vertx/template/middleware/`

中间件系统提供可插拔的请求处理管道，支持认证、限流、验证等功能。

#### 核心中间件

| 中间件类型     | 位置                          | 功能描述                     |
| -------------- | ----------------------------- | ---------------------------- |
| **认证中间件** | `middleware/auth/`            | JWT、Session等认证方式       |
| **限流中间件** | `middleware/ratelimit/`       | 基于令牌桶的限流控制         |
| **响应中间件** | `middleware/response/`        | 统一响应格式处理             |
| **验证中间件** | `middleware/validation/`      | 请求参数验证                 |
| **异常中间件** | `middleware/exception/`       | 全局异常处理                 |

### 3. 数据模型层 (Model Module)

**位置**: `src/main/java/com/vertx/template/model/`

数据模型层遵循阿里巴巴分层架构规范，明确区分不同类型的数据对象。

#### 模型分类

| 模型类型   | 位置            | 命名规则    | 用途                         | 示例                    |
| ---------- | --------------- | ----------- | ---------------------------- | ----------------------- |
| **Entity** | `model/entity/` | `Xxx.java`  | 数据库实体对象               | `User.java`             |
| **DTO**    | `model/dto/`    | `XxxDto.java` | 数据传输对象，用于层间传递   | `UserDto.java`          |
| **VO**     | `model/vo/`     | `XxxVo.java`  | 视图对象，用于前端展示       | `UserVo.java`           |
| **BO**     | `model/bo/`     | `XxxBo.java`  | 业务对象，封装业务逻辑       | `UserBo.java`           |
| **Context** | `model/context/` | `XxxContext.java` | 上下文对象，传递请求上下文 | `UserContext.java`      |

### 4. 服务层 (Service Module)

**位置**: `src/main/java/com/vertx/template/service/`

服务层负责业务逻辑处理，采用接口与实现分离的设计。

#### 设计模式

```java
// 服务接口定义
@ImplementedBy(UserServiceImpl.class)
public interface UserService {
    Future<List<UserDto>> getUsers();
    Future<UserDto> getUserById(String id);
}

// 服务实现类
@Singleton
public class UserServiceImpl implements UserService {
    
    @Inject
    public UserServiceImpl() {
        // 依赖注入构造函数
    }
    
    @Override
    public Future<UserDto> getUserById(String id) {
        return Future.succeededFuture(new UserDto(id, "User-" + id));
    }
}
```

### 5. 数据访问层 (Repository Module)

**位置**: `src/main/java/com/vertx/template/repository/`

数据访问层提供数据持久化操作，采用仓储模式设计。

#### 仓储层次结构

```
BaseRepository (基础仓储接口)
└── AbstractBaseRepository (抽象基础仓储)
    └── UserRepositoryImpl (具体仓储实现)
```

#### 仓储设计模式

```java
// 基础仓储接口
public interface BaseRepository<T, ID> {
    Future<T> findById(ID id);
    Future<List<T>> findAll();
    Future<T> save(T entity);
    Future<Void> deleteById(ID id);
}

// 抽象基础仓储
public abstract class AbstractBaseRepository<T, ID> implements BaseRepository<T, ID> {
    // 基础CRUD操作的默认实现
}

// 具体仓储实现
public class UserRepositoryImpl extends AbstractBaseRepository<User, String> implements UserRepository {
    // 用户特定的数据访问逻辑
}
```

## 🏗️ 分层架构

### 阿里巴巴分层架构规范

| 层级           | 项目对应模块    | 职责                               | 示例文件                     |
| -------------- | --------------- | ---------------------------------- | ---------------------------- |
| **Web层**      | `controller/`   | 接收HTTP请求，参数验证，调用服务层 | `UserController.java`        |
| **Service层**  | `service/`      | 业务逻辑处理，数据转换             | `UserService.java`           |
| **DAO层**      | `repository/`   | 数据访问，外部API调用              | `UserRepository.java`        |
| **领域模型**   | `model/`        | 数据对象定义                       | `User.java`, `UserDto.java`  |

### 数据流向

```
HTTP请求 → Controller → Service → Repository → 数据库
         ↓           ↓         ↓            ↓
      参数验证    业务逻辑   数据访问    数据持久化
         ↓           ↓         ↓            ↓
      DTO转换    BO处理     Entity操作   SQL执行
```

## 📝 命名规范

### 包命名规范

| 包类型         | 命名规则                | 示例                              |
| -------------- | ----------------------- | --------------------------------- |
| **基础包**     | `com.{公司}.{项目}`     | `com.vertx.template`              |
| **控制器包**   | `{基础包}.controller`   | `com.vertx.template.controller`   |
| **服务包**     | `{基础包}.service`      | `com.vertx.template.service`      |
| **服务实现包** | `{基础包}.service.impl` | `com.vertx.template.service.impl` |
| **数据访问包** | `{基础包}.repository`   | `com.vertx.template.repository`   |
| **实体包**     | `{基础包}.model.entity` | `com.vertx.template.model.entity` |
| **DTO包**      | `{基础包}.model.dto`    | `com.vertx.template.model.dto`    |

### 类命名规范

| 类型       | 命名规则               | 示例                                         |
| ---------- | ---------------------- | -------------------------------------------- |
| **控制器** | `XxxController`        | `UserController`, `ProductController`        |
| **服务接口** | `XxxService`         | `UserService`, `ProductService`              |
| **服务实现** | `XxxServiceImpl`     | `UserServiceImpl`, `ProductServiceImpl`      |
| **仓储接口** | `XxxRepository`      | `UserRepository`, `ProductRepository`        |
| **仓储实现** | `XxxRepositoryImpl`  | `UserRepositoryImpl`, `ProductRepositoryImpl` |
| **实体类** | `Xxx`                  | `User`, `Product`                            |
| **DTO类**  | `XxxDto`               | `UserDto`, `ProductDto`                      |
| **异常类** | `XxxException`         | `BusinessException`, `ValidationException`   |
| **工具类** | `XxxUtils`             | `JwtUtils`, `ValidationUtils`                |
| **常量类** | `XxxConstants`         | `HttpConstants`, `RouterConstants`           |

### 方法命名规范

| 操作类型   | 命名规则           | 示例                                    |
| ---------- | ------------------ | --------------------------------------- |
| **查询**   | `get/find/query`   | `getUserById`, `findUserByName`         |
| **创建**   | `create/add/save`  | `createUser`, `addProduct`, `saveOrder` |
| **更新**   | `update/modify`    | `updateUser`, `modifyProduct`           |
| **删除**   | `delete/remove`    | `deleteUser`, `removeProduct`           |
| **验证**   | `validate/check`   | `validateUser`, `checkPermission`       |
| **转换**   | `convert/transform` | `convertToDto`, `transformEntity`       |

## 🔧 配置管理

### 配置模块结构

**位置**: `src/main/java/com/vertx/template/config/`

| 配置类            | 职责                 | 配置内容                     |
| ----------------- | -------------------- | ---------------------------- |
| `ConfigLoader`    | 配置文件加载         | 环境配置、应用配置           |
| `DatabaseConfig`  | 数据库配置           | 连接池、数据源配置           |
| `JacksonConfig`   | JSON序列化配置       | 日期格式、字段命名策略       |
| `RouterConfig`    | 路由配置             | 路由规则、中间件配置         |

### 依赖注入配置

**位置**: `src/main/java/com/vertx/template/di/AppModule.java`

```java
public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        // 绑定服务接口与实现
        bind(UserService.class).to(UserServiceImpl.class);
        
        // 配置单例
        bind(ConfigLoader.class).in(Singleton.class);
    }
    
    @Provides
    @Singleton
    public DatabaseConfig provideDatabaseConfig() {
        return new DatabaseConfig();
    }
}
```

## 📊 项目统计

### 模块统计

| 模块           | 文件数量 | 主要功能                     |
| -------------- | -------- | ---------------------------- |
| **Router**     | 12       | 路由处理、参数解析、请求执行 |
| **Middleware** | 15+      | 认证、限流、验证、异常处理   |
| **Controller** | 6        | HTTP请求处理                 |
| **Service**    | 2        | 业务逻辑处理                 |
| **Repository** | 3        | 数据访问                     |
| **Model**      | 8        | 数据模型定义                 |
| **Config**     | 4        | 配置管理                     |
| **Utils**      | 2        | 工具类                       |

### 代码质量指标

| 指标             | 目标值     | 当前状态 |
| ---------------- | ---------- | -------- |
| **文件行数限制** | ≤ 800行    | ✅ 符合   |
| **方法行数限制** | ≤ 30行     | ✅ 符合   |
| **参数数量限制** | ≤ 3个      | ✅ 符合   |
| **包层级深度**   | ≤ 3级      | ✅ 符合   |
| **命名规范**     | 驼峰命名   | ✅ 符合   |

---

**📝 维护说明**: 本文档与代码结构保持同步更新，最后更新时间：2024年12月
**🔗 相关文档**: [架构总览](ARCHITECTURE_OVERVIEW.md) | [开发规范](../README.md) | [API文档](ANNOTATION_USAGE.md)