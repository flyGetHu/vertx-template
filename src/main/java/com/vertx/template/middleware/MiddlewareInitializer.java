package com.vertx.template.middleware;

import com.vertx.template.middleware.auth.AuthMiddleware;
import com.vertx.template.middleware.common.MiddlewareChain;
import com.vertx.template.middleware.common.MiddlewareResult;
import com.vertx.template.middleware.core.BodyHandlerMiddleware;
import com.vertx.template.middleware.core.CorsMiddleware;
import com.vertx.template.middleware.core.RequestLoggerMiddleware;
import com.vertx.template.middleware.ratelimit.RateLimitMiddleware;
import com.vertx.template.middleware.response.ResponseHandler;
import com.vertx.template.model.dto.ApiResponse;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局中间件初始化器
 *
 * <p>负责注册和管理所有全局中间件，统一中间件的初始化逻辑
 *
 * @author 系统
 * @since 1.0.0
 */
@Singleton
public class MiddlewareInitializer {
  private static final Logger logger = LoggerFactory.getLogger(MiddlewareInitializer.class);
  private final Vertx vertx;
  private final Router router;
  private final JsonObject config;
  private final MiddlewareChain middlewareChain;

  // 核心中间件
  private final CorsMiddleware corsMiddleware;
  private final BodyHandlerMiddleware bodyHandlerMiddleware;
  private final RequestLoggerMiddleware requestLoggerMiddleware;

  // 业务中间件
  private final AuthMiddleware authMiddleware;
  private final RateLimitMiddleware rateLimitMiddleware;

  // 响应处理器
  private final ResponseHandler responseHandler;

  /**
   * 构造函数
   *
   * @param vertx Vert.x实例
   * @param router 路由器实例
   * @param config 配置
   * @param middlewareChain 中间件链
   * @param corsMiddleware CORS中间件
   * @param bodyHandlerMiddleware Body处理器中间件
   * @param requestLoggerMiddleware 请求日志中间件
   * @param authMiddleware 认证中间件
   * @param rateLimitMiddleware 限流中间件
   * @param responseHandler 响应处理器
   */
  @Inject
  public MiddlewareInitializer(
      Vertx vertx,
      Router router,
      JsonObject config,
      MiddlewareChain middlewareChain,
      CorsMiddleware corsMiddleware,
      BodyHandlerMiddleware bodyHandlerMiddleware,
      RequestLoggerMiddleware requestLoggerMiddleware,
      AuthMiddleware authMiddleware,
      RateLimitMiddleware rateLimitMiddleware,
      ResponseHandler responseHandler) {
    this.vertx = vertx;
    this.router = router;
    this.config = config;
    this.middlewareChain = middlewareChain;
    this.corsMiddleware = corsMiddleware;
    this.bodyHandlerMiddleware = bodyHandlerMiddleware;
    this.requestLoggerMiddleware = requestLoggerMiddleware;
    this.authMiddleware = authMiddleware;
    this.rateLimitMiddleware = rateLimitMiddleware;
    this.responseHandler = responseHandler;
  }

  /**
   * 初始化并注册所有中间件
   *
   * <p>按照预定义的顺序注册核心中间件到中间件链中
   */
  public void initialize() {
    // 注册核心中间件到中间件链
    registerCoreMiddlewares();

    // 注册业务中间件到中间件链
    registerBusinessMiddlewares();

    logger.info(
        "全局中间件初始化完成，共注册了 {} 个中间件: {}",
        middlewareChain.size(),
        middlewareChain.getMiddlewareNames());
  }

  /**
   * 注册核心中间件
   *
   * <p>按照执行顺序注册中间件： 1. CORS中间件 (order=10) - 处理跨域请求 2. Body处理器中间件 (order=20) - 解析请求体 3. 请求日志中间件
   * (order=30) - 记录请求日志
   */
  private void registerCoreMiddlewares() {
    // 1. CORS中间件 (order=10)
    if (corsMiddleware.isEnabled()) {
      middlewareChain.register(corsMiddleware);
      logger.debug("已注册CORS中间件");
    }

    // 2. Body处理器中间件 (order=20)
    if (bodyHandlerMiddleware.isEnabled()) {
      middlewareChain.register(bodyHandlerMiddleware);
      logger.debug("已注册Body处理器中间件");
    }

    // 3. 请求日志中间件 (order=30)
    if (requestLoggerMiddleware.isEnabled()) {
      middlewareChain.register(requestLoggerMiddleware);
      logger.debug("已注册请求日志中间件");
    }

    logger.debug("核心中间件注册完成");
  }

  /**
   * 注册业务中间件
   *
   * <p>按照执行顺序注册中间件： 1. 认证中间件 (order=40) - 处理用户认证 2. 限流中间件 (order=50) - 处理请求限流
   */
  private void registerBusinessMiddlewares() {
    // 1. 认证中间件 (order=40)
    if (authMiddleware.isEnabled()) {
      middlewareChain.register(authMiddleware);
      logger.debug("已注册认证中间件");
    }

    // 2. 限流中间件 (order=50)
    if (rateLimitMiddleware.isEnabled()) {
      middlewareChain.register(rateLimitMiddleware);
      logger.debug("已注册限流中间件");
    }

    logger.debug("业务中间件注册完成");
  }

  /**
   * 创建统一的请求处理器
   *
   * <p>该方法作为所有路由的统一入口，负责： 1. 执行完整的中间件链 2. 处理中间件执行结果 3. 调用实际的业务处理器 4. 统一异常处理和响应格式化
   *
   * @param businessHandler 实际的业务处理器
   * @return 包装后的请求处理器
   */
  public Handler<RoutingContext> createRequestHandler(Handler<RoutingContext> businessHandler) {
    return context -> {
      try {
        // 执行中间件链
        MiddlewareResult result = middlewareChain.execute(context);

        // 检查中间件执行结果
        if (!result.shouldContinueChain()) {
          // 中间件链中断，直接返回响应
          handleMiddlewareResult(context, result);
          return;
        }

        // 中间件链执行成功，调用业务处理器
        businessHandler.handle(context);

      } catch (Exception e) {
        logger.error("请求处理过程中发生异常: {}", e.getMessage(), e);
        handleException(context, e);
      }
    };
  }

  /**
   * 处理中间件执行结果
   *
   * @param context 路由上下文
   * @param result 中间件执行结果
   */
  private void handleMiddlewareResult(RoutingContext context, MiddlewareResult result) {
    int statusCode = 500; // 默认状态码
    if (result.getStatusCode() != null) {
      try {
        statusCode = Integer.parseInt(result.getStatusCode());
        context.response().setStatusCode(statusCode);
      } catch (NumberFormatException e) {
        logger.warn("无效的状态码: {}, 使用默认值500", result.getStatusCode());
        context.response().setStatusCode(500);
      }
    }

    if (result.getMessage() != null) {
      // 返回错误消息
      ApiResponse<?> errorResponse = ApiResponse.error(statusCode, result.getMessage());
      context
          .response()
          .putHeader("content-type", "application/json")
          .end(Json.encodePrettily(errorResponse));
    } else {
      // 仅设置状态码，无消息内容
      context.response().end();
    }
  }

  /**
   * 处理异常
   *
   * @param context 路由上下文
   * @param e 异常
   */
  private void handleException(RoutingContext context, Exception e) {
    // 统一异常处理
    ApiResponse<?> errorResponse = ApiResponse.error(500, "服务器内部错误: " + e.getMessage());
    context
        .response()
        .putHeader("content-type", "application/json")
        .setStatusCode(500)
        .end(Json.encodePrettily(errorResponse));
  }

  /**
   * 获取中间件链实例
   *
   * @return 中间件链
   */
  public MiddlewareChain getMiddlewareChain() {
    return middlewareChain;
  }

  /**
   * 获取已注册的中间件数量
   *
   * @return 中间件数量
   */
  public int getRegisteredMiddlewareCount() {
    return middlewareChain.size();
  }

  /**
   * 检查指定中间件是否已注册
   *
   * @param middlewareName 中间件名称
   * @return 如果已注册返回true
   */
  public boolean isMiddlewareRegistered(String middlewareName) {
    return middlewareChain.containsMiddleware(middlewareName);
  }
}
