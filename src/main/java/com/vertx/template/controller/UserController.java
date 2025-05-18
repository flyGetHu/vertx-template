package com.vertx.template.controller;

import javax.inject.Inject;
import javax.inject.Singleton;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.model.User;
import com.vertx.template.router.annotation.GetMapping;
import com.vertx.template.router.annotation.RequestMapping;
import com.vertx.template.router.annotation.RestController;
import com.vertx.template.service.UserService;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/users")
@Singleton
public class UserController {

  private final UserService userService;

  @Inject
  public UserController(UserService userService) {
    this.userService = userService;
  }

  /**
   * 获取所有用户
   */
  @GetMapping("")
  public List<User> getUsers() {
    // 直接返回数据对象，不再包装Future
    return Future.await(userService.getUsers());
  }

  /**
   * 根据ID获取用户
   */
  @GetMapping("/:id")
  public User getUserById(RoutingContext ctx) {
    String id = ctx.pathParam("id");

    // 验证ID格式
    if (id == null || id.trim().isEmpty()) {
      throw new BusinessException(400, "User ID is required");
    }

    // 直接返回数据对象，不再包装Future
    return Future.await(userService.getUserById(id));
  }
}
