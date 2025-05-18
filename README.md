# Vert.x 项目模板

一个基于Vert.x的响应式应用程序模板，提供了完整的项目结构和常用功能实现。

## 项目特性

- 基于注解的路由定义，类似Spring MVC
- 依赖注入支持 (Guice)
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

### 构建与运行

```bash
# 构建项目
mvn clean package

# 运行应用
java -jar target/template-1.0.0-SNAPSHOT-fat.jar

# 或使用Maven直接运行
mvn exec:java
```

## 项目结构

```
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/vertx/template/
│   │   │       ├── config/         # 配置类
│   │   │       ├── controller/     # 控制器
│   │   │       ├── di/            # 依赖注入
│   │   │       ├── exception/      # 异常类
│   │   │       ├── handler/        # 处理器
│   │   │       ├── model/          # 数据模型
│   │   │       │   └── entity/     # 实体类
│   │   │       ├── repository/     # 数据仓库
│   │   │       │   └── impl/       # 仓库实现
│   │   │       ├── router/         # 路由
│   │   │       ├── service/        # 服务层
│   │   │       ├── MainVerticle.java
│   │   │       └── Run.java
│   │   └── resources/
│   │       ├── db/                 # 数据库脚本
│   │       ├── config.yml          # 配置文件
│   │       └── logback.xml         # 日志配置
└── pom.xml
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
