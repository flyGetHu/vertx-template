package com.vertx.template.router.resolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.config.RouterConfig;
import com.vertx.template.exception.ValidationException;
import com.vertx.template.middleware.auth.AuthenticationManager;
import com.vertx.template.middleware.auth.UserContext;
import com.vertx.template.middleware.auth.annotation.CurrentUser;
import com.vertx.template.middleware.validation.ValidationUtils;
import com.vertx.template.router.annotation.*;
import com.vertx.template.router.cache.MethodMetadata;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.validation.Valid;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 参数解析器，专门负责解析HTTP请求中的各种参数 @功能描述 解析路径参数、查询参数、请求体、请求头等 @职责范围 参数提取、类型转换、参数验证 */
@Singleton
public class ParameterResolver {

  private final ObjectMapper objectMapper;
  private final RouterConfig routerConfig;

  @Inject
  public ParameterResolver(RouterConfig routerConfig) {
    this.routerConfig = routerConfig;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.findAndRegisterModules();
  }

  /**
   * 解析方法参数
   *
   * @param metadata 方法元数据（可能为null）
   * @param method 方法信息
   * @param ctx 路由上下文
   * @return 解析后的参数数组
   */
  public Object[] resolveArguments(MethodMetadata metadata, Method method, RoutingContext ctx) {
    if (metadata != null) {
      return resolveFromCache(metadata, ctx);
    }
    return resolveFromReflection(method, ctx);
  }

  /** 使用缓存的元数据解析参数 */
  private Object[] resolveFromCache(MethodMetadata metadata, RoutingContext ctx) {
    List<MethodMetadata.ParameterMetadata> parameters = metadata.getParameters();
    Object[] args = new Object[parameters.size()];

    for (int i = 0; i < parameters.size(); i++) {
      MethodMetadata.ParameterMetadata paramMetadata = parameters.get(i);
      args[i] = resolveParameterFromCache(paramMetadata, ctx);
    }

    return args;
  }

  /** 基于缓存元数据解析单个参数 */
  private Object resolveParameterFromCache(
      MethodMetadata.ParameterMetadata paramMetadata, RoutingContext ctx) {
    return switch (paramMetadata.getParameterType()) {
      case PATH_PARAM -> resolvePathParam(paramMetadata.getName(), paramMetadata.getType(), ctx);
      case QUERY_PARAM ->
          resolveQueryParam(
              paramMetadata.getName(), paramMetadata.getType(), paramMetadata.isRequired(), ctx);
      case HEADER_PARAM ->
          resolveHeaderParam(
              paramMetadata.getName(), paramMetadata.getType(), paramMetadata.isRequired(), ctx);
      case REQUEST_BODY ->
          resolveRequestBody(paramMetadata.getType(), paramMetadata.isNeedsValidation(), ctx);
      case CURRENT_USER -> ctx.get("currentUser");
      case UNKNOWN -> paramMetadata.getType().equals(RoutingContext.class) ? ctx : null;
    };
  }

  /** 通过反射解析参数 */
  private Object[] resolveFromReflection(Method method, RoutingContext ctx) {
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    Annotation[][] paramAnnotations = method.getParameterAnnotations();

    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      Class<?> paramType = parameter.getType();
      Annotation[] annotations = paramAnnotations[i];

      if (annotations.length > 0) {
        args[i] = resolveAnnotatedParam(parameter, annotations, ctx);
      } else if (paramType.equals(RoutingContext.class)) {
        args[i] = ctx;
      } else {
        args[i] = null;
      }
    }

    return args;
  }

  /** 解析带注解的参数 */
  private Object resolveAnnotatedParam(
      Parameter parameter, Annotation[] annotations, RoutingContext ctx) {
    Class<?> paramType = parameter.getType();

    for (Annotation annotation : annotations) {
      if (annotation instanceof PathParam pathParam) {
        String name = pathParam.value().isEmpty() ? parameter.getName() : pathParam.value();
        return resolvePathParam(name, paramType, ctx);
      } else if (annotation instanceof QueryParam queryParam) {
        String name = queryParam.value().isEmpty() ? parameter.getName() : queryParam.value();
        return resolveQueryParam(name, paramType, queryParam.required(), ctx);
      } else if (annotation instanceof RequestBody) {
        boolean needValidation = Arrays.stream(annotations).anyMatch(a -> a instanceof Valid);
        return resolveRequestBody(paramType, needValidation, ctx);
      } else if (annotation instanceof HeaderParam headerParam) {
        String name = headerParam.value().isEmpty() ? parameter.getName() : headerParam.value();
        return resolveHeaderParam(name, paramType, headerParam.required(), ctx);
      } else if (annotation instanceof CurrentUser) {
        UserContext userContext = AuthenticationManager.getCurrentUser(ctx);
        if (userContext == null) {
          throw new ValidationException("当前用户上下文不存在，请确保已通过认证");
        }
        return userContext;
      }
    }

    return null;
  }

  /** 解析路径参数 */
  private Object resolvePathParam(String name, Class<?> type, RoutingContext ctx) {
    String value = ctx.pathParam(name);
    validateParameterLength(value, name, "路径参数");
    return convertValue(value, type, "路径参数 " + name);
  }

  /** 解析查询参数 */
  private Object resolveQueryParam(
      String name, Class<?> type, boolean required, RoutingContext ctx) {
    String value = ctx.request().getParam(name);

    if (value == null && required) {
      throw new ValidationException(String.format("查询参数 %s 不能为空", name));
    }

    validateParameterLength(value, name, "查询参数");
    return convertValue(value, type, "查询参数 " + name);
  }

  /** 解析请求头参数 */
  private Object resolveHeaderParam(
      String name, Class<?> type, boolean required, RoutingContext ctx) {
    String value = ctx.request().getHeader(name);

    if (value == null && required) {
      throw new ValidationException(String.format("请求头 %s 不能为空", name));
    }

    validateParameterLength(value, name, "请求头");
    return convertValue(value, type, "请求头 " + name);
  }

  /** 解析请求体 */
  private Object resolveRequestBody(Class<?> type, boolean needValidation, RoutingContext ctx) {
    try {
      // 检查请求体大小限制
      if (ctx.body() != null && ctx.body().length() > routerConfig.getMaxRequestBodySize()) {
        throw new ValidationException(
            String.format(
                "请求体大小超过限制，最大允许 %d 字节，实际 %d 字节",
                routerConfig.getMaxRequestBodySize(), ctx.body().length()));
      }

      JsonObject jsonBody = ctx.body().asJsonObject();
      if (jsonBody == null || jsonBody.isEmpty()) {
        throw new ValidationException("请求体不能为空");
      }

      Object body;
      if (type == JsonObject.class) {
        body = jsonBody;
      } else {
        try {
          String jsonString = jsonBody.encode();
          body = objectMapper.readValue(jsonString, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
          throw new ValidationException("请求体JSON格式错误: " + e.getMessage());
        } catch (Exception e) {
          throw new ValidationException("请求体转换失败: " + e.getMessage());
        }
      }

      if (body == null) {
        throw new ValidationException("请求体转换后为空");
      }

      // 参数校验
      if (needValidation && routerConfig.isEnableParameterValidation()) {
        try {
          ValidationUtils.validate(body);
        } catch (ValidationException e) {
          throw e;
        } catch (Exception e) {
          throw new ValidationException("参数校验失败: " + e.getMessage());
        }
      }

      return body;
    } catch (DecodeException e) {
      throw new ValidationException("请求体JSON解析失败: " + e.getMessage());
    } catch (ValidationException e) {
      throw e;
    } catch (Exception e) {
      throw new ValidationException("处理请求体时发生异常: " + e.getMessage());
    }
  }

  /** 验证参数长度 */
  private void validateParameterLength(String value, String name, String paramType) {
    if (value != null
        && routerConfig.isEnableParameterValidation()
        && value.length() > routerConfig.getMaxParameterLength()) {
      throw new ValidationException(
          String.format(
              "%s %s 长度超过限制，最大允许 %d 字符，实际 %d 字符",
              paramType, name, routerConfig.getMaxParameterLength(), value.length()));
    }
  }

  /** 类型转换 */
  private Object convertValue(String value, Class<?> targetType, String parameterName) {
    if (value == null) {
      return null;
    }

    try {
      return switch (targetType) {
        case Class<?> clazz when clazz == String.class -> value;
        case Class<?> clazz when clazz == Integer.class || clazz == int.class ->
            parseInteger(value, parameterName);
        case Class<?> clazz when clazz == Long.class || clazz == long.class ->
            parseLong(value, parameterName);
        case Class<?> clazz when clazz == Double.class || clazz == double.class ->
            parseDouble(value, parameterName);
        case Class<?> clazz when clazz == Boolean.class || clazz == boolean.class ->
            Boolean.parseBoolean(value);
        default -> parseComplexType(value, targetType, parameterName);
      };
    } catch (ValidationException e) {
      throw e;
    } catch (Exception e) {
      throw new ValidationException(
          String.format(
              "%s 类型转换失败: 无法将值 '%s' 转换为 %s 类型", parameterName, value, targetType.getSimpleName()));
    }
  }

  private Integer parseInteger(String value, String parameterName) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(
          String.format("%s 格式错误: 无法将值 '%s' 转换为整数", parameterName, value));
    }
  }

  private Long parseLong(String value, String parameterName) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(
          String.format("%s 格式错误: 无法将值 '%s' 转换为长整数", parameterName, value));
    }
  }

  private Double parseDouble(String value, String parameterName) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(
          String.format("%s 格式错误: 无法将值 '%s' 转换为浮点数", parameterName, value));
    }
  }

  private Object parseComplexType(String value, Class<?> targetType, String parameterName) {
    try {
      return new JsonObject().put("value", value).mapTo(Map.class).get("value");
    } catch (Exception e) {
      throw new ValidationException(
          String.format("%s 类型转换失败: 不支持的参数类型 %s", parameterName, targetType.getName()));
    }
  }
}
