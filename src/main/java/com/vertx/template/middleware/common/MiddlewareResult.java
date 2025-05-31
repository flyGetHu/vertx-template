package com.vertx.template.middleware.common;

/**
 * 中间件执行结果
 *
 * @author 系统
 * @since 1.0.0
 */
public class MiddlewareResult {

  /** 是否成功 */
  private final boolean success;

  /** HTTP状态码 */
  private final String statusCode;

  /** 响应消息 */
  private final String message;

  /** 是否继续执行后续中间件 */
  private final boolean continueChain;

  /** 额外数据 */
  private final Object data;

  private MiddlewareResult(
      boolean success, String statusCode, String message, boolean continueChain, Object data) {
    this.success = success;
    this.statusCode = statusCode;
    this.message = message;
    this.continueChain = continueChain;
    this.data = data;
  }

  /**
   * 创建成功结果，继续执行后续中间件
   *
   * @return 成功结果
   */
  public static MiddlewareResult success() {
    return new MiddlewareResult(true, "200", "Success", true, null);
  }

  /**
   * 创建成功结果，继续执行后续中间件
   *
   * @param message 成功消息
   * @return 成功结果
   */
  public static MiddlewareResult success(String message) {
    return new MiddlewareResult(true, "200", message, true, null);
  }

  /**
   * 创建成功结果，继续执行后续中间件
   *
   * @param message 成功消息
   * @param data 额外数据
   * @return 成功结果
   */
  public static MiddlewareResult success(String message, Object data) {
    return new MiddlewareResult(true, "200", message, true, data);
  }

  /**
   * 创建失败结果，中断执行链条
   *
   * @param statusCode HTTP状态码
   * @param message 错误消息
   * @return 失败结果
   */
  public static MiddlewareResult failure(String statusCode, String message) {
    return new MiddlewareResult(false, statusCode, message, false, null);
  }

  /**
   * 创建失败结果，中断执行链条
   *
   * @param statusCode HTTP状态码
   * @param message 错误消息
   * @param data 额外数据
   * @return 失败结果
   */
  public static MiddlewareResult failure(String statusCode, String message, Object data) {
    return new MiddlewareResult(false, statusCode, message, false, data);
  }

  /**
   * 创建成功但停止执行后续中间件的结果
   *
   * @param message 消息
   * @return 停止结果
   */
  public static MiddlewareResult stop(String message) {
    return new MiddlewareResult(true, "200", message, false, null);
  }

  /**
   * 创建成功但停止执行后续中间件的结果
   *
   * @param statusCode HTTP状态码
   * @param message 消息
   * @return 停止结果
   */
  public static MiddlewareResult stop(String statusCode, String message) {
    return new MiddlewareResult(true, statusCode, message, false, null);
  }

  // Getters
  public boolean isSuccess() {
    return success;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public String getMessage() {
    return message;
  }

  public boolean shouldContinueChain() {
    return continueChain;
  }

  public Object getData() {
    return data;
  }

  @Override
  public String toString() {
    return String.format(
        "MiddlewareResult{success=%s, statusCode='%s', message='%s', continueChain=%s}",
        success, statusCode, message, continueChain);
  }
}
