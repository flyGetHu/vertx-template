# ID生成策略说明

本项目支持两种ID生成策略：FlexID生成器和数据库自增ID。通过`@Id`注解的`generated`属性来控制使用哪种策略。

## 策略配置

### 1. FlexID生成器（默认策略）

```java
@Id  // 默认 generated = false，使用FlexID生成器
private Long id;

// 或者显式指定
@Id(generated = false)
private Long id;
```

**特点：**
- 分布式友好，支持多机器部署
- 保证时间顺序性，后生成的ID值更大
- 支持高并发（单机每秒10万）
- 无视时间回拨问题
- 最大支持99台机器
- 可使用约300年

**ID结构：**
```
时间差值（7+位）| 毫秒内序列（00-99：2位）| 机器ID（00-99：2位）| 随机数（00-99：2位）
```

**配置机器ID：**
- 系统属性：`-Dflex.work.id=1`
- 环境变量：`FLEX_WORK_ID=1`
- 默认机器ID为1

### 2. 数据库自增ID

```java
@Id(generated = true)  // 使用数据库自增策略
private Long id;
```

**特点：**
- 传统的数据库自增ID
- 单机部署友好
- 依赖数据库的AUTO_INCREMENT功能
- ID值连续递增

## 使用示例

### FlexID策略示例（User实体）

```java
@Data
@Table("users")
public class User extends BaseEntity {
    @Id  // 默认使用FlexID生成器
    private Long id;

    private String username;
    private String email;
    // 其他字段...
}
```

### 自增ID策略示例（Product实体）

```java
@Data
@Table("products")
public class Product extends BaseEntity {
    @Id(generated = true)  // 使用数据库自增
    private Long id;

    private String name;
    private Double price;
    // 其他字段...
}
```

## 数据库表结构

### FlexID策略对应的表结构

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY,  -- 不使用AUTO_INCREMENT
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 自增ID策略对应的表结构

```sql
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 使用AUTO_INCREMENT
    name VARCHAR(50) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 保存操作行为

### FlexID策略

```java
User user = new User();
user.setUsername("张三");
user.setEmail("zhangsan@example.com");

// 保存时自动生成FlexID并设置到实体
User savedUser = userRepository.save(user);
System.out.println("生成的FlexID: " + savedUser.getId()); // 例如：1704067200000123456
```

### 自增ID策略

```java
Product product = new Product();
product.setName("iPhone 15");
product.setPrice(6999.0);

// 保存时由数据库生成自增ID，然后回填到实体
Product savedProduct = productRepository.save(product);
System.out.println("生成的自增ID: " + savedProduct.getId()); // 例如：1, 2, 3...
```

## 选择建议

### 使用FlexID生成器的场景
- 分布式系统部署
- 需要保证ID的时间顺序性
- 高并发场景
- 分库分表场景
- 微服务架构

### 使用数据库自增ID的场景
- 单机部署
- 传统单体应用
- 需要连续递增的ID
- 与现有系统兼容

## 注意事项

1. **默认策略变更**：从v1.1.0开始，默认使用FlexID生成器（`generated = false`）
2. **数据库兼容性**：自增ID策略需要数据库支持AUTO_INCREMENT
3. **ID类型**：两种策略都使用Long类型的ID
4. **性能考虑**：FlexID生成器在高并发场景下性能更好
5. **分布式考虑**：多机器部署时必须使用FlexID生成器

## 迁移指南

### 从自增ID迁移到FlexID

1. 修改实体类注解：
```java
// 原来
@Id(generated = true)
private Long id;

// 修改为
@Id  // 或 @Id(generated = false)
private Long id;
```

2. 修改数据库表结构：
```sql
-- 移除AUTO_INCREMENT
ALTER TABLE your_table MODIFY id BIGINT NOT NULL;
```

### 从FlexID迁移到自增ID

1. 修改实体类注解：
```java
// 原来
@Id
private Long id;

// 修改为
@Id(generated = true)
private Long id;
```

2. 修改数据库表结构：
```sql
-- 添加AUTO_INCREMENT
ALTER TABLE your_table MODIFY id BIGINT AUTO_INCREMENT;
```
