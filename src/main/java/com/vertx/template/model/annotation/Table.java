package com.vertx.template.model.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 表名注解，用于标识实体类对应的数据库表名
 *
 * @author template
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {
  /**
   * 表名
   *
   * @return 数据库表名
   */
  String value();

  /**
   * 表名（别名）
   *
   * @return 数据库表名
   */
  String name() default "";
}
