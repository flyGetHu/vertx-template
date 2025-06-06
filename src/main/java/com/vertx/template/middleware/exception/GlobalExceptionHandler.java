package com.vertx.template.middleware.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.exception.RateLimitException;
import com.vertx.template.exception.ValidationException;
import com.vertx.template.middleware.auth.AuthenticationException;
import com.vertx.template.model.dto.ApiResponse;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 全局异常处理器 */
@Singleton
public class GlobalExceptionHandler implements Handler<RoutingContext> {
  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Inject
  public GlobalExceptionHandler() {}

  @Override
  public void handle(RoutingContext ctx) {
    Throwable failure = ctx.failure();
    if (failure != null) {
      // 生成错误追踪ID
      String errorId = UUID.randomUUID().toString().substring(0, 8);

      // 记录详细异常日志
      logException(ctx, failure, errorId);

      // 构建错误响应
      ApiResponse<?> response = buildErrorResponse(failure, errorId);

      // 发送响应
      sendErrorResponse(ctx, response);
    } else {
      // 没有异常，继续处理
      ctx.next();
    }
  }

  /**
   * 记录异常日志
   *
   * @param ctx 路由上下文
   * @param failure 异常对象
   * @param errorId 错误追踪ID
   */
  private void logException(RoutingContext ctx, Throwable failure, String errorId) {
    String requestPath = ctx.request().path();
    String method = ctx.request().method().name();
    String remoteAddress =
        ctx.request().remoteAddress() != null ? ctx.request().remoteAddress().host() : "unknown";

    // 根据异常类型记录不同级别的日志
    if (failure instanceof BusinessException) {
      // 业务异常记录为WARN级别
      logger.warn(
          "[{}] 业务异常 - {} {} - 客户端: {} - 异常: {}",
          errorId,
          method,
          requestPath,
          remoteAddress,
          failure.getMessage());
    } else if (failure instanceof RateLimitException rateLimitEx) {
      // 限流异常记录为WARN级别，包含限流信息
      logger.warn(
          "[{}] 限流异常 - {} {} - 客户端: {} - 限流键: {} - 限流规则: {} - 重试时间: {}秒",
          errorId,
          method,
          requestPath,
          remoteAddress,
          rateLimitEx.getRateLimitKey(),
          rateLimitEx.getLimitInfo(),
          rateLimitEx.getRetryAfterSeconds());
    } else if (isSerializationException(failure)) {
      // 序列化异常记录为ERROR级别，包含完整堆栈
      logger.error(
          "[{}] 序列化异常 - {} {} - 客户端: {} - 这可能是数据模型配置问题",
          errorId,
          method,
          requestPath,
          remoteAddress,
          failure);
    } else if (isValidationException(failure)) {
      // 参数校验异常记录为WARN级别
      logger.warn(
          "[{}] 参数校验异常 - {} {} - 客户端: {} - 异常: {}",
          errorId,
          method,
          requestPath,
          remoteAddress,
          failure.getMessage());
    } else {
      // 其他系统异常记录为ERROR级别
      logger.error(
          "[{}] 系统异常 - {} {} - 客户端: {} - 异常类型: {}",
          errorId,
          method,
          requestPath,
          remoteAddress,
          failure.getClass().getSimpleName(),
          failure);
    }
  }

  /**
   * 构建错误响应
   *
   * @param failure 异常对象
   * @param errorId 错误追踪ID
   * @return API响应对象
   */
  private ApiResponse<?> buildErrorResponse(Throwable failure, String errorId) {
    return switch (failure) {
      case ValidationException ex -> {
        // 参数校验异常，包含详细的校验错误信息
        final var response = ApiResponse.error(ex.getCode(), ex.getMessage());
        // 添加验证错误信息
        response.setExtra("validationErrors", ex.getValidationErrors());
        yield response;
      }
      case AuthenticationException ex -> {
        // 认证异常，返回401状态码和认证失败信息
        yield ApiResponse.error(ex.getCode(), ex.getMessage());
      }
      case RateLimitException ex -> {
        // 限流异常，返回429状态码和限流信息
        final var response = ApiResponse.error(ex.getCode(), ex.getMessage());
        // 添加限流相关信息
        response.setExtra("rateLimitKey", ex.getRateLimitKey());
        response.setExtra("limitInfo", ex.getLimitInfo());
        response.setExtra("retryAfterSeconds", ex.getRetryAfterSeconds());
        yield response;
      }
      case BusinessException ex -> {
        // 业务异常直接返回异常信息
        yield ApiResponse.error(ex.getCode(), ex.getMessage());
      }
      case Throwable ex when isSerializationException(ex) -> {
        // 序列化异常返回通用错误信息，不暴露技术细节
        logger.error("[{}] 数据序列化失败，请检查数据模型配置", errorId);
        yield ApiResponse.error(500, "数据处理异常，请稍后重试");
      }
      case Throwable ex when isValidationException(ex) -> {
        // 其他参数校验异常返回友好提示
        yield ApiResponse.error(400, "请求参数格式错误");
      }
      default -> {
        // 其他系统异常返回通用错误信息
        yield ApiResponse.error(500, "系统繁忙，请稍后重试");
      }
    };
  }

  /**
   * 发送错误响应
   *
   * @param ctx 路由上下文
   * @param response API响应对象
   */
  private void sendErrorResponse(RoutingContext ctx, ApiResponse<?> response) {
    try {
      // 检查响应头是否已发送
      if (ctx.response().headWritten()) {
        logger.warn("响应头已发送，无法发送错误响应");
        return;
      }

      // 检查连接是否已关闭
      if (ctx.response().closed()) {
        logger.warn("连接已关闭，无法发送错误响应");
        return;
      }

      ctx.response()
          .setStatusCode(200) // 统一使用200状态码，通过业务code区分错误
          .putHeader("content-type", "application/json")
          .end(Json.encodePrettily(response));
    } catch (Exception e) {
      // 如果响应发送失败，记录日志并尝试发送最简单的错误响应
      logger.error("发送错误响应失败", e);

      // 最后的保护机制：仅在响应头未发送且连接未关闭时尝试发送简单响应
      if (!ctx.response().headWritten() && !ctx.response().closed()) {
        try {
          ctx.response()
              .setStatusCode(500)
              .putHeader("content-type", "text/plain")
              .end("Internal Server Error");
        } catch (Exception fallbackException) {
          logger.error("发送兜底错误响应也失败", fallbackException);
          // 这时只能记录日志，无法继续处理
        }
      }
    }
  }

  /**
   * 判断是否为序列化相关异常
   *
   * @param throwable 异常对象
   * @return 是否为序列化异常
   */
  private boolean isSerializationException(Throwable throwable) {
    return throwable instanceof JsonProcessingException
        || throwable instanceof JsonMappingException
        || throwable.getMessage() != null
            && (throwable.getMessage().contains("Failed to encode as JSON")
                || throwable.getMessage().contains("jackson")
                || throwable.getMessage().contains("JSON")
                || throwable.getMessage().contains("serializ"));
  }

  /**
   * 判断是否为参数校验异常（非ValidationException类型的其他校验异常）
   *
   * @param throwable 异常对象
   * @return 是否为校验异常
   */
  private boolean isValidationException(Throwable throwable) {
    // 排除已经单独处理的ValidationException
    if (throwable instanceof ValidationException) {
      return false;
    }
    return throwable.getClass().getSimpleName().contains("Validation")
        || throwable.getClass().getSimpleName().contains("Constraint")
        || (throwable.getMessage() != null && throwable.getMessage().contains("validation"));
  }
}
