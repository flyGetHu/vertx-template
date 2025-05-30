package com.vertx.template.middleware.auth.annotation;

/** 认证类型枚举 */
public enum AuthType {
  /** JWT认证 */
  JWT,

  /** 自定义认证 */
  CUSTOM,

  /** 不需要认证（空实现） */
  NONE
}
