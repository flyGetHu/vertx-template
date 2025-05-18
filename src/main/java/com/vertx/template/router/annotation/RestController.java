package com.vertx.template.router.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为REST控制器，该类中定义的方法将自动注册为HTTP处理器
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestController {
  /**
   * 控制器名称，可选
   */
  String value() default "";
}
