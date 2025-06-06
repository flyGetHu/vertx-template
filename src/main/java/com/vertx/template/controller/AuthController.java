package com.vertx.template.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.middleware.auth.JwtUtils;
import com.vertx.template.middleware.auth.annotation.AuthType;
import com.vertx.template.middleware.auth.annotation.RequireAuth;
import com.vertx.template.router.annotation.PostMapping;
import com.vertx.template.router.annotation.RequestBody;
import com.vertx.template.router.annotation.RequestMapping;
import com.vertx.template.router.annotation.RestController;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 认证控制器 */
@RestController
@RequestMapping("/api/auth")
@Singleton
public class AuthController {
  private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

  private final JwtUtils jwtUtils;

  @Inject
  public AuthController(JwtUtils jwtUtils) {
    this.jwtUtils = jwtUtils;
  }

  /** 用户登录 - 简化版本，实际项目中需要验证用户名密码 */
  @PostMapping("/login")
  @RequireAuth(AuthType.NONE)
  public JsonObject login(@RequestBody JsonObject loginRequest) {
    String username = loginRequest.getString("username");
    String password = loginRequest.getString("password");

    // 简化的验证逻辑，实际项目中需要查询数据库验证用户名密码
    if (username == null || username.trim().isEmpty()) {
      return new JsonObject().put("success", false).put("message", "用户名不能为空");
    }

    if (password == null || password.trim().isEmpty()) {
      return new JsonObject().put("success", false).put("message", "密码不能为空");
    }

    // 模拟用户ID，实际项目中从数据库获取
    String userId = "user_" + username;

    // 生成JWT token
    String token = jwtUtils.generateToken(userId);

    logger.info("用户 {} 登录成功", username);

    return new JsonObject()
        .put("success", true)
        .put("message", "登录成功")
        .put("token", token)
        .put("userId", userId)
        .put("timestamp", System.currentTimeMillis());
  }

  /** 生成测试token */
  @PostMapping("/test-token")
  public JsonObject generateTestToken(@RequestBody JsonObject request) {
    String userId = request.getString("userId", "test_user");
    Integer expireSeconds = request.getInteger("expireSeconds", 3600);

    String token = jwtUtils.generateToken(userId, expireSeconds);

    logger.info("生成测试token，用户ID: {}, 过期时间: {}秒", userId, expireSeconds);

    return new JsonObject()
        .put("success", true)
        .put("token", token)
        .put("userId", userId)
        .put("expireSeconds", expireSeconds)
        .put("timestamp", System.currentTimeMillis());
  }
}
