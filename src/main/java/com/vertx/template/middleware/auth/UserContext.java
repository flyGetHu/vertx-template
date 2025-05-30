package com.vertx.template.middleware.auth;

/** 用户上下文，保存当前认证用户的信息 */
public class UserContext {
  private final String userId;

  public UserContext(String userId) {
    this.userId = userId;
  }

  public String getUserId() {
    return userId;
  }

  @Override
  public String toString() {
    return "UserContext{userId='" + userId + "'}";
  }
}
