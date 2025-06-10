package com.vertx.template.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.middleware.auth.annotation.AuthType;
import com.vertx.template.middleware.auth.annotation.CurrentUser;
import com.vertx.template.middleware.auth.annotation.RequireAuth;
import com.vertx.template.model.context.UserContext;
import com.vertx.template.model.entity.User;
import com.vertx.template.router.annotation.*;
import com.vertx.template.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 用户控制器 负责处理用户相关的HTTP请求，调用UserService进行业务处理 */
@RestController
@RequestMapping("/api/users")
@Singleton
public class UserController {
  private static final Logger logger = LoggerFactory.getLogger(UserController.class);
  private final UserService userService;

  @Inject
  public UserController(UserService userService) {
    this.userService = userService;
    logger.info("UserController initialized with service: {}", userService.getClass().getName());
  }

  /**
   * 公开接口：获取系统信息（不需要认证）
   *
   * @return 系统基本信息
   */
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

  /**
   * 获取所有用户（需要JWT认证）
   *
   * @param activeOnly 是否只查询活跃用户，可选参数
   * @return 用户列表
   */
  @GetMapping("")
  public List<User> getAllUsers(
      @QueryParam(value = "active", required = false) Boolean activeOnly) {
    logger.debug("获取用户列表，activeOnly: {}", activeOnly);
    return userService.getAllUsers(activeOnly);
  }

  /**
   * 根据ID获取用户
   *
   * @param id 用户ID
   * @return 用户对象
   */
  @GetMapping("/:id")
  public User getUserById(@PathParam("id") Long id) {
    logger.debug("根据ID获取用户: {}", id);
    return userService.getUserById(id);
  }

  /**
   * 创建用户
   *
   * @param user 用户对象，包含用户基本信息
   * @return 创建成功的用户对象
   */
  @PostMapping("")
  public User createUser(@Valid @RequestBody User user) {
    logger.debug("创建用户: {}", user != null ? user.getUsername() : "null");
    return userService.createUser(user);
  }

  /**
   * 更新用户
   *
   * @param id 用户ID
   * @param user 更新的用户信息
   * @return 更新后的用户对象
   */
  @PutMapping("/:id")
  public User updateUser(@PathParam("id") Long id, @Valid @RequestBody User user) {
    logger.debug("更新用户: {}", id);
    return userService.updateUser(id, user);
  }

  /**
   * 删除用户
   *
   * @param id 用户ID
   * @return 删除结果，true表示删除成功
   */
  @GetMapping("/:id/delete")
  public Boolean deleteUser(@PathParam("id") Long id) {
    logger.debug("删除用户: {}", id);
    return userService.deleteUser(id);
  }

  /**
   * 根据用户名查找用户
   *
   * @param username 用户名
   * @return 用户对象，如果不存在则返回null
   */
  @GetMapping("/by-username")
  public User findByUsername(@QueryParam("username") String username) {
    logger.debug("根据用户名查找用户: {}", username);
    return userService.findByUsername(username);
  }

  /**
   * 获取当前用户信息 - 需要JWT认证
   *
   * @param userContext 当前用户上下文
   * @return 包含用户信息的JsonObject
   */
  @GetMapping("/profile")
  @RequireAuth(AuthType.JWT)
  public io.vertx.core.json.JsonObject getCurrentUserProfile(@CurrentUser UserContext userContext) {
    logger.debug("获取当前用户信息: {}", userContext.getUserId());
    return userService.getUserProfile(userContext.getUserId());
  }

  /**
   * 更新当前用户信息 - 需要JWT认证
   *
   * @param userContext 当前用户上下文
   * @param updateData 更新数据
   * @return 包含操作结果的JsonObject
   */
  @PostMapping("/profile")
  @RequireAuth(AuthType.JWT)
  public io.vertx.core.json.JsonObject updateCurrentUserProfile(
      @CurrentUser UserContext userContext, @RequestBody io.vertx.core.json.JsonObject updateData) {
    logger.debug("更新当前用户信息: {}", userContext.getUserId());
    return userService.updateUserProfile(userContext.getUserId(), updateData);
  }
}
