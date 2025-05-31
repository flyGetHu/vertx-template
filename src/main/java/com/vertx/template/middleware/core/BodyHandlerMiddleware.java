package com.vertx.template.middleware.core;

import com.vertx.template.middleware.common.Middleware;
import com.vertx.template.middleware.common.MiddlewareResult;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Body处理器中间件
 *
 * <p>处理HTTP请求体的解析和缓存
 *
 * @author 系统
 * @since 1.0.0
 */
@Singleton
public class BodyHandlerMiddleware implements Middleware {
  private static final Logger logger = LoggerFactory.getLogger(BodyHandlerMiddleware.class);
  private final BodyHandler bodyHandler;
  private final boolean enabled;

  @Inject
  public BodyHandlerMiddleware(JsonObject config) {
    JsonObject bodyConfig = config.getJsonObject("body_handler", new JsonObject());
    this.enabled = bodyConfig.getBoolean("enabled", true);

    if (enabled) {
      // 配置Body处理器
      long bodyLimit = bodyConfig.getLong("body_limit", 1024L * 1024L); // 默认1MB
      String uploadsDirectory = bodyConfig.getString("uploads_directory", "uploads");
      boolean deleteUploadedFilesOnEnd =
          bodyConfig.getBoolean("delete_uploaded_files_on_end", true);

      this.bodyHandler =
          BodyHandler.create()
              .setBodyLimit(bodyLimit)
              .setUploadsDirectory(uploadsDirectory)
              .setDeleteUploadedFilesOnEnd(deleteUploadedFilesOnEnd);

      logger.debug("Body处理器中间件初始化完成，body限制: {}MB", bodyLimit / (1024 * 1024));
    } else {
      this.bodyHandler = null;
      logger.info("Body处理器中间件已禁用");
    }
  }

  @Override
  public MiddlewareResult handle(RoutingContext context) {
    if (!enabled) {
      return MiddlewareResult.success("Body处理器中间件已禁用");
    }

    try {
      // 使用Vert.x的BodyHandler处理请求体
      bodyHandler.handle(context);
      return MiddlewareResult.success("Body处理完成");
    } catch (Exception e) {
      logger.error("Body处理失败", e);
      return MiddlewareResult.failure("400", "请求体处理失败: " + e.getMessage());
    }
  }

  @Override
  public String getName() {
    return "BodyHandlerMiddleware";
  }

  @Override
  public int getOrder() {
    return 20; // 在CORS之后执行
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
