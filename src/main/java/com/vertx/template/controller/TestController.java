package com.vertx.template.controller;

import com.vertx.template.model.dto.ApiResponse;
import com.vertx.template.router.annotation.GetMapping;
import com.vertx.template.router.annotation.RestController;
import io.vertx.ext.web.RoutingContext;

/** 测试控制器，用于验证CORS功能 */
@RestController
public class TestController {

  /**
   * 测试CORS的简单接口
   *
   * @param context 路由上下文
   */
  @GetMapping("/api/test/cors")
  public void testCors(RoutingContext context) {
    ApiResponse<String> response = ApiResponse.success("CORS测试成功！");
    context.response().putHeader("Content-Type", "application/json").end(response.toString());
  }

  /**
   * 获取服务器信息
   *
   * @param context 路由上下文
   */
  @GetMapping("/api/test/info")
  public void getServerInfo(RoutingContext context) {
    ApiResponse<Object> response =
        ApiResponse.success(
            new Object() {
              public final String server = "Vert.x Template";
              public final String version = "1.0.0";
              public final long timestamp = System.currentTimeMillis();
              public final String cors = "enabled";
            });

    context.response().putHeader("Content-Type", "application/json").end(response.toString());
  }
}
