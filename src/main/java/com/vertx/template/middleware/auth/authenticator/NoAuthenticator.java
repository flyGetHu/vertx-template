package com.vertx.template.middleware.auth.authenticator;

import com.google.inject.Singleton;
import com.vertx.template.middleware.auth.AuthenticationException;
import com.vertx.template.middleware.auth.Authenticator;
import com.vertx.template.middleware.auth.UserContext;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 空认证器实现，用于不需要认证的接口 该认证器不执行任何认证逻辑，直接通过 */
@Singleton
public class NoAuthenticator implements Authenticator {
  private static final Logger logger = LoggerFactory.getLogger(NoAuthenticator.class);

  @Override
  public UserContext authenticate(RoutingContext ctx) throws AuthenticationException {
    // 空实现，不执行任何认证逻辑，返回null表示无用户上下文
    logger.debug("跳过认证检查，接口路径: {}", ctx.request().path());
    return null;
  }
}
