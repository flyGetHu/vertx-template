# 依赖注入使用指南

本项目使用Google Guice 7.0.0结合JSR-330标准注解进行依赖注入管理，简化组件间依赖关系。

## 版本信息

- **Guice版本**: 7.0.0
- **JSR-330**: 1.0
- **升级说明**: Guice 7.0.0引入了更严格的依赖注入验证，详见[修复总结](./DEPENDENCY_INJECTION_FIX_SUMMARY.md)

## 核心组件

- [AppModule.java](../di/AppModule.java) - Guice模块配置，只定义基础依赖绑定
- [RouterRegistry.java](../router/RouterRegistry.java) - 创建Injector并注册路由
- [AnnotationRouterHandler.java](../router/handler/AnnotationRouterHandler.java) - 处理基于注解的路由

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

@RequestMapping(value = "/admin", method = HttpMethod.GET)
public void adminMethod() {...}
```

#### HTTP方法注解
简化特定HTTP方法的路由定义：
- `@GetMapping("/path")` - 定义GET请求路由
- `@PostMapping("/path")` - 定义POST请求路由
- 更多方法可扩展

## 使用示例

### 依赖注入示例
- [UserService.java](../service/UserService.java) - 使用@ImplementedBy注解
- [UserServiceImpl.java](../service/impl/UserServiceImpl.java) - 使用@Singleton注解

### 路由定义示例
- [UserController.java](../controller/UserController.java) - 使用Spring Boot风格注解

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
     public NewServiceImpl() {...}
   }
   ```

3. 在需要使用的地方通过构造函数注入：
   ```java
   @Inject
   public MyClass(NewService service) {...}
   ```

## 路由注册 (Spring Boot风格)

创建REST控制器非常简单：

1. 创建控制器类并添加@RestController注解
2. 添加@RequestMapping指定基础路径
3. 使用@GetMapping等注解定义具体路由方法
4. 控制器方法直接返回数据对象，无需包装Future

示例：
```java
@RestController
@RequestMapping("/api/products")
@Singleton
public class ProductController {

  @Inject
  public ProductController(ProductService service) {...}

  @GetMapping("/:id")
  public Product getProduct(RoutingContext ctx) {
    String id = ctx.pathParam("id");
    // 如果服务方法返回Future，可以使用Future.await解包
    return Future.await(productService.getById(id));
  }

  @PostMapping("")
  public Product createProduct(RoutingContext ctx) {
    // 处理POST请求并直接返回数据对象
    Product product = ctx.getBodyAsJson().mapTo(Product.class);
    return Future.await(productService.create(product));
  }
}
```
