package com.vertx.template.model.entity;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.data.Numeric;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基础实体类，提供通用的Row映射功能
 *
 * @author template
 * @since 1.0.0
 */
public abstract class BaseEntity {

  /** 缓存类的字段信息，提高反射效率 */
  private static final Map<Class<?>, Field[]> CLASS_FIELDS_CACHE = new ConcurrentHashMap<>();

  /** 缓存Setter方法，提高反射效率 */
  private static final Map<String, Method> SETTER_METHOD_CACHE = new ConcurrentHashMap<>();

  /**
   * 通用的fromRow映射方法
   *
   * @param row 数据库行对象
   * @param clazz 目标实体类
   * @param <T> 实体类型
   * @return 映射后的实体对象
   */
  /**
   * 通用的fromRow映射方法，增加了字段和Setter方法缓存，并优化了列存在性检查。
   *
   * @param row 数据库行对象
   * @param clazz 目标实体类
   * @param <T> 实体类型
   * @return 映射后的实体对象
   */
  @SuppressWarnings("unchecked")
  public static <T extends BaseEntity> T fromRow(Row row, Class<T> clazz) {
    if (row == null) {
      return null;
    }

    try {
      T entity = clazz.getDeclaredConstructor().newInstance();
      Set<String> columnNamesInRow = new java.util.HashSet<>();
      for (int i = 0; i < row.size(); i++) {
        columnNamesInRow.add(row.getColumnName(i).toLowerCase());
      }

      // 获取所有字段并进行映射 (使用缓存)
      Field[] fields =
          CLASS_FIELDS_CACHE.computeIfAbsent(
              clazz,
              k -> {
                Field[] declaredFields = k.getDeclaredFields();
                for (Field field : declaredFields) {
                  field.setAccessible(true); // 提高反射性能，并允许访问私有字段
                }
                return declaredFields;
              });

      for (Field field : fields) {
        String fieldName = field.getName();
        String columnName = camelToSnake(fieldName);

        // 跳过不存在的列 (优化后)
        if (!columnNamesInRow.contains(columnName.toLowerCase())) {
          continue;
        }

        Object value = getValueFromRow(row, columnName, field.getType());
        if (value != null) {
          // 使用setter方法设置值 (使用缓存)
          String setterName = "set" + capitalize(fieldName);
          String cacheKey = clazz.getName() + "#" + setterName + "#" + field.getType().getName();
          Method setter =
              SETTER_METHOD_CACHE.computeIfAbsent(
                  cacheKey,
                  k -> {
                    try {
                      Method m = clazz.getMethod(setterName, field.getType());
                      m.setAccessible(true); // 提高反射性能
                      return m;
                    } catch (NoSuchMethodException e) {
                      // 对于某些没有标准setter的字段（例如boolean类型的isXXX），可以尝试其他方式或记录警告
                      // 这里简单抛出运行时异常，或者可以根据实际情况调整
                      throw new RuntimeException(
                          "Setter method not found: "
                              + setterName
                              + " for field "
                              + fieldName
                              + " in class "
                              + clazz.getSimpleName(),
                          e);
                    }
                  });
          setter.invoke(entity, value);
        }
      }

      return entity;
    } catch (Exception e) {
      throw new RuntimeException("Failed to map row to entity: " + clazz.getSimpleName(), e);
    }
  }

  /**
   * 从Row中获取指定类型的值 根据Vert.x MySQL驱动的类型映射规范实现
   *
   * @param row 数据库行对象
   * @param columnName 列名
   * @param fieldType 字段类型
   * @return 对应类型的值
   */
  private static Object getValueFromRow(Row row, String columnName, Class<?> fieldType) {
    return switch (fieldType) {
      // 数值类型映射
      case Class<?> c when c == Long.class || c == long.class -> row.getLong(columnName);
      case Class<?> c when c == Integer.class || c == int.class -> row.getInteger(columnName);
      case Class<?> c when c == Short.class || c == short.class -> row.getShort(columnName);
      case Class<?> c when c == Byte.class || c == byte.class ->
          row.getShort(columnName).byteValue();
      case Class<?> c when c == Double.class || c == double.class -> row.getDouble(columnName);
      case Class<?> c when c == Float.class || c == float.class -> row.getFloat(columnName);
      case Class<?> c when c == BigDecimal.class -> row.getBigDecimal(columnName);
      case Class<?> c when c == BigInteger.class -> {
        // BigInteger通常用于NUMERIC(precision=0)或DECIMAL(precision=0)
        BigDecimal decimal = row.getBigDecimal(columnName);
        yield decimal != null ? decimal.toBigInteger() : null;
      }
      case Class<?> c when c == Numeric.class -> row.getNumeric(columnName);

      // 布尔类型映射 (MySQL BOOLEAN/BOOL -> TINYINT(1))
      case Class<?> c when c == Boolean.class || c == boolean.class -> row.getBoolean(columnName);

      // 字符串类型映射 (CHAR, VARCHAR, TEXT, ENUM, SET)
      case Class<?> c when c == String.class -> row.getString(columnName);

      // 日期时间类型映射
      case Class<?> c when c == LocalDateTime.class -> row.getLocalDateTime(columnName);
      case Class<?> c when c == LocalDate.class -> row.getLocalDate(columnName);
      case Class<?> c when c == LocalTime.class -> row.getLocalTime(columnName);
      case Class<?> c when c == Duration.class ->
          row.getTemporal(columnName); // MySQL TIME类型映射到Duration

      // 二进制类型映射 (BINARY, VARBINARY, BLOB系列)
      case Class<?> c when c == Buffer.class -> row.getBuffer(columnName);
      case Class<?> c when c == byte[].class -> {
        Buffer buffer = row.getBuffer(columnName);
        yield buffer != null ? buffer.getBytes() : null;
      }

      // JSON类型映射
      case Class<?> c when c == JsonObject.class -> row.getJsonObject(columnName);
      case Class<?> c when c == JsonArray.class -> row.getJsonArray(columnName);

      // 通用对象类型
      default -> row.getValue(columnName);
    };
  }

  /**
   * 检查Row中是否包含指定列 (此方法已不再被fromRow直接使用，保留以供其他潜在用途或向后兼容).
   *
   * <p>注意: {@link #fromRow(Row, Class)} 方法已优化为预取列名集合进行检查，性能更优。
   *
   * @param row 数据库行对象
   * @param columnName 列名
   * @return 是否包含该列
   * @deprecated 已被 {@link #fromRow(Row, Class)} 中的内联列名检查取代以提高性能。
   */
  @Deprecated
  private static boolean hasColumn(Row row, String columnName) {
    // Vert.x SQL client Row.getColumnIndex(String) throws if column not found
    // or we can iterate row.columnNames()
    try {
      // 尝试获取列索引，如果列不存在会抛出异常
      row.getColumnIndex(columnName);
      return true;
    } catch (IllegalArgumentException e) {
      // 列名不存在
      return false;
    }
  }

  /**
   * 驼峰转下划线
   *
   * <p>将驼峰命名法（camelCase）字符串转换为下划线命名法（snake_case）字符串。
   *
   * @param camelCase 驼峰命名字符串，例如 "userName" 或 "userID"
   * @return 对应的下划线命名字符串，例如 "user_name" 或 "user_id"
   */
  private static String camelToSnake(String camelCase) {
    if (camelCase == null || camelCase.isEmpty()) {
      return camelCase;
    }
    StringBuilder result = new StringBuilder();
    result.append(Character.toLowerCase(camelCase.charAt(0)));
    for (int i = 1; i < camelCase.length(); i++) {
      char ch = camelCase.charAt(i);
      if (Character.isUpperCase(ch)) {
        result.append('_').append(Character.toLowerCase(ch));
      } else {
        result.append(ch);
      }
    }
    return result.toString();
  }

  /**
   * 首字母大写
   *
   * <p>将字符串的首字母大写。
   *
   * @param str 输入字符串，例如 "username" 或 "userId"
   * @return 首字母大写的字符串，例如 "Username" 或 "UserId"
   */
  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    if (str.length() == 1) {
      return str.toUpperCase();
    }
    StringBuilder sb = new StringBuilder(str.length());
    sb.append(Character.toUpperCase(str.charAt(0)));
    sb.append(str.substring(1));
    return sb.toString();
  }
}
