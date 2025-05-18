package com.vertx.template;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * 应用启动入口
 */
public class Run {

  public static void main(String[] args) {
    // 设置VertxOptions，启用虚拟线程
    VertxOptions options = new VertxOptions()
        .setPreferNativeTransport(true) // 优先使用本地传输
        .setEventLoopPoolSize(2 * Runtime.getRuntime().availableProcessors()) // 设置事件循环池大小
        .setWorkerPoolSize(20); // 设置工作线程池大小

    // 创建Vertx实例
    Vertx vertx = Vertx.vertx(options);

    // 部署MainVerticle
    Future.await(vertx.deployVerticle(new MainVerticle(),
        new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)));
  }
}
