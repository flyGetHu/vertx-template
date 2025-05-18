package com.vertx.template.repository;

import com.vertx.template.model.entity.User;
import io.vertx.core.Future;

import java.util.List;

/**
 * 用户仓库接口，定义与用户相关的数据操作
 */
public interface UserRepository extends BaseRepository<User, Long> {

  /**
   * 根据用户名查找用户
   *
   * @param username 用户名
   * @return 用户对象Future
   */
  User findByUsername(String username);

  /**
   * 根据邮箱查找用户
   *
   * @param email 邮箱
   * @return 用户对象Future
   */
  User findByEmail(String email);

  /**
   * 查找活跃用户
   *
   * @return 活跃用户列表Future
   */
  List<User> findActiveUsers();

  /**
   * 更新用户密码
   *
   * @param id          用户ID
   * @param newPassword 新密码
   * @return 操作结果Future
   */
  Boolean updatePassword(Long id, String newPassword);
}
