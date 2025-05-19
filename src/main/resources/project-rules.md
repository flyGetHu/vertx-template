# Vert.x 项目编码规范

## 1. 变量声明规则

### 1.1 变量不可变性原则
- 所有变量默认声明为 `final`
- 只有在确实需要修改变量值时才能省略 `final` 关键字
- 集合类型优先使用不可变集合：`List.of()`, `Set.of()`, `Map.of()`

```java
// 正确示例
final String name = "product";
final List<String> items = List.of("item1", "item2");

// 需要修改时例外
Map<String, Object> properties = new HashMap<>();
properties.put("key", "value");
```

### 1.2 变量命名约定
- 局部变量：驼峰式，如 `userId`, `productName`
- 常量：全大写+下划线，如 `MAX_RETRY_COUNT`
- 成员变量：不使用 `m_` 前缀，直接使用驼峰式

## 2. 方法规范

### 2.1 方法设计原则
- 方法应当短小精悍，遵循单一职责原则
- 最大行数控制在30行以内，超过应考虑拆分
- 参数数量不超过3个，多于3个时考虑使用DTO对象

### 2.2 异步方法规范
- 返回 `Future<T>` 而非使用回调
- 使用 `Promise` 创建 `Future`
- 异步方法命名添加动词，如 `fetchData()`, `saveUser()`

```java
// 正确示例
public Future<User> fetchUserById(final String id) {
  final Promise<User> promise = Promise.promise();
  // 实现...
  return promise.future();
}
```

## 3. 异常处理

### 3.1 异常分类
- 使用自定义异常体系：`BusinessException` 作为基类
- 对不同业务场景创建特定异常类

### 3.2 异常处理原则
- 不要捕获后不处理异常（避免空catch块）
- 日志记录异常时包含上下文信息
- 在合适的层次处理异常，通常在服务层或控制器层

## 4. 响应式编程规范

### 4.1 Vert.x风格
- 优先使用 Vert.x 提供的响应式API
- 避免阻塞操作，必要时使用 `vertx.executeBlocking()`
- 当操作可能耗时，使用 `vertx.setTimer()` 或 `vertx.setPeriodic()`

### 4.2 Future 组合
- 使用 `compose()`, `map()`, `flatMap()` 链式调用
- 避免嵌套 Future 回调
- 使用 `CompositeFuture` 组合多个并行 Future

```java
// 正确示例 - 链式调用
userService.findUser(userId)
  .compose(user -> orderService.getOrders(user))
  .map(orders -> processOrders(orders))
  .onSuccess(result -> {
    // 处理成功结果
  })
  .onFailure(err -> {
    // 处理错误
  });
```

## 5. 代码组织

### 5.1 包结构
- 按功能模块分包，而非按技术层次
- 相关功能放在同一包内，提高内聚性

### 5.2 类设计
- 遵循SOLID原则
- 使用依赖注入，避免直接实例化依赖
- 控制类的大小，建议不超过500行

## 6. 并发控制

### 6.1 线程安全
- 默认所有共享变量为不可变对象
- 使用线程安全集合：`ConcurrentHashMap`, `CopyOnWriteArrayList`
- 避免使用同步块，优先使用Vert.x事件循环模型

### 6.2 上下文传递
- 使用Vert.x Context跨异步边界传递数据
- 避免使用ThreadLocal存储上下文信息

## 7. 日志规范

### 7.1 日志级别使用
- ERROR: 影响系统运行的错误
- WARN: 不影响系统但需要关注的异常情况
- INFO: 重要业务事件和状态变化
- DEBUG: 调试信息，生产环境通常不开启
- TRACE: 非常详细的调试信息

### 7.2 日志内容
- 包含关键标识信息如用户ID、请求ID
- 敏感信息脱敏后记录
- 使用结构化日志格式

## 8. 代码提交规范

### 8.1 提交信息格式
- 使用约定式提交（Conventional Commits）规范
- 基本格式：`类型(可选范围): 描述`
- 简化格式：`类型: 描述`

#### 8.1.1 类型说明
- `feat`: 新功能
- `fix`: 修复Bug
- `docs`: 文档变更
- `style`: 代码风格、格式调整
- `refactor`: 代码重构
- `test`: 添加/修改测试
- `chore`: 构建过程或辅助工具变动
- `perf`: 性能优化
- `ci`: CI/CD配置变更
- `build`: 构建系统变更
- `revert`: 回退提交
- `merge`: 分支合并
- `release`: 发布版本
- `optimize`: 功能优化

#### 8.1.2 正确示例
```
feat(user): 添加用户注册功能
fix: 修复登录验证码不显示问题
docs: 更新API文档
chore: 升级依赖版本
```

### 8.2 代码检查
- 提交前自动执行阿里巴巴P3C规约检查
- 推送前自动运行单元测试
- 严禁提交编译不通过的代码

### 8.3 分支管理
- 功能开发使用`feature-名字-功能作用-日期`分支
- 缺陷修复使用`bugfix-名字-功能作用-日期`分支
- 发布版本使用`release-名字-功能作用-日期`分支
