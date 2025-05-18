# 路由模块设计指南

本项目采用基于注解的路由设计，类似Spring Boot的路由定义方式，简化开发并提高代码可读性。

## 核心概念

### 1. 路由注解

使用以下注解定义路由：

- `@RestController` - 标记一个类为REST控制器
- `@RequestMapping` - 定义基础URL路径和HTTP方法
- `@GetMapping`, `@PostMapping` 等 - 定义特定HTTP方法的路由

这些注解位于 `com.vertx.template.router.annotation` 包中。

### 2. 注解路由处理器

[AnnotationRouterHandler](mdc:src/main/java/com/vertx/template/router/handler/AnnotationRouterHandler.java)负责：

- 自动扫描带有@RestController注解的类
- 处理各种路由注解并注册对应的处理器
- 支持方法参数自动注入（如RoutingContext）
- 自动处理返回值，支持直接返回数据对象

### 3. 路由注册中心

[RouterRegistry](mdc:src/main/java/com/vertx/template/router/RouterRegistry.java)负责集中管理路由注册：

- 创建并存储Guice注入器和主路由器
- 注册全局中间件
- 通过AnnotationRouterHandler注册基于注解的路由
- 注册全局异常处理器
- 提供统一的Router实例给HTTP服务器

### 4. 全局中间件

[GlobalMiddleware](mdc:src/main/java/com/vertx/template/router/GlobalMiddleware.java)负责处理通用中间件：

- CORS配置
- 请求体解析
- 请求日志和计时
- 其他全局处理逻辑

### 5. 响应处理器

[ResponseHandler](mdc:src/main/java/com/vertx/template/handler/ResponseHandler.java)负责统一处理响应：

- 自动将返回数据包装成标准ApiResponse格式
- 统一处理异常和错误响应
- 设置响应头和状态码

## 实现示例

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
