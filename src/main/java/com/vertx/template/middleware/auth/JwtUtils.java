package com.vertx.template.middleware.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JWT工具类，用于生成和验证JWT token */
@Singleton
public class JwtUtils {
  private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);
  private final JWTAuth jwtAuth;
  private final int defaultExpireSeconds;

  @Inject
  public JwtUtils(JsonObject config) {
    // 从配置中获取JWT配置
    JsonObject jwtConfig = config.getJsonObject("jwt", new JsonObject());

    String secret = jwtConfig.getString("secret", "default-secret-key-change-in-production");
    String algorithm = jwtConfig.getString("algorithm", "HS256");
    this.defaultExpireSeconds = jwtConfig.getInteger("expire_seconds", 3600);

    JWTAuthOptions jwtAuthOptions =
        new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions().setAlgorithm(algorithm).setBuffer(secret));

    this.jwtAuth = JWTAuth.create(null, jwtAuthOptions);
    logger.info("JWT工具类初始化完成，算法: {}, 默认过期时间: {}秒", algorithm, defaultExpireSeconds);
  }

  /**
   * 生成JWT token
   *
   * @param userId 用户ID
   * @return JWT token
   */
  public String generateToken(String userId) {
    return generateToken(userId, defaultExpireSeconds);
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
