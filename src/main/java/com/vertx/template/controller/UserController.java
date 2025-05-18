package com.vertx.template.controller;

import com.google.inject.Inject;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.handler.ResponseHandler;
import com.vertx.template.model.User;
import com.vertx.template.service.UserService;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * 用户控制器
 */
public class UserController {
  private final UserService userService;
  private final ResponseHandler responseHandler;

  @Inject
  public UserController(UserService userService, ResponseHandler responseHandler) {
    this.userService = userService;
    this.responseHandler = responseHandler;
  }

  /**
   * 获取所有用户
   */
  public Handler<RoutingContext> getUsers() {
    return responseHandler.handle(ctx -> {
      // 直接返回业务数据，ResponseHandler会自动包装和序列化
      return Future.await(userService.getUsers());
    });
  }

  /**
   * 根据ID获取用户
   */
  public Handler<RoutingContext> getUserById() {
    return responseHandler.handle(ctx -> {
      String id = ctx.pathParam("id");

      // 验证ID格式
      if (id == null || id.trim().isEmpty()) {
        throw new BusinessException(400, "User ID is required");
      }

      // 直接返回业务数据，ResponseHandler会自动包装和序列化
      return Future.await(userService.getUserById(id));
    });
  }
}
