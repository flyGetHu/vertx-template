package com.vertx.template.controller;

import com.google.inject.Inject;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.model.ApiResponse;
import com.vertx.template.model.User;
import com.vertx.template.service.UserService;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

public class UserController {
  private final UserService userService;

  @Inject
  public UserController(UserService userService) {
    this.userService = userService;
  }

  public Handler<RoutingContext> getUsers() {
    return ctx -> {
      try {
        // 使用await直接获取结果
        List<User> users = Future.await(userService.getUsers());

        ApiResponse<Object> response = ApiResponse.success(users);
        ctx.response()
            .putHeader("content-type", "application/json")
            .setStatusCode(200)
            .end(Json.encodePrettily(response));
      } catch (Exception e) {
        ctx.fail(e);
      }
    };
  }

  public Handler<RoutingContext> getUserById() {
    return ctx -> {
      try {
        String id = ctx.pathParam("id");

        // 验证ID格式
        if (id == null || id.trim().isEmpty()) {
          ctx.fail(new BusinessException(400, "User ID is required"));
          return;
        }

        // 使用await直接获取结果
        User user = Future.await(userService.getUserById(id));

        ApiResponse<Object> response = ApiResponse.success(user);
        ctx.response()
            .putHeader("content-type", "application/json")
            .setStatusCode(200)
            .end(Json.encodePrettily(response));
      } catch (Exception e) {
        ctx.fail(e);
      }
    };
  }
}
