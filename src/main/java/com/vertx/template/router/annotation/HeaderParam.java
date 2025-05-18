package com.vertx.template.router.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记请求头参数
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface HeaderParam {
  /**
   * 请求头名称
   */
  String value() default "";

  /**
   * 是否必须，默认为true
   */
  boolean required() default true;
}
