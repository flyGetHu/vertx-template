# Vert.x模板项目

这是一个基于Vert.x框架的模板项目，支持单机模式和集群模式运行。

## 特性

- 基于Vert.x 4.5.14构建
- 支持Hazelcast集群管理
- 采用响应式编程模型
- 使用Logback进行日志记录（支持文件日志和日志切割）
- 依赖注入支持（Guice）
- 自动化路由注册
- YAML配置文件支持

## 项目结构

```
src/
├── main/
│   ├── java/
│   │   └── com/vertx/template/
│   │       ├── config/           # 配置类
│   │       ├── controller/       # 控制器
│   │       ├── di/              # 依赖注入
│   │       ├── exception/        # 异常处理
│   │       ├── handler/          # 请求处理器
│   │       ├── model/            # 数据模型
│   │       ├── repository/       # 数据访问层
│   │       ├── router/           # 路由相关
│   │       ├── service/          # 业务逻辑层
│   │       ├── MainVerticle.java # 主Verticle
│   │       └── Run.java          # 应用入口
│   └── resources/
│       ├── cluster.xml          # Hazelcast集群配置
│       ├── config.yml           # 应用配置
│       └── logback.xml          # 日志配置
└── test/                        # 测试代码
```

## 构建和运行

### 前提条件

- JDK 21
- Maven 3.8+

### 构建项目

```bash
mvn clean package
```

### 运行方式

#### 单机模式

```bash
# 使用脚本启动
./start-standalone.sh

# 或者手动启动
java -jar target/template-1.0.0-SNAPSHOT-fat.jar
```

#### 集群模式

```bash
# 使用脚本启动
./start-cluster.sh

# 或者手动启动
java -Dcluster=true -jar target/template-1.0.0-SNAPSHOT-fat.jar
```

## 集群配置

项目使用Hazelcast作为集群管理器，配置文件位于`src/main/resources/cluster.xml`。

集群配置在`config.yml`中：

```yaml
cluster:
  enabled: true  # 是否启用集群模式
  type: hazelcast # 集群类型
  config_file: cluster.xml # 集群配置文件
```

## 日志配置

项目使用Logback作为日志框架，配置文件位于`src/main/resources/logback.xml`。日志文件保存在`logs`目录中，支持以下特性：

- 控制台和文件双重输出
- 基于大小和时间的日志切割
- 自定义日志级别
- 日志文件总大小限制

## 注意事项

- 多播可能在某些环境中不可用，如果遇到集群问题，请修改Hazelcast配置使用TCP-IP或其他发现方式
- 在生产环境中使用前，请适当调整JVM参数和日志配置

## 项目特性

- 基于注解的路由定义，类似Spring MVC
- 参数校验 (Jakarta Bean Validation)
- 统一异常处理
- 响应式数据库访问 (MySQL)
- Repository模式数据访问层

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+

### 项目初始化流程

#### 1. 克隆项目
```bash
git clone <repository-url>
cd vertx-template
```

#### 2. 数据库准备

创建MySQL数据库：
```sql
CREATE DATABASE vertx_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

导入初始化脚本（如果存在）：
```bash
mysql -u root -p vertx_demo < src/main/resources/db/init.sql
```

#### 3. 配置项目

编辑 `src/main/resources/config.yml` 文件，修改数据库连接信息：

```yaml
# 服务器配置
server:
  port: 8888
  host: localhost

# 数据库配置
database:
  mysql:
    host: localhost
    port: 3306
    database: vertx_demo
    username: your_username
    password: your_password
    max_pool_size: 5

# CORS配置
cors:
  enabled: true
  allowed_origins: "*"
  allowed_methods:
    - GET
    - POST
    - PUT
    - DELETE

# JWT配置（如果使用认证）
jwt:
  secret: your_jwt_secret
  expiration: 86400
```

#### 4. 构建项目
```bash
mvn clean package
```

#### 5. 启动应用

**单机模式：**
```bash
# 使用脚本
./start-standalone.sh

# 或直接运行
java -jar target/template-1.0.0-SNAPSHOT-fat.jar
```

**集群模式：**
```bash
# 使用脚本
./start-cluster.sh

# 或直接运行
java -Dcluster=true -jar target/template-1.0.0-SNAPSHOT-fat.jar
```

#### 6. 验证启动

访问健康检查接口：
```bash
curl http://localhost:8888/api/users/public/info
```

预期响应：
```json
{
  "version": "1.0.0",
  "name": "Vert.x Template",
  "timestamp": 1703123456789
}
```

## 数据库访问示例

### 实体类定义

```java
@Data
public class User {
  private Long id;
  private String username;
  private String password;
  private String email;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private boolean active;
}
```

### Repository接口

```java
public interface UserRepository extends BaseRepository<User, Long> {
  Future<User> findByUsername(String username);
  Future<List<User>> findActiveUsers();
}
```

### 在控制器中使用

```java
@RestController
@RequestMapping("/api/users")
@Singleton
public class UserController {
  private final UserRepository userRepository;

  @Inject
  public UserController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @GetMapping("/:id")
  public Future<User> getUserById(@PathParam("id") Long id) {
    return userRepository.findById(id);
  }
}
```

## API请求处理流程

### 整体架构流程

```
客户端请求 → HTTP服务器 → 路由匹配 → 中间件处理 → 控制器 → 服务层 → 数据访问层 → 响应返回
```

### 详细处理流程

#### 1. 应用启动流程

```
Run.main()
├── 创建VertxOptions（启用虚拟线程）
├── 创建Vertx实例（单机/集群模式）
├── 部署MainVerticle
│   ├── 配置Jackson时间类型支持
│   ├── 加载config.yml配置文件
│   └── 启动HTTP服务器
│       ├── 创建RouterRegistry
│       │   ├── 初始化Guice依赖注入器
│       │   ├── 注册全局中间件（CORS、BodyHandler、日志）
│       │   ├── 扫描并注册控制器路由
│       │   └── 注册全局异常处理器
│       └── 绑定路由到HTTP服务器
│           ├── 创建Router实例
│           └── 绑定路由到HTTP服务器
└── 应用启动完成
```

#### 2. 依赖注入初始化

```
Guice.createInjector(new AppModule())
├── 绑定接口到实现类
│   ├── UserRepository → UserRepositoryImpl
│   └── UserService → UserServiceImpl
├── 提供单例实例
│   ├── Vertx实例
│   ├── Router实例
│   └── JsonObject配置
└── 自动注入到控制器和服务
```

#### 3. 路由注册流程

```
AnnotationRouterHandler.scanAndRegister()
├── 扫描com.vertx.template包下的所有类
├── 查找@RestController注解的控制器
├── 解析@RequestMapping类级别路径
├── 遍历控制器方法
│   ├── 解析HTTP方法注解（@GetMapping、@PostMapping等）
│   ├── 构建完整路径（类路径 + 方法路径）
│   ├── 解析方法参数（@PathParam、@QueryParam等）
│   ├── 检查认证要求（@RequireAuth）
│   └── 注册路由处理器
└── 路由注册完成
```

#### 4. API请求处理流程

以 `GET /api/users/123` 为例：

```
1. HTTP请求到达
   ↓
2. 全局中间件处理
   ├── CORS处理（跨域检查）
   ├── BodyHandler（解析请求体）
   └── 请求日志记录
   ↓
3. 路由匹配
   ├── 匹配路径模式：/api/users/:id
   ├── 提取路径参数：id=123
   └── 找到对应的控制器方法
   ↓
4. 认证检查（如果需要）
   ├── 检查@RequireAuth注解
   ├── 验证JWT Token
   └── 设置用户上下文
   ↓
5. 参数注入和验证
   ├── 注入@PathParam参数：id=123
   ├── 注入@QueryParam参数
   ├── 注入@CurrentUser用户信息
   └── 执行参数验证（@Valid）
   ↓
6. 控制器方法执行
   ├── UserController.getUserById(123)
   └── 调用业务服务层
   ↓
7. 服务层处理
   ├── UserService.getUserById(123)
   └── 调用数据访问层
   ↓
8. 数据访问层
   ├── UserRepository.findById(123)
   ├── 执行数据库查询
   └── 返回User实体
   ↓
9. 响应处理
   ├── 业务逻辑处理结果
   ├── 数据转换（Entity → DTO）
   ├── 包装为ApiResponse格式
   └── JSON序列化
   ↓
10. HTTP响应返回
    ├── 设置响应头
    ├── 设置状态码
    └── 返回JSON响应体
```

#### 5. 异常处理流程

```
异常发生
├── 业务异常（BusinessException）
│   ├── 记录WARN级别日志
│   └── 返回400状态码 + 错误信息
├── 验证异常（ValidationException）
│   ├── 记录WARN级别日志
│   └── 返回400状态码 + 验证错误详情
├── 认证异常（AuthenticationException）
│   ├── 记录WARN级别日志
│   └── 返回401状态码 + 认证失败信息
└── 系统异常（其他Exception）
    ├── 记录ERROR级别日志
    └── 返回500状态码 + 通用错误信息
```

### 关键组件说明

| 组件                        | 职责                                 | 位置                                |
| --------------------------- | ------------------------------------ | ----------------------------------- |
| **Run**                     | 应用程序入口，配置Vertx选项          | `com.vertx.template.Run`            |
| **MainVerticle**            | 主Verticle，负责启动HTTP服务器       | `com.vertx.template.verticle`       |
| **RouterRegistry**          | 路由注册中心，统一管理所有路由       | `com.vertx.template.router`         |
| **AppModule**               | Guice依赖注入模块配置                | `com.vertx.template.di`             |
| **AnnotationRouterHandler** | 注解路由处理器，扫描并注册控制器路由 | `com.vertx.template.router.handler` |
| **GlobalMiddleware**        | 全局中间件，处理CORS、日志等         | `com.vertx.template.router`         |
| **GlobalExceptionHandler**  | 全局异常处理器                       | `com.vertx.template.handler`        |
| **Controller**              | 控制器层，处理HTTP请求               | `com.vertx.template.controller`     |
| **Service**                 | 服务层，业务逻辑处理                 | `com.vertx.template.service`        |
| **Repository**              | 数据访问层，数据库操作               | `com.vertx.template.repository`     |

## API接口文档

### 用户管理接口

| 路径                     | 方法   | 说明         | 认证要求 |
| ------------------------ | ------ | ------------ | -------- |
| `/api/users/public/info` | GET    | 获取系统信息 | 无       |
| `/api/users`             | GET    | 获取所有用户 | JWT      |
| `/api/users/:id`         | GET    | 获取指定用户 | JWT      |
| `/api/users`             | POST   | 创建用户     | JWT      |
| `/api/users/:id`         | PUT    | 更新用户     | JWT      |
| `/api/users/:id`         | DELETE | 删除用户     | JWT      |

### 请求示例

#### 获取系统信息（无需认证）
```bash
curl -X GET http://localhost:8888/api/users/public/info
```

#### 获取用户列表（需要JWT认证）
```bash
curl -X GET http://localhost:8888/api/users \
  -H "Authorization: Bearer your_jwt_token"
```

#### 创建用户
```bash
curl -X POST http://localhost:8888/api/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_jwt_token" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com",
    "password": "password123"
  }'
```

### 响应格式

所有API响应都遵循统一格式：

```json
{
  "success": true,
  "data": {},
  "message": "操作成功",
  "timestamp": 1703123456789
}
```

错误响应格式：
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "参数验证失败",
    "details": []
  },
  "timestamp": 1703123456789
}
```

## 开发指南

### 添加新的API接口

1. **创建实体类**（如果需要）
```java
// src/main/java/com/vertx/template/model/entity/Product.java
public class Product {
  private Long id;
  private String name;
  private BigDecimal price;
  // getters and setters
}
```

2. **创建Repository接口和实现**
```java
// src/main/java/com/vertx/template/repository/ProductRepository.java
public interface ProductRepository extends BaseRepository<Product, Long> {
  Future<List<Product>> findByCategory(String category);
}
```

3. **创建Service接口和实现**
```java
// src/main/java/com/vertx/template/service/ProductService.java
public interface ProductService {
  Future<List<Product>> getProducts();
  Future<Product> createProduct(Product product);
}
```

4. **创建Controller**
```java
// src/main/java/com/vertx/template/controller/ProductController.java
@RestController
@RequestMapping("/api/products")
@Singleton
public class ProductController {

  @Inject
  private ProductService productService;

  @GetMapping("")
  public Future<List<Product>> getProducts() {
    return productService.getProducts();
  }

  @PostMapping("")
  @RequireAuth(AuthType.JWT)
  public Future<Product> createProduct(@RequestBody @Valid Product product) {
    return productService.createProduct(product);
  }
}
```

5. **在AppModule中注册依赖**
```java
@Override
protected void configure() {
  bind(ProductRepository.class).to(ProductRepositoryImpl.class);
  bind(ProductService.class).to(ProductServiceImpl.class);
}
```

### 注解说明

| 注解              | 说明                 | 示例                               |
| ----------------- | -------------------- | ---------------------------------- |
| `@RestController` | 标记为REST控制器     | `@RestController`                  |
| `@RequestMapping` | 定义类级别的路径前缀 | `@RequestMapping("/api/users")`    |
| `@GetMapping`     | 处理GET请求          | `@GetMapping("/:id")`              |
| `@PostMapping`    | 处理POST请求         | `@PostMapping("")`                 |
| `@PutMapping`     | 处理PUT请求          | `@PutMapping("/:id")`              |
| `@DeleteMapping`  | 处理DELETE请求       | `@DeleteMapping("/:id")`           |
| `@PathParam`      | 注入路径参数         | `@PathParam("id") Long id`         |
| `@QueryParam`     | 注入查询参数         | `@QueryParam("page") Integer page` |
| `@RequestBody`    | 注入请求体           | `@RequestBody User user`           |
| `@RequireAuth`    | 指定认证要求         | `@RequireAuth(AuthType.JWT)`       |
| `@CurrentUser`    | 注入当前用户信息     | `@CurrentUser UserContext user`    |
| `@Valid`          | 启用参数验证         | `@Valid @RequestBody User user`    |
| `@Singleton`      | 标记为单例（Guice）  | `@Singleton`                       |
| `@Inject`         | 依赖注入（Guice）    | `@Inject`                          |

### 配置说明

项目配置文件位于 `src/main/resources/config.yml`，支持以下配置项：

- **server**: HTTP服务器配置（端口、主机）
- **database**: 数据库连接配置
- **cors**: 跨域资源共享配置
- **jwt**: JWT认证配置
- **logging**: 日志配置
- **cluster**: 集群模式配置

### 日志使用

项目使用SLF4J + Logback进行日志记录：

```java
private static final Logger logger = LoggerFactory.getLogger(YourClass.class);

// 不同级别的日志
logger.debug("调试信息");
logger.info("一般信息");
logger.warn("警告信息");
logger.error("错误信息", exception);
```

## 性能优化

### 虚拟线程支持

项目基于JDK 21的虚拟线程特性，提供高并发处理能力：

- 使用 `ThreadingModel.VIRTUAL_THREAD` 部署Verticle
- 支持大量并发连接而不消耗过多系统资源
- 适合I/O密集型应用场景

### 集群模式

支持Hazelcast集群管理，实现水平扩展：

- 自动服务发现
- 负载均衡
- 故障转移
- 分布式缓存

## 故障排查

### 常见问题

1. **启动失败**
   - 检查JDK版本是否为21+
   - 检查端口是否被占用
   - 检查数据库连接配置

2. **路由不生效**
   - 确认控制器类有 `@RestController` 注解
   - 确认方法有对应的HTTP方法注解
   - 检查包扫描路径是否正确

3. **依赖注入失败**
   - 确认类有 `@Singleton` 注解
   - 确认在 `AppModule` 中正确绑定接口和实现
   - 检查构造函数是否有 `@Inject` 注解

4. **认证失败**
   - 检查JWT配置是否正确
   - 确认请求头包含正确的Authorization
   - 检查Token是否过期

### 日志查看

日志文件位于 `logs/` 目录：
- `vertx-app.log`: 应用日志
- `vertx-app.log.yyyy-MM-dd.gz`: 历史日志（按日期归档）

## 贡献

欢迎提交问题和改进建议！

## 许可

MIT License

## MQ连接管理

### RabbitMQ连接重试策略

本项目采用智能的指数退避重试策略，确保在网络不稳定或服务临时不可用时能够高效重连：

#### 重试配置参数

| 参数                         | 值   | 说明         |
| ---------------------------- | ---- | ------------ |
| `INITIAL_RECONNECT_INTERVAL` | 1秒  | 初始重连间隔 |
| `MAX_RECONNECT_INTERVAL`     | 60秒 | 最大重连间隔 |
| `BACKOFF_MULTIPLIER`         | 1.5  | 退避倍数     |
| `MAX_RETRY_ATTEMPTS`         | 15次 | 最大重试次数 |
| `HEALTH_CHECK_INTERVAL`      | 30秒 | 健康检查间隔 |

#### 重试间隔计算示例

使用指数退避算法计算重连间隔：

```
间隔 = min(初始间隔 * (退避倍数 ^ 重试次数), 最大间隔) * 抖动因子
```

重试序列示例（基础计算，不含抖动）：
- 第1次：1秒
- 第2次：1.5秒
- 第3次：2.25秒
- 第4次：3.38秒
- 第5次：5.06秒
- 第10次：38.44秒
- 第15次：60秒（达到上限）

#### 抖动机制

为避免多个实例同时重连造成的雷群效应，每次重连间隔会添加±20%的随机抖动：

```java
抖动因子 = 0.8 + (random() * 0.4)  // 范围：0.8-1.2
最终间隔 = 基础间隔 * 抖动因子
```

#### 重置机制

当连续重试达到最大次数（15次）后：
- 等待2分钟（120秒）
- 重置重试计数器为0
- 继续下一轮重试循环

这种策略在保证服务可用性的同时，避免了对RabbitMQ服务器的过度冲击。
