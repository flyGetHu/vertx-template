package com.vertx.template.security.impl;

import com.vertx.template.security.AuthenticationException;
import com.vertx.template.security.Authenticator;
import com.vertx.template.security.UserContext;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.RoutingContext;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JWT认证器实现 */
@Singleton
public class JwtAuthenticator implements Authenticator {
  private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticator.class);
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final int EXPIRE_OFFSET_MINUTES = 5; // 过期时间偏移5分钟

  private final JWTAuth jwtAuth;

  @Inject
  public JwtAuthenticator(JsonObject config) {
    // 从配置中获取JWT密钥，如果没有配置则使用默认密钥
    String secret =
        config
            .getJsonObject("jwt", new JsonObject())
            .getString("secret", "default-secret-key-change-in-production");

    JWTAuthOptions jwtAuthOptions =
        new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions().setAlgorithm("HS256").setBuffer(secret));

    this.jwtAuth = JWTAuth.create(null, jwtAuthOptions);
  }

  @Override
  public UserContext authenticate(RoutingContext ctx) throws AuthenticationException {
    final String authHeader = ctx.request().getHeader(AUTHORIZATION_HEADER);

    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      throw new AuthenticationException("缺少有效的Authorization头");
    }

    final String token = authHeader.substring(BEARER_PREFIX.length());

    try {
      // 验证JWT token
      JsonObject payload = jwtAuth.authenticate(new TokenCredentials(token)).result().principal();

      // 检查过期时间（支持5分钟偏移）
      long exp = payload.getLong("exp", 0L);
      long now = Instant.now().getEpochSecond();
      long expWithOffset = exp + (EXPIRE_OFFSET_MINUTES * 60);

      if (now > expWithOffset) {
        throw new AuthenticationException("Token已过期");
      }

      // 从payload中提取用户ID
      String userId = payload.getString("sub");
      if (userId == null || userId.trim().isEmpty()) {
        throw new AuthenticationException("Token中缺少用户ID");
      }

      logger.debug("JWT认证成功，用户ID: {}", userId);
      return new UserContext(userId);

    } catch (Exception e) {
      logger.debug("JWT认证失败: {}", e.getMessage());
      throw new AuthenticationException("Token验证失败: " + e.getMessage());
    }
  }
}
