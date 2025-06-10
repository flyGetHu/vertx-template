package com.vertx.template.service.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.model.dto.UserDto;
import com.vertx.template.model.entity.User;
import com.vertx.template.repository.UserRepository;
import com.vertx.template.service.UserService;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 用户服务实现类，处理用户相关的业务逻辑 */
@Singleton
public class UserServiceImpl implements UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
  private final UserRepository userRepository;

  @Inject
  public UserServiceImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
    logger.info(
        "UserServiceImpl initialized with repository: {}", userRepository.getClass().getName());
  }

  // 异步方法实现（向后兼容）
  @Override
  public List<UserDto> getUsers() {
    logger.debug("异步获取用户DTO列表");
    List<UserDto> users = new ArrayList<>();
    users.add(new UserDto("1", "Alice"));
    users.add(new UserDto("2", "Bob"));
    return users;
  }

  @Override
  public UserDto getUserById(String id) {
    logger.debug("异步根据ID获取用户DTO: {}", id);
    return new UserDto(id, "User-" + id);
  }

  // 同步方法实现（供Controller调用）
  @Override
  public List<User> getAllUsers(Boolean activeOnly) {
    logger.debug("获取用户列表，activeOnly: {}", activeOnly);

    if (Boolean.TRUE.equals(activeOnly)) {
      logger.debug("查询活跃用户");
      return userRepository.findActiveUsers();
    } else {
      logger.debug("查询所有用户");
      return userRepository.findAll();
    }
  }

  @Override
  public User getUserById(Long id) {
    logger.debug("根据ID获取用户: {}", id);

    if (id == null) {
      throw new IllegalArgumentException("用户ID不能为空");
    }

    User user = userRepository.findById(id);
    if (user == null) {
      throw new RuntimeException("用户不存在: " + id);
    }

    return user;
  }

  @Override
  public User createUser(User user) {
    logger.debug("创建用户: {}", user.getUsername());

    if (user == null) {
      throw new IllegalArgumentException("用户对象不能为空");
    }

    // 业务逻辑：检查用户名是否已存在
    if (user.getUsername() != null) {
      User existingUser = userRepository.findByUsername(user.getUsername());
      if (existingUser != null) {
        throw new RuntimeException("用户名已存在: " + user.getUsername());
      }
    }

    // 业务逻辑：检查邮箱是否已存在
    if (user.getEmail() != null) {
      User existingUser = userRepository.findByEmail(user.getEmail());
      if (existingUser != null) {
        throw new RuntimeException("邮箱已存在: " + user.getEmail());
      }
    }

    // 设置创建时间
    user.setCreatedAt(LocalDateTime.now());
    user.setUpdatedAt(LocalDateTime.now());
    user.setActive(true); // 新用户默认为活跃状态

    return userRepository.save(user);
  }

  @Override
  public User updateUser(Long id, User user) {
    logger.debug("更新用户: {}", id);

    if (id == null) {
      throw new IllegalArgumentException("用户ID不能为空");
    }

    if (user == null) {
      throw new IllegalArgumentException("用户对象不能为空");
    }

    // 业务逻辑：检查用户是否存在
    User existingUser = userRepository.findById(id);
    if (existingUser == null) {
      throw new RuntimeException("用户不存在: " + id);
    }

    // 业务逻辑：如果更新用户名，检查是否与其他用户冲突
    if (user.getUsername() != null && !user.getUsername().equals(existingUser.getUsername())) {
      User userWithSameName = userRepository.findByUsername(user.getUsername());
      if (userWithSameName != null && !userWithSameName.getId().equals(id)) {
        throw new RuntimeException("用户名已存在: " + user.getUsername());
      }
    }

    // 业务逻辑：如果更新邮箱，检查是否与其他用户冲突
    if (user.getEmail() != null && !user.getEmail().equals(existingUser.getEmail())) {
      User userWithSameEmail = userRepository.findByEmail(user.getEmail());
      if (userWithSameEmail != null && !userWithSameEmail.getId().equals(id)) {
        throw new RuntimeException("邮箱已存在: " + user.getEmail());
      }
    }

    // 设置更新时间
    user.setUpdatedAt(LocalDateTime.now());

    return userRepository.update(id, user);
  }

  @Override
  public Boolean deleteUser(Long id) {
    logger.debug("删除用户: {}", id);

    if (id == null) {
      throw new IllegalArgumentException("用户ID不能为空");
    }

    // 业务逻辑：检查用户是否存在
    User existingUser = userRepository.findById(id);
    if (existingUser == null) {
      throw new RuntimeException("用户不存在: " + id);
    }

    // 可以添加业务逻辑：检查是否可以删除（例如，管理员用户不能删除）

    return userRepository.deleteById(id);
  }

  @Override
  public User findByUsername(String username) {
    logger.debug("根据用户名查找用户: {}", username);

    if (username == null || username.trim().isEmpty()) {
      throw new IllegalArgumentException("用户名不能为空");
    }

    return userRepository.findByUsername(username);
  }

  @Override
  public JsonObject getUserProfile(String userId) {
    logger.debug("获取用户profile信息: {}", userId);

    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("用户ID不能为空");
    }

    // 这里可以添加更复杂的业务逻辑，比如获取用户详细信息、权限信息等
    return new JsonObject()
        .put("message", "获取用户信息成功")
        .put("userId", userId)
        .put("timestamp", System.currentTimeMillis());
  }

  @Override
  public JsonObject updateUserProfile(String userId, JsonObject updateData) {
    logger.debug("更新用户profile信息: {}", userId);

    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("用户ID不能为空");
    }

    if (updateData == null) {
      throw new IllegalArgumentException("更新数据不能为空");
    }

    // 这里可以添加更复杂的业务逻辑，比如数据验证、权限检查等

    return new JsonObject()
        .put("message", "用户信息更新成功")
        .put("userId", userId)
        .put("updateData", updateData)
        .put("timestamp", System.currentTimeMillis());
  }
}
