package com.vertx.template.verticle;

import com.vertx.template.config.ConfigLoader;
import com.vertx.template.router.RouterRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) {
    try {
      // 加载配置（使用await直接获取结果）
      JsonObject config = Future.await(ConfigLoader.loadConfig(vertx));

      // 配置加载成功后，启动HTTP服务器
      Future.await(startHttpServer(config));

      // 启动成功
      startPromise.complete();
    } catch (Exception e) {
      logger.error("启动失败：", e);
      startPromise.fail(e);
    }
  }

  private Future<Void> startHttpServer(JsonObject config) {
    Promise<Void> promise = Promise.promise();

    try {
      // 获取服务器配置
      JsonObject serverConfig = config.getJsonObject("server", new JsonObject());
      int port = serverConfig.getInteger("port", 8888);
      String host = serverConfig.getString("host", "localhost");

      // 使用路由注册中心配置路由
      RouterRegistry routerRegistry = new RouterRegistry(vertx, config);
      Router router = routerRegistry.registerAll();

      // 启动HTTP服务器（使用await直接获取结果）
      Future.await(vertx.createHttpServer().requestHandler(router).listen(port, host));

      logger.info("HTTP服务器启动成功 - 监听 {}:{}", host, port);
      promise.complete();
    } catch (Exception e) {
      logger.error("HTTP服务器启动失败：", e);
      promise.fail(e);
    }

    return promise.future();
  }
}
