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

/**
 * 基础实体类，提供通用的Row映射功能
 *
 * @author template
 * @since 1.0.0
 */
public abstract class BaseEntity {

  /**
   * 通用的fromRow映射方法
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

      // 获取所有字段并进行映射
      Field[] fields = clazz.getDeclaredFields();
      for (Field field : fields) {
        String fieldName = field.getName();
        String columnName = camelToSnake(fieldName);

        // 跳过不存在的列
        if (!hasColumn(row, columnName)) {
          continue;
        }

        Object value = getValueFromRow(row, columnName, field.getType());
        if (value != null) {
          // 使用setter方法设置值
          String setterName = "set" + capitalize(fieldName);
          Method setter = clazz.getMethod(setterName, field.getType());
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
    // 数值类型映射
    if (fieldType == Long.class || fieldType == long.class) {
      return row.getLong(columnName);
    } else if (fieldType == Integer.class || fieldType == int.class) {
      return row.getInteger(columnName);
    } else if (fieldType == Short.class || fieldType == short.class) {
      return row.getShort(columnName);
    } else if (fieldType == Byte.class || fieldType == byte.class) {
      return row.getShort(columnName).byteValue();
    } else if (fieldType == Double.class || fieldType == double.class) {
      return row.getDouble(columnName);
    } else if (fieldType == Float.class || fieldType == float.class) {
      return row.getFloat(columnName);
    } else if (fieldType == BigDecimal.class) {
      return row.getBigDecimal(columnName);
    } else if (fieldType == BigInteger.class) {
      // BigInteger通常用于NUMERIC(precision=0)或DECIMAL(precision=0)
      BigDecimal decimal = row.getBigDecimal(columnName);
      return decimal != null ? decimal.toBigInteger() : null;
    } else if (fieldType == Numeric.class) {
      return row.getNumeric(columnName);
    }
    // 布尔类型映射 (MySQL BOOLEAN/BOOL -> TINYINT(1))
    else if (fieldType == Boolean.class || fieldType == boolean.class) {
      return row.getBoolean(columnName);
    }
    // 字符串类型映射 (CHAR, VARCHAR, TEXT, ENUM, SET)
    else if (fieldType == String.class) {
      return row.getString(columnName);
    }
    // 日期时间类型映射
    else if (fieldType == LocalDateTime.class) {
      return row.getLocalDateTime(columnName);
    } else if (fieldType == LocalDate.class) {
      return row.getLocalDate(columnName);
    } else if (fieldType == LocalTime.class) {
      return row.getLocalTime(columnName);
    } else if (fieldType == Duration.class) {
      // MySQL TIME类型映射到Duration
      return row.getTemporal(columnName);
    }
    // 二进制类型映射 (BINARY, VARBINARY, BLOB系列)
    else if (fieldType == Buffer.class) {
      return row.getBuffer(columnName);
    } else if (fieldType == byte[].class) {
      Buffer buffer = row.getBuffer(columnName);
      return buffer != null ? buffer.getBytes() : null;
    }
    // JSON类型映射
    else if (fieldType == JsonObject.class) {
      return row.getJsonObject(columnName);
    } else if (fieldType == JsonArray.class) {
      return row.getJsonArray(columnName);
    }
    // 通用对象类型
    else {
      return row.getValue(columnName);
    }
  }

  /**
   * 检查Row中是否包含指定列
   *
   * @param row 数据库行对象
   * @param columnName 列名
   * @return 是否包含该列
   */
  private static boolean hasColumn(Row row, String columnName) {
    try {
      row.getValue(columnName);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 驼峰转下划线
   *
   * @param camelCase 驼峰命名字符串
   * @return 下划线命名字符串
   */
  private static String camelToSnake(String camelCase) {
    return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
  }

  /**
   * 首字母大写
   *
   * @param str 输入字符串
   * @return 首字母大写的字符串
   */
  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }
}
