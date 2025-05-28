package com.vertx.template.model.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** BaseEntity测试类 验证fromRow方法对各种数据类型的映射功能 */
class BaseEntityTest {

  @Mock private Row mockRow;

  private TestEntity testEntity;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    testEntity = new TestEntity();
  }

  @Test
  void testFromRowWithStringField() {
    // 准备测试数据
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("name");
    when(mockRow.getString("name")).thenReturn("测试用户");

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals("测试用户", result.getName());
  }

  @Test
  void testFromRowWithIntegerField() {
    // 准备测试数据
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("age");
    when(mockRow.getInteger("age")).thenReturn(25);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals(25, result.getAge());
  }

  @Test
  void testFromRowWithLongField() {
    // 准备测试数据
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("id");
    when(mockRow.getLong("id")).thenReturn(123456L);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals(123456L, result.getId());
  }

  @Test
  void testFromRowWithBooleanField() {
    // 准备测试数据
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("active");
    when(mockRow.getBoolean("active")).thenReturn(true);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertTrue(result.getActive());
  }

  @Test
  void testFromRowWithDoubleField() {
    // 准备测试数据
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("salary");
    when(mockRow.getDouble("salary")).thenReturn(5000.50);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals(5000.50, result.getSalary(), 0.01);
  }

  @Test
  void testFromRowWithFloatField() {
    // 准备测试数据
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("score");
    when(mockRow.getFloat("score")).thenReturn(95.5f);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals(95.5f, result.getScore(), 0.01f);
  }

  @Test
  void testFromRowWithLocalDateTimeField() {
    // 准备测试数据
    LocalDateTime testDateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("created_at"); // camelToSnake("createdAt")
    when(mockRow.getLocalDateTime("created_at")).thenReturn(testDateTime);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals(testDateTime, result.getCreatedAt());
  }

  @Test
  void testFromRowWithLocalDateField() {
    // 准备测试数据
    LocalDate testDate = LocalDate.of(2024, 1, 15);
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("birth_date"); // camelToSnake("birthDate")
    when(mockRow.getLocalDate("birth_date")).thenReturn(testDate);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals(testDate, result.getBirthDate());
  }

  @Test
  void testFromRowWithBigDecimalField() {
    // 准备测试数据
    BigDecimal testDecimal = new BigDecimal("999.99");
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("amount");
    when(mockRow.getBigDecimal("amount")).thenReturn(testDecimal);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals(testDecimal, result.getAmount());
  }

  @Test
  void testFromRowWithJsonObjectField() {
    // 准备测试数据
    JsonObject testJson = new JsonObject().put("key", "value");
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("metadata");
    when(mockRow.getJsonObject("metadata")).thenReturn(testJson);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals(testJson, result.getMetadata());
  }

  @Test
  void testFromRowWithMultipleFields() {
    // 准备测试数据
    when(mockRow.size()).thenReturn(4);
    when(mockRow.getColumnName(0)).thenReturn("id");
    when(mockRow.getColumnName(1)).thenReturn("name");
    when(mockRow.getColumnName(2)).thenReturn("age");
    when(mockRow.getColumnName(3)).thenReturn("active");

    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("name")).thenReturn("张三");
    when(mockRow.getInteger("age")).thenReturn(30);
    when(mockRow.getBoolean("active")).thenReturn(true);

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertEquals(1L, result.getId());
    assertEquals("张三", result.getName());
    assertEquals(30, result.getAge());
    assertTrue(result.getActive());
  }

  @Test
  void testFromRowWithNullValues() {
    // 准备测试数据
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("name"); // Column "name" exists
    when(mockRow.getString("name")).thenReturn(null); // Value is null

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果
    assertNotNull(result);
    assertNull(result.getName());
  }

  @Test
  void testFromRowWithNonExistentColumn() {
    // 准备测试数据 - 模拟列不存在的情况
    // Simulate a row that has some other columns, but not 'name' or 'age'
    when(mockRow.size()).thenReturn(1);
    when(mockRow.getColumnName(0)).thenReturn("some_other_unrelated_column");
    // No mocks for getValue("name") or getValue("age") are needed for this test's
    // purpose,

    // 执行测试
    TestEntity result = BaseEntity.fromRow(mockRow, TestEntity.class);

    // 验证结果 - 应该创建对象但字段为默认值
    assertNotNull(result);
    assertNull(result.getName());
    assertNull(result.getAge());
  }

  @Test
  void testFromRowWithInvalidClass() {
    // 测试异常情况
    assertThrows(
        RuntimeException.class,
        () -> {
          BaseEntity.fromRow(mockRow, InvalidClass.class);
        });
  }

  /** 测试用实体类 - 继承BaseEntity */
  public static class TestEntity extends BaseEntity {
    private Long id;
    private String name;
    private Integer age;
    private Boolean active;
    private Double salary;
    private Float score;
    private LocalDateTime createdAt;
    private LocalDate birthDate;
    private BigDecimal amount;
    private JsonObject metadata;

    // Getters and Setters
    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Integer getAge() {
      return age;
    }

    public void setAge(Integer age) {
      this.age = age;
    }

    public Boolean getActive() {
      return active;
    }

    public void setActive(Boolean active) {
      this.active = active;
    }

    public Double getSalary() {
      return salary;
    }

    public void setSalary(Double salary) {
      this.salary = salary;
    }

    public Float getScore() {
      return score;
    }

    public void setScore(Float score) {
      this.score = score;
    }

    public LocalDateTime getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
    }

    public LocalDate getBirthDate() {
      return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
      this.birthDate = birthDate;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }

    public JsonObject getMetadata() {
      return metadata;
    }

    public void setMetadata(JsonObject metadata) {
      this.metadata = metadata;
    }
  }

  /** 无效类（没有无参构造函数） */
  public static class InvalidClass extends BaseEntity {
    public InvalidClass(String param) {
      // 有参构造函数，没有无参构造函数
    }
  }
}
