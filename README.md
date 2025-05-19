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

### 数据库准备

1. 创建MySQL数据库
```sql
CREATE DATABASE vertx_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 导入初始化脚本
```bash
mysql -u root -p vertx_demo < src/main/resources/db/init.sql
```

### 配置项目

1. 编辑 `src/main/resources/config.yml` 文件，修改数据库连接信息

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

## API接口文档

| 路径             | 方法   | 说明         |
| ---------------- | ------ | ------------ |
| `/api/users`     | GET    | 获取所有用户 |
| `/api/users/:id` | GET    | 获取指定用户 |
| `/api/users`     | POST   | 创建用户     |
| `/api/users/:id` | PUT    | 更新用户     |
| `/api/users/:id` | DELETE | 删除用户     |

## 贡献

欢迎提交问题和改进建议！

## 许可

MIT License
