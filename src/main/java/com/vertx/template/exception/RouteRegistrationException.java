package com.vertx.template.exception;

/** 路由注册异常 当路由注册过程中发生错误时抛出此异常 */
public class RouteRegistrationException extends RuntimeException {

  public RouteRegistrationException(String message) {
    super(message);
  }

  public RouteRegistrationException(String message, Throwable cause) {
    super(message, cause);
  }

  public RouteRegistrationException(Throwable cause) {
    super(cause);
  }
}
