package com.vertx.template.repository.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vertx.template.config.DatabaseConfig;
import com.vertx.template.model.entity.User;
import com.vertx.template.repository.UserRepository;

import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

/**
 * 用户仓库接口实现
 */
@Singleton
public class UserRepositoryImpl implements UserRepository {
  private static final Logger logger = LoggerFactory.getLogger(UserRepositoryImpl.class);
  private final Pool pool;

  @Inject
  public UserRepositoryImpl(DatabaseConfig databaseConfig) {
    this.pool = databaseConfig.getPool();
  }

  @Override
  public List<User> findAll() {
    final String sql = "SELECT * FROM users";

    final RowSet<Row> rows = Future.await(pool.query(sql)
        .execute());

    final List<User> users = new ArrayList<>();
    for (Row row : rows) {
      users.add(User.fromRow(row));
    }
    return users;
  }

  @Override
  public User findById(Long id) {
    final String sql = "SELECT * FROM users WHERE id = ?";

    final RowSet<Row> rows = Future.await(pool.preparedQuery(sql)
        .execute(Tuple.of(id)));

    return rows.size() > 0 ? User.fromRow(rows.iterator().next()) : null;
  }

  @Override
  public User save(User user) {

    final LocalDateTime now = LocalDateTime.now();
    user.setCreatedAt(now);
    user.setUpdatedAt(now);

    final String sql = "INSERT INTO users (username, password, email, created_at, updated_at, active) "
        + "VALUES (?, ?, ?, ?, ?, ?)";

    Future.await(pool.preparedQuery(sql)
        .execute(Tuple.of(
            user.getUsername(),
            user.getPassword(),
            user.getEmail(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.isActive())));
    // 通过查询获取最后插入的ID
    final RowSet<Row> idResult = Future.await(pool.query("SELECT LAST_INSERT_ID()")
        .execute());

    if (idResult.size() > 0) {
      final Long id = idResult.iterator().next().getLong(0);
      user.setId(id);
      logger.debug("用户创建成功: {}", id);
    } else {
      logger.error("获取最后插入ID失败");
    }
    return user;
  }

  @Override
  public User update(Long id, User user) {
    user.setId(id);
    user.setUpdatedAt(LocalDateTime.now());

    final String sql = "UPDATE users SET username = ?, email = ?, active = ?, updated_at = ? WHERE id = ?";

    final RowSet<Row> rows = Future.await(pool.preparedQuery(sql)
        .execute(Tuple.of(
            user.getUsername(),
            user.getEmail(),
            user.isActive(),
            user.getUpdatedAt(),
            id)));

    return rows.size() > 0 ? user : null;
  }

  @Override
  public Boolean deleteById(Long id) {
    final String sql = "DELETE FROM users WHERE id = ?";

    final RowSet<Row> rows = Future.await(pool.preparedQuery(sql)
        .execute(Tuple.of(id)));

    return rows.size() > 0;
  }

  @Override
  public User findByUsername(String username) {
    final String sql = "SELECT * FROM users WHERE username = ?";

    final RowSet<Row> rows = Future.await(pool.preparedQuery(sql)
        .execute(Tuple.of(username)));

    return rows.size() > 0 ? User.fromRow(rows.iterator().next()) : null;
  }

  @Override
  public User findByEmail(String email) {
    final String sql = "SELECT * FROM users WHERE email = ?";

    final RowSet<Row> rows = Future.await(pool.preparedQuery(sql)
        .execute(Tuple.of(email)));

    return rows.size() > 0 ? User.fromRow(rows.iterator().next()) : null;
  }

  @Override
  public List<User> findActiveUsers() {
    final String sql = "SELECT * FROM users WHERE active = true";

    final RowSet<Row> rows = Future.await(pool.query(sql)
        .execute());

    final List<User> users = new ArrayList<>();
    for (Row row : rows) {
      users.add(User.fromRow(row));
    }
    return users;
  }

  @Override
  public Boolean updatePassword(Long id, String newPassword) {
    Objects.requireNonNull(newPassword, "新密码不能为null");

    final String sql = "UPDATE users SET password = ?, updated_at = ? WHERE id = ?";

    final RowSet<Row> rows = Future.await(pool.preparedQuery(sql)
        .execute(Tuple.of(newPassword, LocalDateTime.now(), id)));

    return rows.size() > 0;
  }
}
