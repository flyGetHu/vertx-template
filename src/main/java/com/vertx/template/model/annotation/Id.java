package com.vertx.template.model.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 主键注解，用于标识实体的主键字段
 *
 * @author template
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Id {
  /**
   * 是否自动生成
   *
   * @return true表示自动生成主键值，false表示使用FlexID生成器
   */
  boolean generated() default false;
}
