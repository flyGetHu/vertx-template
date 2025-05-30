package com.vertx.template.model.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 列名注解，用于标识实体字段对应的数据库列名
 *
 * @author template
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
  /**
   * 列名
   *
   * @return 数据库列名
   */
  String value();

  /**
   * 列名（别名）
   *
   * @return 数据库列名
   */
  String name() default "";

  /**
   * 是否为主键
   *
   * @return true表示主键字段
   */
  boolean primaryKey() default false;

  /**
   * 是否可插入
   *
   * @return true表示插入时包含此字段
   */
  boolean insertable() default true;

  /**
   * 是否可更新
   *
   * @return true表示更新时包含此字段
   */
  boolean updatable() default true;
}
