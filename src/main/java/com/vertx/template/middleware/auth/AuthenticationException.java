package com.vertx.template.middleware.auth;

import com.vertx.template.exception.BusinessException;

/** 认证异常 */
public class AuthenticationException extends BusinessException {

  public AuthenticationException(String message) {
    super(401, message);
  }

  public AuthenticationException(int code, String message) {
    super(code, message);
  }
}
