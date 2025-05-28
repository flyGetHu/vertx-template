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
  private final JWTAuth jwtAuth;
  private final int expireOffsetMinutes;

  @Inject
  public JwtAuthenticator(JsonObject config) {
    // 从配置中获取JWT配置
    JsonObject jwtConfig = config.getJsonObject("jwt", new JsonObject());

    String secret = jwtConfig.getString("secret", "NvkY5HqIQAJf1eZZzxDp52MIf81aj4Ow");
    String algorithm = jwtConfig.getString("algorithm", "HS256");
    this.expireOffsetMinutes = jwtConfig.getInteger("expire_offset_minutes", 5);

    JWTAuthOptions jwtAuthOptions =
        new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions().setAlgorithm(algorithm).setBuffer(secret));

    this.jwtAuth = JWTAuth.create(null, jwtAuthOptions);

    logger.info("JWT认证器初始化完成，算法: {}, 过期偏移: {}分钟", algorithm, expireOffsetMinutes);
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
      jwtAuth.authenticate(new TokenCredentials(token)).result().principal();

      // 手动解析JWT payload来获取完整的claims信息
      String[] tokenParts = token.split("\\.");
      if (tokenParts.length != 3) {
        throw new AuthenticationException("无效的JWT token格式");
      }

      // 解码payload部分
      String payloadJson = new String(java.util.Base64.getDecoder().decode(tokenParts[1]));
      JsonObject payload = new JsonObject(payloadJson);

      // 检查过期时间（支持配置的偏移时间）
      long exp = payload.getLong("exp", 0L);
      long now = Instant.now().getEpochSecond();
      long expWithOffset = exp + (expireOffsetMinutes * 60);

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
