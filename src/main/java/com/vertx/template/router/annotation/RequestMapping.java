package com.vertx.template.router.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义请求映射的基础路径
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestMapping {
  /**
   * 请求路径
   */
  String value() default "";

  /**
   * 请求方法，默认支持所有方法
   */
  HttpMethod[] method() default {};
}
