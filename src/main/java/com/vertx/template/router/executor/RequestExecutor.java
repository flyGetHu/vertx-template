package com.vertx.template.router.executor;

import com.google.inject.Singleton;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.exception.RateLimitException;
import com.vertx.template.exception.ValidationException;
import com.vertx.template.middleware.auth.AuthenticationException;
import io.vertx.core.Future;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 请求执行器，专门负责控制器方法的调用和结果处理 @功能描述 调用控制器方法、处理Future结果、异常标准化 @职责范围 方法调用、结果处理、异常转换 */
@Singleton
public class RequestExecutor {

  private static final Logger logger = LoggerFactory.getLogger(RequestExecutor.class);

  /**
   * 执行控制器方法
   *
   * @param controller 控制器实例
   * @param method 要调用的方法
   * @param args 方法参数
   * @return 方法执行结果
   */
  public Object execute(Object controller, Method method, Object[] args) {
    try {
      Object result = method.invoke(controller, args);
      return handleResult(result);
    } catch (Exception e) {
      logger.error(
          "执行控制器方法 [{}#{}] 时发生异常", controller.getClass().getSimpleName(), method.getName(), e);
      return normalizeException(e);
    }
  }

  /** 处理方法执行结果 */
  private Object handleResult(Object result) {
    // 处理Future结果
    if (result instanceof Future<?> future) {
      @SuppressWarnings("unchecked")
      Future<Object> typedFuture = (Future<Object>) future;
      return Future.await(typedFuture);
    }

    return result;
  }

  /**
   * 标准化异常处理
   *
   * @param exception 原始异常
   * @return 标准化后的业务异常
   */
  public Exception normalizeException(Exception exception) {
    return switch (exception) {
      case AuthenticationException authEx -> authEx;
      case RateLimitException rateLimitEx -> rateLimitEx;
      case ValidationException validationEx -> validationEx;
      case BusinessException businessEx -> businessEx;
      case Exception ex when ex.getCause() instanceof BusinessException ->
          (BusinessException) ex.getCause();
      case Exception ex when ex.getCause() instanceof AuthenticationException ->
          (AuthenticationException) ex.getCause();
      case Exception ex when ex.getCause() instanceof RateLimitException ->
          (RateLimitException) ex.getCause();
      case Exception ex when ex.getCause() instanceof ValidationException ->
          (ValidationException) ex.getCause();
      default -> new BusinessException(500, "Internal Server Error: " + exception.getMessage());
    };
  }
}
