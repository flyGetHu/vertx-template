package com.vertx.template.middleware.ratelimit.core;

import com.vertx.template.middleware.ratelimit.annotation.RateLimit;
import io.vertx.ext.web.RoutingContext;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流键生成器 根据不同的限流维度生成对应的限流键
 *
 * @author System
 * @since 1.0.0
 */
@Slf4j
public class RateLimitKeyGenerator {

  private static final String KEY_SEPARATOR = ":";
  private static final String DEFAULT_USER_ID = "anonymous";
  private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("[^a-zA-Z0-9_.-]");

  /**
   * 生成限流键
   *
   * @param rateLimit 限流注解
   * @param method 目标方法
   * @param context Vert.x路由上下文
   * @param args 方法参数
   * @return 限流键
   */
  public static String generateKey(
      RateLimit rateLimit, Method method, RoutingContext context, Object[] args) {
    StringBuilder keyBuilder = new StringBuilder();

    // 添加基础前缀
    String baseKey = rateLimit.key().isEmpty() ? generateMethodKey(method) : rateLimit.key();
    keyBuilder.append("ratelimit").append(KEY_SEPARATOR).append(baseKey);

    // 根据维度添加特定的键部分
    String dimensionKey = generateDimensionKey(rateLimit, context, args);
    if (dimensionKey != null && !dimensionKey.isEmpty()) {
      keyBuilder.append(KEY_SEPARATOR).append(dimensionKey);
    }

    String finalKey = keyBuilder.toString();
    log.debug("Generated rate limit key: {} for dimension: {}", finalKey, rateLimit.dimension());

    return finalKey;
  }

  /** 根据维度生成键的特定部分 */
  private static String generateDimensionKey(
      RateLimit rateLimit, RoutingContext context, Object[] args) {
    switch (rateLimit.dimension()) {
      case IP:
        return getClientIp(context);

      case USER:
        return getUserId(context);

      case GLOBAL:
        return "global";

      case CUSTOM:
        return generateCustomKey(rateLimit.customKey(), context, args);

      default:
        log.warn("Unknown rate limit dimension: {}", rateLimit.dimension());
        return "unknown";
    }
  }

  /** 生成方法键 */
  private static String generateMethodKey(Method method) {
    return method.getDeclaringClass().getSimpleName() + "." + method.getName();
  }

  /** 获取客户端IP地址 */
  private static String getClientIp(RoutingContext context) {
    if (context == null || context.request() == null) {
      return "unknown";
    }

    // 尝试从各种头部获取真实IP
    String ip = context.request().getHeader("X-Forwarded-For");
    if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
      // X-Forwarded-For可能包含多个IP，取第一个
      int index = ip.indexOf(',');
      if (index != -1) {
        ip = ip.substring(0, index);
      }
      return sanitizeKey(ip.trim());
    }

    ip = context.request().getHeader("X-Real-IP");
    if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
      return sanitizeKey(ip);
    }

    ip = context.request().getHeader("X-Original-Forwarded-For");
    if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
      return sanitizeKey(ip);
    }

    // 最后使用远程地址
    ip =
        context.request().remoteAddress() != null
            ? context.request().remoteAddress().host()
            : "unknown";

    return sanitizeKey(ip);
  }

  /** 获取用户ID */
  private static String getUserId(RoutingContext context) {
    if (context == null) {
      return DEFAULT_USER_ID;
    }

    // 尝试从不同地方获取用户ID
    // 1. 从路由上下文的用户信息中获取
    if (context.user() != null) {
      String userId = context.user().principal().getString("userId");
      if (userId != null && !userId.isEmpty()) {
        return sanitizeKey(userId);
      }

      userId = context.user().principal().getString("id");
      if (userId != null && !userId.isEmpty()) {
        return sanitizeKey(userId);
      }

      userId = context.user().principal().getString("sub");
      if (userId != null && !userId.isEmpty()) {
        return sanitizeKey(userId);
      }
    }

    // 2. 从请求头中获取
    String userId = context.request().getHeader("X-User-Id");
    if (userId != null && !userId.isEmpty()) {
      return sanitizeKey(userId);
    }

    // 3. 从JWT token中获取（如果有的话）
    String authorization = context.request().getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      // 这里可以解析JWT获取用户ID，暂时简化处理
      return "jwt_user";
    }

    return DEFAULT_USER_ID;
  }

  /** 生成自定义键 简化版本，支持基本的参数访问 */
  private static String generateCustomKey(String customKey, RoutingContext context, Object[] args) {
    if (customKey == null || customKey.isEmpty()) {
      return "custom";
    }

    try {
      // 简单的参数替换，支持 #arg0, #arg1 等格式
      String result = customKey;
      if (args != null) {
        for (int i = 0; i < args.length; i++) {
          String placeholder = "#arg" + i;
          if (result.contains(placeholder) && args[i] != null) {
            result = result.replace(placeholder, args[i].toString());
          }
        }
      }

      // 支持一些常用的上下文变量
      if (result.contains("#ip")) {
        result = result.replace("#ip", getClientIp(context));
      }

      if (result.contains("#userId")) {
        result = result.replace("#userId", getUserId(context));
      }

      return sanitizeKey(result);

    } catch (Exception e) {
      log.warn("Failed to generate custom key with expression: {}", customKey, e);
      return "custom_error";
    }
  }

  /** 清理键值，确保安全性 */
  private static String sanitizeKey(String key) {
    if (key == null || key.isEmpty()) {
      return "empty";
    }

    // 移除不安全的字符
    String sanitized = SAFE_KEY_PATTERN.matcher(key).replaceAll("_");

    // 限制长度
    if (sanitized.length() > 100) {
      sanitized = sanitized.substring(0, 100);
    }

    return sanitized;
  }
}
