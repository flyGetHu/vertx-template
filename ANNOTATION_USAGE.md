# 数据库映射注解使用指南

本项目支持使用注解来动态配置数据库表名和列名映射，采用**约定优于配置**的原则，默认使用驼峰转蛇形命名，只有特殊情况才使用注解。

## 设计原则

- **表名**：直接使用 `@Table` 注解定义的名称，不做额外处理
- **列名**：默认使用驼峰转蛇形命名（如：`userName` → `user_name`），只有特殊情况才使用 `@Column` 注解
- **约定优于配置**：减少注解使用，提高代码简洁性

## 可用注解

### @Table
用于标识实体类对应的数据库表名，直接使用注解中定义的名称。

```java
@Table("users")  // 表名直接为 users
public class User extends BaseEntity {
    // ...
}
```

**属性：**
- `value()`: 表名（主要属性）
- `name()`: 表名（备用属性，与value等效）

### @Column
**仅在特殊情况下使用**，如需要下划线命名或特殊配置时。

```java
// 特殊情况：需要下划线命名
@Column(value = "created_at", updatable = false)
private LocalDateTime createdAt;

// 大部分情况：不需要注解，自动转换
private String username;  // 自动映射为 user_name
private String email;     // 自动映射为 email
```

**属性：**
- `value()`: 列名（主要属性）
- `name()`: 列名（备用属性，与value等效）
- `insertable()`: 是否可插入（默认true）
- `updatable()`: 是否可更新（默认true）
- `primaryKey()`: 是否为主键（默认false）

### @Id
用于标识实体的主键字段，通常不需要配合 `@Column` 使用。

```java
@Id
private Long id;  // 自动映射为 id 列
```

**属性：**
- `generated()`: 主键是否自动生成（默认true）

## 使用示例

### 完整的实体类示例

```java
@Data
@EqualsAndHashCode(callSuper = false)
@Table("users")  // 指定表名
public class User extends BaseEntity {

  @Id  // 主键标识，自动映射为 id 列
  private Long id;

  @NotBlank(message = "用户名不能为空")
  @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
  private String username;  // 自动映射为 username 列

  @NotBlank(message = "密码不能为空")
  @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
  private String password;  // 自动映射为 password 列

  @Email(message = "邮箱格式不正确")
  private String email;  // 自动映射为 email 列

  @Column(value = "created_at", updatable = false)  // 特殊情况：需要下划线命名且不可更新
  private LocalDateTime createdAt;

  @Column("updated_at")  // 特殊情况：需要下划线命名
  private LocalDateTime updatedAt;

  private boolean active;  // 自动映射为 active 列
}
```

### 命名转换示例

| Java字段名    | 数据库列名     | 是否需要注解 | 说明                      |
| ------------- | -------------- | ------------ | ------------------------- |
| `id`          | `id`           | 否           | 简单字段，直接映射        |
| `username`    | `username`     | 否           | 简单字段，直接映射        |
| `firstName`   | `first_name`   | 否           | 驼峰自动转蛇形            |
| `createdAt`   | `created_at`   | 是           | 需要@Column指定下划线命名 |
| `isActive`    | `is_active`    | 否           | 驼峰自动转蛇形            |
| `userProfile` | `user_profile` | 否           | 驼峰自动转蛇形            |

## 工作原理

### 1. 表名解析
- 如果实体类有 `@Table` 注解，直接使用注解中定义的表名，不做额外处理
- 如果没有注解，使用类名转换为下划线格式并添加复数后缀（如：User -> users）

### 2. 列名解析
- **默认行为**：使用字段名转换为下划线格式（如：userName -> user_name）
- **特殊情况**：如果字段有 `@Column` 注解，使用注解中指定的列名
- **约定优于配置**：大部分字段无需注解，自动转换命名

### 3. SQL 动态生成
- **INSERT SQL**: 自动排除自动生成的主键字段和不可插入的字段
- **UPDATE SQL**: 自动排除主键字段和不可更新的字段
- **参数绑定**: 根据字段顺序和注解配置自动绑定参数

### 4. 字段过滤规则

**插入操作 (INSERT)**:
- 排除标记为 `@Id(generated = true)` 的字段
- 排除标记为 `@Column(insertable = false)` 的字段

**更新操作 (UPDATE)**:
- 排除所有标记为 `@Id` 的字段
- 排除标记为 `@Column(updatable = false)` 的字段

## 优势

1. **约定优于配置**: 减少注解使用，大部分字段无需手动配置
2. **自动命名转换**: 智能的驼峰转蛇形命名，符合数据库规范
3. **代码简洁性**: 只在特殊情况下使用注解，保持代码整洁
4. **灵活性**: 可以自由指定表名和列名，不受Java命名约定限制
5. **可维护性**: 数据库结构变更时只需修改注解，无需修改SQL语句
6. **类型安全**: 编译时检查，避免运行时错误
7. **自动化**: 减少手动编写SQL的工作量
8. **一致性**: 统一的映射规则，减少人为错误

## 注意事项

1. 如果不使用注解，系统会使用默认的命名转换规则
2. `@Id` 注解的字段默认被认为是自动生成的主键，在插入时会被排除
3. `created_at` 等时间戳字段建议设置为 `updatable = false`
4. 确保注解中指定的表名和列名与实际数据库结构一致
