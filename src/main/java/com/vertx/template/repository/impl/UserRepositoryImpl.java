package com.vertx.template.repository.impl;

import com.vertx.template.config.DatabaseConfig;
import com.vertx.template.model.entity.User;
import com.vertx.template.repository.UserRepository;
import com.vertx.template.repository.common.AbstractBaseRepository;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 用户仓库接口实现 */
@Singleton
public class UserRepositoryImpl extends AbstractBaseRepository<User, Long>
    implements UserRepository {
  private static final Logger logger = LoggerFactory.getLogger(UserRepositoryImpl.class);

  @Inject
  public UserRepositoryImpl(DatabaseConfig databaseConfig) {
    super(databaseConfig.getPool());
  }

  // 基础CRUD操作已由AbstractBaseRepository提供默认实现

  @Override
  protected String buildInsertSql() {
    final List<String> columns = getInsertableColumns();
    final String columnNames = String.join(", ", columns);
    final String placeholders =
        columns.stream().map(c -> "?").collect(java.util.stream.Collectors.joining(", "));
    return "INSERT INTO " + tableName + " (" + columnNames + ") VALUES (" + placeholders + ")";
  }

  @Override
  protected Tuple buildInsertParams(User user) {
    final List<Object> params = new ArrayList<>();

    // 根据可插入字段的顺序添加参数
    final java.lang.reflect.Field[] fields = entityClass.getDeclaredFields();
    for (java.lang.reflect.Field field : fields) {
      final com.vertx.template.model.annotation.Column column =
          field.getAnnotation(com.vertx.template.model.annotation.Column.class);
      final com.vertx.template.model.annotation.Id id =
          field.getAnnotation(com.vertx.template.model.annotation.Id.class);

      // 排除主键字段（如果是自动生成的）和不可插入的字段
      if (id != null && id.generated()) {
        continue;
      }
      if (column != null && !column.insertable()) {
        continue;
      }

      try {
        field.setAccessible(true);
        params.add(field.get(user));
      } catch (Exception e) {
        logger.warn("Failed to get field value: {}", field.getName(), e);
      }
    }

    return Tuple.wrap(params);
  }

  @Override
  protected String buildUpdateSql() {
    final List<String> columns = getUpdatableColumns();
    final String setClause =
        columns.stream().map(c -> c + " = ?").collect(java.util.stream.Collectors.joining(", "));
    return "UPDATE " + tableName + " SET " + setClause + " WHERE id = ?";
  }

  @Override
  protected Tuple buildUpdateParams(User user) {
    final List<Object> params = new ArrayList<>();

    // 根据可更新字段的顺序添加参数
    final java.lang.reflect.Field[] fields = entityClass.getDeclaredFields();
    for (java.lang.reflect.Field field : fields) {
      final com.vertx.template.model.annotation.Column column =
          field.getAnnotation(com.vertx.template.model.annotation.Column.class);
      final com.vertx.template.model.annotation.Id id =
          field.getAnnotation(com.vertx.template.model.annotation.Id.class);

      // 排除主键字段和不可更新的字段
      if (id != null) {
        continue;
      }
      if (column != null && !column.updatable()) {
        continue;
      }

      try {
        field.setAccessible(true);
        params.add(field.get(user));
      } catch (Exception e) {
        logger.warn("Failed to get field value: {}", field.getName(), e);
      }
    }

    // 最后添加ID参数用于WHERE条件
    params.add(user.getId());

    return Tuple.wrap(params);
  }

  // 业务特有方法实现
  @Override
  public User findByUsername(String username) {
    final String sql = "SELECT * FROM users WHERE username = ?";
    final RowSet<Row> rows = Future.await(pool.preparedQuery(sql).execute(Tuple.of(username)));
    return rows.size() > 0 ? User.fromRow(rows.iterator().next()) : null;
  }

  @Override
  public User findByEmail(String email) {
    final String sql = "SELECT * FROM users WHERE email = ?";
    final RowSet<Row> rows = Future.await(pool.preparedQuery(sql).execute(Tuple.of(email)));
    return rows.size() > 0 ? User.fromRow(rows.iterator().next()) : null;
  }

  @Override
  public List<User> findActiveUsers() {
    final String sql = "SELECT * FROM users WHERE active = true";
    final RowSet<Row> rows = Future.await(pool.query(sql).execute());

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
    final RowSet<Row> rows =
        Future.await(
            pool.preparedQuery(sql).execute(Tuple.of(newPassword, LocalDateTime.now(), id)));

    return rows.rowCount() > 0;
  }
}
