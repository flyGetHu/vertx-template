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

[UserController](mdc:src/main/java/com/vertx/template/controller/UserController.java)展示了基于注解路由的标准实现：

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
    public User getUserById(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        // 验证和处理...
        return Future.await(userService.getUserById(id));
    }
}
```

### 高级参数解析和校验示例

[ProductController](mdc:src/main/java/com/vertx/template/controller/ProductController.java)展示了参数解析和校验功能：

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

    @GetMapping("/:id")
    public Product getProductById(@PathParam("id") String id) {
        // 路径参数注解，自动解析
        // ...
    }

    @PostMapping("")
    public Product createProduct(@Valid @RequestBody Product product) {
        // 请求体对象并进行校验
        // ...
    }
}
```

## 添加新路由

添加新路由的步骤：

1. 创建控制器类并添加`@RestController`注解
2. 添加`@RequestMapping`注解指定基础路径
3. 使用`@GetMapping`、`@PostMapping`等注解定义具体路由方法
4. 控制器方法直接返回数据对象，系统会自动包装成标准响应格式
5. 使用参数注解和校验注解定义和校验方法参数
6. 添加`@Singleton`注解确保单例模式
7. 使用`@Inject`注入所需依赖

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
