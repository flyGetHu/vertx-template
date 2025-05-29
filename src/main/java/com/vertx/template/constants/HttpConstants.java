package com.vertx.template.constants;

/**
 * HTTP相关常量定义 包含HTTP状态码、响应头、内容类型等常量
 *
 * @author 系统
 * @since 1.0.0
 */
public final class HttpConstants {

  /** 私有构造函数，防止实例化 */
  private HttpConstants() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  // ==================== HTTP状态码常量 ====================

  /** HTTP状态码：成功 */
  public static final int HTTP_OK = 200;

  /** HTTP状态码：未找到 */
  public static final int HTTP_NOT_FOUND = 404;

  /** HTTP状态码：方法不允许 */
  public static final int HTTP_METHOD_NOT_ALLOWED = 405;

  /** HTTP状态码：请求过多 */
  public static final int HTTP_TOO_MANY_REQUESTS = 429;

  /** HTTP状态码：服务不可用 */
  public static final int HTTP_SERVICE_UNAVAILABLE = 503;

  /** HTTP状态码：网关超时 */
  public static final int HTTP_GATEWAY_TIMEOUT = 504;

  // ==================== 响应头常量 ====================

  /** 响应头：内容类型 */
  public static final String CONTENT_TYPE_HEADER = "content-type";

  /** 响应头：缓存控制 */
  public static final String CACHE_CONTROL_HEADER = "cache-control";

  /** 响应头：访问控制允许源 */
  public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "access-control-allow-origin";

  /** 响应头：访问控制允许方法 */
  public static final String ACCESS_CONTROL_ALLOW_METHODS = "access-control-allow-methods";

  /** 响应头：访问控制允许头 */
  public static final String ACCESS_CONTROL_ALLOW_HEADERS = "access-control-allow-headers";

  // ==================== 内容类型常量 ====================

  /** 内容类型：JSON */
  public static final String APPLICATION_JSON = "application/json";

  /** 内容类型：JSON（带UTF-8编码） */
  public static final String APPLICATION_JSON_UTF8 = "application/json; charset=utf-8";

  /** 内容类型：纯文本 */
  public static final String TEXT_PLAIN = "text/plain";

  /** 内容类型：HTML */
  public static final String TEXT_HTML = "text/html";

  /** 内容类型：XML */
  public static final String APPLICATION_XML = "application/xml";

  /** 内容类型：表单数据 */
  public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";

  /** 内容类型：多部分表单数据 */
  public static final String MULTIPART_FORM_DATA = "multipart/form-data";

  // ==================== HTTP方法常量 ====================

  /** HTTP方法：GET */
  public static final String METHOD_GET = "GET";

  /** HTTP方法：POST */
  public static final String METHOD_POST = "POST";

  /** HTTP方法：PUT */
  public static final String METHOD_PUT = "PUT";

  /** HTTP方法：DELETE */
  public static final String METHOD_DELETE = "DELETE";

  /** HTTP方法：PATCH */
  public static final String METHOD_PATCH = "PATCH";

  /** HTTP方法：OPTIONS */
  public static final String METHOD_OPTIONS = "OPTIONS";

  /** HTTP方法：HEAD */
  public static final String METHOD_HEAD = "HEAD";

  // ==================== 错误消息常量 ====================

  /** 错误消息：未找到 */
  public static final String ERROR_NOT_FOUND = "Not Found";

  /** 错误消息：方法不允许 */
  public static final String ERROR_METHOD_NOT_ALLOWED = "Method Not Allowed";

  /** 错误消息：请求过多 */
  public static final String ERROR_TOO_MANY_REQUESTS = "Too Many Requests";

  /** 错误消息：服务不可用 */
  public static final String ERROR_SERVICE_UNAVAILABLE = "Service Unavailable";

  /** 错误消息：网关超时 */
  public static final String ERROR_GATEWAY_TIMEOUT = "Gateway Timeout";

  /** 错误消息：内部服务器错误 */
  public static final String ERROR_INTERNAL_SERVER_ERROR = "Internal Server Error";

  /** 错误消息：参数无效 */
  public static final String ERROR_INVALID_PARAMETER = "Invalid Parameter";

  /** 错误消息：未授权 */
  public static final String ERROR_UNAUTHORIZED = "Unauthorized";

  /** 错误消息：禁止访问 */
  public static final String ERROR_FORBIDDEN = "Forbidden";
}
