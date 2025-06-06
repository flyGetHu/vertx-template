package com.vertx.template.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.middleware.auth.annotation.AuthType;
import com.vertx.template.middleware.auth.annotation.CurrentUser;
import com.vertx.template.middleware.auth.annotation.RequireAuth;
import com.vertx.template.model.context.UserContext;
import com.vertx.template.model.entity.User;
import com.vertx.template.repository.UserRepository;
import com.vertx.template.router.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 用户控制器 */
@RestController
@RequestMapping("/api/users")
@Singleton
public class UserController {
  private static final Logger logger = LoggerFactory.getLogger(UserController.class);
  private final UserRepository userRepository;

  @Inject
  public UserController(UserRepository userRepository) {
    this.userRepository = userRepository;
    logger.info(
        "UserController initialized with repository: {}", userRepository.getClass().getName());
  }

  /** 公开接口：获取系统信息（不需要认证） */
  @GetMapping("/public/info")
  @RequireAuth(AuthType.NONE)
  public Object getPublicInfo() {
    logger.debug("获取公开系统信息");
    return new java.util.HashMap<String, Object>() {
      {
        put("version", "1.0.0");
        put("name", "Vert.x Template");
        put("timestamp", System.currentTimeMillis());
      }
    };
  }

  /** 获取所有用户（需要JWT认证） */
  @GetMapping("")
  public List<User> getAllUsers(
      @QueryParam(value = "active", required = false) Boolean activeOnly) {

    if (Boolean.TRUE.equals(activeOnly)) {
      logger.debug("查询活跃用户");
      return userRepository.findActiveUsers();
    } else {
      logger.debug("查询所有用户");
      return userRepository.findAll();
    }
  }

  /** 根据ID获取用户 */
  @GetMapping("/:id")
  public User getUserById(@PathParam("id") Long id) {
    logger.debug("查询用户: {}", id);
    return userRepository.findById(id);
  }

  /** 创建用户 */
  @PostMapping("")
  public User createUser(@Valid @RequestBody User user) {
    logger.debug("创建用户: {}", user.getUsername());
    return userRepository.save(user);
  }

  /** 更新用户 */
  @PutMapping("/:id")
  public User updateUser(@PathParam("id") Long id, @Valid @RequestBody User user) {

    logger.debug("更新用户: {}", id);
    return userRepository.update(id, user);
  }

  /** 删除用户 */
  @GetMapping("/:id/delete")
  public Boolean deleteUser(@PathParam("id") Long id) {
    logger.debug("删除用户: {}", id);
    return userRepository.deleteById(id);
  }

  /** 根据用户名查找用户 */
  @GetMapping("/by-username")
  public User findByUsername(@QueryParam("username") String username) {
    logger.debug("根据用户名查找用户: {}", username);
    return userRepository.findByUsername(username);
  }

  /** 获取当前用户信息 - 需要JWT认证 */
  @GetMapping("/profile")
  @RequireAuth(AuthType.JWT)
  public io.vertx.core.json.JsonObject getCurrentUserProfile(@CurrentUser UserContext userContext) {
    logger.debug("获取当前用户信息: {}", userContext.getUserId());
    return new io.vertx.core.json.JsonObject()
        .put("message", "获取用户信息成功")
        .put("userId", userContext.getUserId())
        .put("timestamp", System.currentTimeMillis());
  }

  /** 更新当前用户信息 - 需要JWT认证 */
  @PostMapping("/profile")
  @RequireAuth(AuthType.JWT)
  public io.vertx.core.json.JsonObject updateCurrentUserProfile(
      @CurrentUser UserContext userContext, @RequestBody io.vertx.core.json.JsonObject updateData) {
    logger.debug("更新当前用户信息: {}", userContext.getUserId());
    return new io.vertx.core.json.JsonObject()
        .put("message", "用户信息更新成功")
        .put("userId", userContext.getUserId())
        .put("updateData", updateData)
        .put("timestamp", System.currentTimeMillis());
  }
}
