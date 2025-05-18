package com.vertx.template.routes;

import com.google.inject.Inject;
import com.vertx.template.controller.UserController;
import com.vertx.template.router.RouteGroup;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用户相关路由配置
 */
public class UserRoutes implements RouteGroup {
  private static final Logger logger = LoggerFactory.getLogger(UserRoutes.class);
  private final UserController controller;

  /**
   * 构造函数
   *
   * @param controller 用户控制器
   */
  @Inject
  public UserRoutes(UserController controller) {
    this.controller = controller;
  }

  /**
   * 实现RouteGroup接口，注册所有用户相关路由
   *
   * @param router Vert.x路由器
   */
  @Override
  public void register(Router router) {
    // 获取所有用户
    router.get("/api/users").handler(controller.getUsers());

    // 获取指定ID的用户
    router.get("/api/users/:id").handler(controller.getUserById());

    // 此处可以添加更多用户相关路由...

    logger.debug("用户路由注册完成");
  }
}
