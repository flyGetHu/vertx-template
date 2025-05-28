package com.vertx.template.security;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JWT工具类，用于生成和验证JWT token */
@Singleton
public class JwtUtils {
  private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);
  private static final int DEFAULT_EXPIRE_SECONDS = 3600; // 默认1小时过期

  private final JWTAuth jwtAuth;

  @Inject
  public JwtUtils(JsonObject config) {
    String secret =
        config
            .getJsonObject("jwt", new JsonObject())
            .getString("secret", "default-secret-key-change-in-production");

    JWTAuthOptions jwtAuthOptions =
        new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions().setAlgorithm("HS256").setBuffer(secret));

    this.jwtAuth = JWTAuth.create(null, jwtAuthOptions);
    logger.info("JWT工具类初始化完成");
  }

  /**
   * 生成JWT token
   *
   * @param userId 用户ID
   * @return JWT token
   */
  public String generateToken(String userId) {
    return generateToken(userId, DEFAULT_EXPIRE_SECONDS);
  }

  /**
   * 生成JWT token
   *
   * @param userId 用户ID
   * @param expireSeconds 过期时间（秒）
   * @return JWT token
   */
  public String generateToken(String userId, int expireSeconds) {
    long now = Instant.now().getEpochSecond();
    long exp = now + expireSeconds;

    JsonObject claims = new JsonObject().put("sub", userId).put("iat", now).put("exp", exp);

    String token = jwtAuth.generateToken(claims);
    logger.debug("为用户 {} 生成JWT token，过期时间: {}", userId, exp);

    return token;
  }

  /**
   * 验证JWT token
   *
   * @param token JWT token
   * @return 用户ID，验证失败返回null
   */
  public String validateToken(String token) {
    try {
      JsonObject payload = jwtAuth.authenticate(new TokenCredentials(token)).result().principal();

      return payload.getString("sub");
    } catch (Exception e) {
      logger.debug("JWT token验证失败: {}", e.getMessage());
      return null;
    }
  }
}
