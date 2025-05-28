package com.vertx.template.security;

import com.google.inject.Injector;
import com.vertx.template.security.annotation.AuthType;
import com.vertx.template.security.authenticator.NoAuthenticator;
import com.vertx.template.security.impl.JwtAuthenticator;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 认证管理器，负责根据认证类型选择对应的认证器 */
@Singleton
public class AuthenticationManager {
  private static final Logger logger = LoggerFactory.getLogger(AuthenticationManager.class);
  private static final String USER_CONTEXT_KEY = "userContext";

  private final Map<AuthType, Authenticator> authenticators = new ConcurrentHashMap<>();
  private final Injector injector;

  @Inject
  public AuthenticationManager(Injector injector) {
    this.injector = injector;
    initializeAuthenticators();
  }

  /** 初始化认证器 */
  private void initializeAuthenticators() {
    // 注册JWT认证器
    authenticators.put(AuthType.JWT, injector.getInstance(JwtAuthenticator.class));

    // 注册空认证器
    authenticators.put(AuthType.NONE, injector.getInstance(NoAuthenticator.class));

    logger.info("认证管理器初始化完成，已注册 {} 个认证器", authenticators.size());
  }

  /**
   * 执行认证
   *
   * @param ctx 路由上下文
   * @param authType 认证类型
   * @throws AuthenticationException 认证失败时抛出
   */
  public void authenticate(RoutingContext ctx, AuthType authType) throws AuthenticationException {
    final Authenticator authenticator = authenticators.get(authType);

    if (authenticator == null) {
      throw new AuthenticationException("不支持的认证类型: " + authType);
    }

    try {
      final UserContext userContext = authenticator.authenticate(ctx);

      // 将用户上下文保存到路由上下文中（可能为null，表示无需认证）
      ctx.put(USER_CONTEXT_KEY, userContext);

      if (userContext != null) {
        logger.debug("认证成功，用户ID: {}", userContext.getUserId());
      } else {
        logger.debug("跳过认证，无用户上下文");
      }
    } catch (AuthenticationException e) {
      logger.debug("认证失败: {}", e.getMessage());
      throw e;
    }
  }

  /**
   * 从路由上下文中获取当前用户上下文
   *
   * @param ctx 路由上下文
   * @return 用户上下文，如果未认证则返回null
   */
  public static UserContext getCurrentUser(RoutingContext ctx) {
    return ctx.get(USER_CONTEXT_KEY);
  }

  /**
   * 注册自定义认证器
   *
   * @param authType 认证类型
   * @param authenticator 认证器实例
   */
  public void registerAuthenticator(AuthType authType, Authenticator authenticator) {
    authenticators.put(authType, authenticator);
    logger.info("注册自定义认证器: {}", authType);
  }
}
