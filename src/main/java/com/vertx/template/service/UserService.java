package com.vertx.template.service;

import com.google.inject.ImplementedBy;
import com.vertx.template.model.dto.UserDto;
import com.vertx.template.model.entity.User;
import com.vertx.template.service.impl.UserServiceImpl;
import io.vertx.core.json.JsonObject;
import java.util.List;

/** 用户服务接口，定义用户相关的业务逻辑操作 */
@ImplementedBy(UserServiceImpl.class)
public interface UserService {

  // 异步方法（用于内部调用）
  List<UserDto> getUsers();

  UserDto getUserById(String id);

  // 同步方法（用于Controller调用）

  /**
   * 获取所有用户列表
   *
   * @param activeOnly 是否只查询活跃用户，可选参数
   * @return 用户列表
   */
  List<User> getAllUsers(Boolean activeOnly);

  /**
   * 根据ID获取用户
   *
   * @param id 用户ID
   * @return 用户对象，如果不存在则抛出异常
   */
  User getUserById(Long id);

  /**
   * 创建新用户
   *
   * @param user 用户对象，包含用户基本信息
   * @return 创建成功的用户对象（包含生成的ID）
   */
  User createUser(User user);

  /**
   * 更新用户信息
   *
   * @param id 用户ID
   * @param user 更新的用户信息
   * @return 更新后的用户对象
   */
  User updateUser(Long id, User user);

  /**
   * 删除用户
   *
   * @param id 用户ID
   * @return 删除结果，true表示删除成功
   */
  Boolean deleteUser(Long id);

  /**
   * 根据用户名查找用户
   *
   * @param username 用户名
   * @return 用户对象，如果不存在则返回null
   */
  User findByUsername(String username);

  /**
   * 获取用户详细信息（用于profile接口）
   *
   * @param userId 用户ID
   * @return 包含用户详细信息的JsonObject
   */
  JsonObject getUserProfile(String userId);

  /**
   * 更新用户Profile信息
   *
   * @param userId 用户ID
   * @param updateData 更新数据
   * @return 包含操作结果的JsonObject
   */
  JsonObject updateUserProfile(String userId, JsonObject updateData);
}
