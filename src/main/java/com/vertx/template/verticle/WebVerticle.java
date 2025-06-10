package com.vertx.template.verticle;

import com.vertx.template.router.RouterRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Web服务器Verticle 负责HTTP服务器启动、路由注册和Web相关配置 */
public class WebVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(WebVerticle.class);

  private HttpServer httpServer;

  @Override
  public void start(Promise<Void> startPromise) {
    try {
      // 获取从MainVerticle传递过来的配置
      final JsonObject config = config();

      // 启动HTTP服务器
      Future.await(startHttpServer(config));

      // 启动成功
      logger.info("WebVerticle启动成功");
      startPromise.complete();
    } catch (Exception e) {
      logger.error("WebVerticle启动失败：", e);
      startPromise.fail(e);
    }
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    if (httpServer != null) {
      httpServer
          .close()
          .onComplete(
              result -> {
                if (result.succeeded()) {
                  logger.info("HTTP服务器已停止");
                  stopPromise.complete();
                } else {
                  logger.error("HTTP服务器停止失败：", result.cause());
                  stopPromise.fail(result.cause());
                }
              });
    } else {
      stopPromise.complete();
    }
  }

  private Future<Void> startHttpServer(final JsonObject config) {
    final Promise<Void> promise = Promise.promise();

    try {
      // 获取服务器配置
      final JsonObject serverConfig = config.getJsonObject("server", new JsonObject());
      final int port = serverConfig.getInteger("port", 8888);
      final String host = serverConfig.getString("host", "localhost");

      // 使用路由注册中心配置路由
      final RouterRegistry routerRegistry = new RouterRegistry(vertx, config);
      final Router router = routerRegistry.registerAll();

      // 创建HTTP服务器
      httpServer = vertx.createHttpServer();

      // 启动HTTP服务器（使用await直接获取结果）
      Future.await(httpServer.requestHandler(router).listen(port, host));

      logger.info("HTTP服务器启动成功 - 监听 {}:{}", host, port);
      promise.complete();
    } catch (Exception e) {
      logger.error("HTTP服务器启动失败：", e);
      promise.fail(e);
    }

    return promise.future();
  }
}
