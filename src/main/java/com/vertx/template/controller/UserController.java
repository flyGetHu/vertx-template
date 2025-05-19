package com.vertx.template.controller;

import com.vertx.template.exception.BusinessException;
import com.vertx.template.model.entity.User;
import com.vertx.template.repository.UserRepository;
import com.vertx.template.router.annotation.*;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/users")
@Singleton
public class UserController {
  private static final Logger logger = LoggerFactory.getLogger(UserController.class);
  private final UserRepository userRepository;

  @Inject
  public UserController(UserRepository userRepository) {
    this.userRepository = userRepository;
    logger.info("UserController initialized with repository: {}", userRepository.getClass().getName());
  }

  /**
   * 获取所有用户
   */
  @GetMapping("")
  public List<User> getAllUsers(@QueryParam(value = "active", required = false) Boolean activeOnly) {

    if (Boolean.TRUE.equals(activeOnly)) {
      logger.debug("查询活跃用户");
      return userRepository.findActiveUsers();
    } else {
      logger.debug("查询所有用户");
      return userRepository.findAll();
    }
  }

  /**
   * 根据ID获取用户
   */
  @GetMapping("/:id")
  public User getUserById(@PathParam("id") Long id) {
    logger.debug("查询用户: {}", id);
    return userRepository.findById(id);
  }

  /**
   * 创建用户
   */
  @PostMapping("")
  public User createUser(@Valid @RequestBody User user) {
    logger.debug("创建用户: {}", user.getUsername());
    return userRepository.save(user);
  }

  /**
   * 更新用户
   */
  @PutMapping("/:id")
  public User updateUser(@PathParam("id") Long id, @Valid @RequestBody User user) {

    logger.debug("更新用户: {}", id);
    return userRepository.update(id, user);

  }

  /**
   * 删除用户
   */
  @GetMapping("/:id/delete")
  public Boolean deleteUser(@PathParam("id") Long id) {
    logger.debug("删除用户: {}", id);
    return userRepository.deleteById(id);

  }

  /**
   * 根据用户名查找用户
   */
  @GetMapping("/by-username")
  public User findByUsername(@QueryParam("username") String username) {
    logger.debug("通过用户名查询: {}", username);
    return userRepository.findByUsername(username);
  }
}
