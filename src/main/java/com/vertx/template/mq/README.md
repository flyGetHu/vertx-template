# SimpleMQManager 统一MQ管理系统

## 📋 系统概述

SimpleMQManager 是一个统一的消息队列管理器，将消息的**生产**和**消费**功能合并在一个类中，实现了：
- 🔄 **统一入口**：一个Manager处理所有MQ操作
- 🎯 **注解驱动**：消费者通过@RabbitConsumer自动管理
- 📤 **简单发送**：通过API方法直接发送消息
- 📊 **集成监控**：内置消费者性能监控

## 🏗️ 核心组件

| 组件                     | 职责                        | 文件大小 |
| ------------------------ | --------------------------- | -------- |
| **SimpleMQManager**      | 统一的MQ管理器（生产+消费） | 380行    |
| **MessageConsumer**      | 消费者接口定义              | 46行     |
| **RabbitConsumer**       | 消费者注解配置              | 56行     |
| **BasicConsumerMonitor** | 基础监控统计                | 223行    |
| **ExampleConsumer**      | 消费者使用示例              | 62行     |
| **SimpleMQExample**      | 完整使用示例（生产+消费）   | 120行    |

## 🚀 快速开始

### 1. 创建消费者（注解方式）

```java
@Slf4j
@Singleton
@RabbitConsumer(
    queueName = "user.events",
    maxRetries = 3,
    retryDelayMs = 2000,
    autoAck = false,
    description = "用户事件处理消费者"
)
public class UserEventConsumer implements MessageConsumer {

    @Override
    public Boolean handleMessage(RabbitMQMessage message) {
        try {
            String eventData = message.body().toString();
            log.info("处理用户事件: {}", eventData);

            // 处理业务逻辑
            processUserEvent(eventData);

            return true; // 处理成功
        } catch (Exception e) {
            log.error("处理用户事件失败", e);
            return false; // 处理失败，会触发重试
        }
    }

    @Override
    public String getConsumerName() {
        return "UserEventConsumer";
    }
}
```

### 2. 启动系统并发送消息

```java
@Inject
private SimpleMQManager mqManager;

public void start() {
    // 自动扫描并启动所有消费者
    mqManager.scanAndStartConsumers("com.vertx.template");

    // 发送文本消息
    mqManager.sendToQueue("user.events", "用户登录事件");

    // 发送JSON消息
    JsonObject userEvent = new JsonObject()
        .put("userId", "12345")
        .put("action", "login")
        .put("timestamp", System.currentTimeMillis());

    mqManager.sendJsonToQueue("user.events", userEvent);

    // 发送到交换机
    mqManager.sendToExchange("user.exchange", "user.created", "新用户注册");
}
```

### 3. 查看监控信息

```java
// 获取系统状态
int activeConsumers = mqManager.getActiveConsumerCount();
boolean isActive = mqManager.isConsumerActive("UserEventConsumer");

// 获取监控统计
JsonObject stats = mqManager.getMonitor().getAllStats();
log.info("监控数据: {}", stats.encodePrettily());
```

## 🎛️ API 参考

### 消费者管理

| 方法                                 | 说明                 |
| ------------------------------------ | -------------------- |
| `scanAndStartConsumers(basePackage)` | 扫描并启动所有消费者 |
| `startConsumer(consumerName)`        | 启动指定消费者       |
| `stopConsumer(consumerName)`         | 停止指定消费者       |
| `stopAllConsumers()`                 | 停止所有消费者       |

### 消息发送

| 方法                                                 | 说明                   |
| ---------------------------------------------------- | ---------------------- |
| `sendToQueue(queueName, message)`                    | 发送文本消息到队列     |
| `sendToQueue(queueName, message, properties)`        | 发送带属性的消息到队列 |
| `sendToExchange(exchange, routingKey, message)`      | 发送消息到交换机       |
| `sendJsonToQueue(queueName, jsonData)`               | 发送JSON消息到队列     |
| `sendJsonToExchange(exchange, routingKey, jsonData)` | 发送JSON消息到交换机   |

### 状态查询

| 方法                                 | 说明                 |
| ------------------------------------ | -------------------- |
| `getActiveConsumerCount()`           | 获取活跃消费者数量   |
| `getRegisteredConsumerCount()`       | 获取已注册消费者数量 |
| `isConsumerActive(consumerName)`     | 检查消费者是否活跃   |
| `isConsumerRegistered(consumerName)` | 检查消费者是否已注册 |
| `getMonitor()`                       | 获取监控组件         |

## ⚙️ 注解配置

### @RabbitConsumer 参数说明

| 参数             | 类型    | 默认值 | 说明                      |
| ---------------- | ------- | ------ | ------------------------- |
| **queueName**    | String  | 必填   | 队列名称                  |
| **autoAck**      | boolean | false  | 是否自动确认消息          |
| **enabled**      | boolean | true   | 是否启用消费者            |
| **maxRetries**   | int     | 3      | 最大重试次数，0表示不重试 |
| **retryDelayMs** | long    | 1000L  | 重试延迟时间（毫秒）      |
| **description**  | String  | ""     | 消费者描述信息            |

## 🔄 重试机制

### 简化的重试逻辑
- **线性延迟**：重试延迟 = retryDelayMs × 重试次数
- **最大重试**：达到maxRetries后放弃重试
- **触发条件**：
  - 返回 `false`
  - 抛出异常

### 重试示例
```java
@RabbitConsumer(
    queueName = "order.process",
    maxRetries = 5,        // 最多重试5次
    retryDelayMs = 1000    // 首次重试延迟1秒，第二次2秒，第三次3秒...
)
public class OrderConsumer implements MessageConsumer {
    // ...
}
```

## 📊 监控功能

### 基础统计指标
- ✅ **成功计数**：处理成功的消息数量
- ❌ **失败计数**：处理失败的消息数量
- 🔄 **重试计数**：重试的总次数
- ⏱️ **平均处理时间**：消息处理耗时统计
- 🕐 **最后活跃时间**：最后一次处理消息的时间

### 获取监控数据
```java
// 获取JSON格式的统计信息
JsonObject allStats = mqManager.getMonitor().getAllStats();

// 重置统计信息
mqManager.getMonitor().resetStats("UserEventConsumer");
mqManager.getMonitor().resetAllStats();
```

## 🔧 业务集成示例

### 在Service中使用

```java
@Singleton
public class UserService {

    @Inject
    private SimpleMQManager mqManager;

    public User createUser(CreateUserRequest request) {
        // 创建用户
        User user = Future.await(userRepository.save(request.toUser()));

        // 发送用户创建事件
        JsonObject userCreatedEvent = new JsonObject()
            .put("userId", user.getId())
            .put("username", user.getUsername())
            .put("createdAt", user.getCreatedAt().toString());

        mqManager.sendJsonToQueue("user.created", userCreatedEvent);

        return user;
    }
}
```

### 在Controller中查看状态

```java
@RestController
@RequestMapping("/api/mq")
public class MQController {

    @Inject
    private SimpleMQManager mqManager;

    @GetMapping("/status")
    public JsonObject getStatus() {
        return new JsonObject()
            .put("activeConsumers", mqManager.getActiveConsumerCount())
            .put("registeredConsumers", mqManager.getRegisteredConsumerCount())
            .put("stats", mqManager.getMonitor().getAllStats());
    }

    @PostMapping("/send")
    public void sendMessage(@RequestBody JsonObject message) {
        String queueName = message.getString("queue");
        String content = message.getString("content");
        mqManager.sendToQueue(queueName, content);
    }
}
```

## ⚠️ 注意事项

### 队列管理
- **队列必须预先创建**：系统不会自动创建队列或交换机
- **队列不存在会报错**：启动时会检查队列是否存在

### 错误处理
- **返回false**：会触发重试，不会抛出异常
- **抛出异常**：会触发重试，同时记录错误日志
- **重试耗尽**：会调用 `onMessageFailed` 回调

### 依赖注入
- **使用@Singleton**：确保消费者是单例
- **Guice自动管理**：支持构造函数注入其他服务

## 🎯 架构优势

### 相比分离式设计的优势

| 方面           | 分离式设计                        | SimpleMQManager     | 优势   |
| -------------- | --------------------------------- | ------------------- | ------ |
| **学习成本**   | 需要学习Producer和Consumer两套API | 只需学习一套统一API | ⬇️ 50%  |
| **依赖注入**   | 需要注入多个组件                  | 只需注入一个Manager | ⬇️ 简化 |
| **文件数量**   | 2个核心类 + 各自的配置            | 1个核心类           | ⬇️ 50%  |
| **维护复杂度** | 需要维护两套生命周期              | 统一的生命周期管理  | ⬇️ 40%  |
| **使用一致性** | 两套不同的API风格                 | 统一的API风格       | ✅ 提升 |

### 设计权衡
- ✅ **简化优先**：牺牲了严格的职责分离，换取使用便利性
- ✅ **实用导向**：符合小型项目快速开发的需求
- ✅ **内聚性强**：MQ相关的所有操作都在一个地方
- ⚠️ **类体积增大**：单个类承担了更多职责（但仍在可控范围内）

---

**设计理念**：在保持核心功能完整的前提下，优先考虑开发者的使用体验和学习成本，实现"简单而强大"的MQ管理系统。 🚀
