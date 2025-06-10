package com.vertx.template.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vertx.template.model.entity.User;
import com.vertx.template.repository.UserRepository;
import com.vertx.template.service.impl.UserServiceImpl;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * UserService单元测试
 * 测试分层架构修复后的Service层功能
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserServiceImpl(userRepository);
  }

  @Test
  void testGetAllUsers_WithActiveOnly() {
    // 准备测试数据
    User activeUser1 = createTestUser(1L, "alice", "alice@test.com", true);
    User activeUser2 = createTestUser(2L, "bob", "bob@test.com", true);
    List<User> activeUsers = Arrays.asList(activeUser1, activeUser2);

    // 配置Mock
    when(userRepository.findActiveUsers()).thenReturn(activeUsers);

    // 执行测试
    List<User> result = userService.getAllUsers(true);

    // 验证结果
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("alice", result.get(0).getUsername());
    assertEquals("bob", result.get(1).getUsername());

    // 验证Repository调用
    verify(userRepository).findActiveUsers();
    verify(userRepository, never()).findAll();
  }

  @Test
  void testGetAllUsers_WithoutActiveOnly() {
    // 准备测试数据
    User user1 = createTestUser(1L, "alice", "alice@test.com", true);
    User user2 = createTestUser(2L, "bob", "bob@test.com", false);
    List<User> allUsers = Arrays.asList(user1, user2);

    // 配置Mock
    when(userRepository.findAll()).thenReturn(allUsers);

    // 执行测试
    List<User> result = userService.getAllUsers(false);

    // 验证结果
    assertNotNull(result);
    assertEquals(2, result.size());

    // 验证Repository调用
    verify(userRepository).findAll();
    verify(userRepository, never()).findActiveUsers();
  }

  @Test
  void testGetUserById_Success() {
    // 准备测试数据
    Long userId = 1L;
    User user = createTestUser(userId, "alice", "alice@test.com", true);

    // 配置Mock
    when(userRepository.findById(userId)).thenReturn(user);

    // 执行测试
    User result = userService.getUserById(userId);

    // 验证结果
    assertNotNull(result);
    assertEquals(userId, result.getId());
    assertEquals("alice", result.getUsername());

    // 验证Repository调用
    verify(userRepository).findById(userId);
  }

  @Test
  void testGetUserById_UserNotFound() {
    // 准备测试数据
    Long userId = 999L;

    // 配置Mock
    when(userRepository.findById(userId)).thenReturn(null);

    // 执行测试并验证异常
    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
      userService.getUserById(userId);
    });

    assertEquals("用户不存在: " + userId, exception.getMessage());
    verify(userRepository).findById(userId);
  }

  @Test
  void testCreateUser_Success() {
    // 准备测试数据
    User inputUser = createTestUser(null, "newuser", "newuser@test.com", false);
    User savedUser = createTestUser(1L, "newuser", "newuser@test.com", true);

    // 配置Mock
    when(userRepository.findByUsername("newuser")).thenReturn(null);
    when(userRepository.findByEmail("newuser@test.com")).thenReturn(null);
    when(userRepository.save(any(User.class))).thenReturn(savedUser);

    // 执行测试
    User result = userService.createUser(inputUser);

    // 验证结果
    assertNotNull(result);
    assertEquals(1L, result.getId());
    assertEquals("newuser", result.getUsername());
    assertTrue(result.isActive());

    // 验证Repository调用
    verify(userRepository).findByUsername("newuser");
    verify(userRepository).findByEmail("newuser@test.com");
    verify(userRepository).save(any(User.class));
  }

  @Test
  void testCreateUser_UsernameExists() {
    // 准备测试数据
    User inputUser = createTestUser(null, "existinguser", "new@test.com", false);
    User existingUser = createTestUser(2L, "existinguser", "existing@test.com", true);

    // 配置Mock
    when(userRepository.findByUsername("existinguser")).thenReturn(existingUser);

    // 执行测试并验证异常
    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
      userService.createUser(inputUser);
    });

    assertEquals("用户名已存在: existinguser", exception.getMessage());
    verify(userRepository).findByUsername("existinguser");
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void testDeleteUser_Success() {
    // 准备测试数据
    Long userId = 1L;
    User existingUser = createTestUser(userId, "alice", "alice@test.com", true);

    // 配置Mock
    when(userRepository.findById(userId)).thenReturn(existingUser);
    when(userRepository.deleteById(userId)).thenReturn(true);

    // 执行测试
    Boolean result = userService.deleteUser(userId);

    // 验证结果
    assertTrue(result);

    // 验证Repository调用
    verify(userRepository).findById(userId);
    verify(userRepository).deleteById(userId);
  }

  @Test
  void testGetUserProfile() {
    // 执行测试
    JsonObject result = userService.getUserProfile("123");

    // 验证结果
    assertNotNull(result);
    assertEquals("获取用户信息成功", result.getString("message"));
    assertEquals("123", result.getString("userId"));
    assertTrue(result.containsKey("timestamp"));
  }

  @Test
  void testUpdateUserProfile() {
    // 准备测试数据
    JsonObject updateData = new JsonObject().put("name", "newname");

    // 执行测试
    JsonObject result = userService.updateUserProfile("123", updateData);

    // 验证结果
    assertNotNull(result);
    assertEquals("用户信息更新成功", result.getString("message"));
    assertEquals("123", result.getString("userId"));
    assertEquals(updateData, result.getJsonObject("updateData"));
    assertTrue(result.containsKey("timestamp"));
  }

  /**
   * 创建测试用户对象
   */
  private User createTestUser(Long id, String username, String email, boolean active) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setEmail(email);
    user.setPassword("password123");
    user.setActive(active);
    user.setCreatedAt(LocalDateTime.now());
    user.setUpdatedAt(LocalDateTime.now());
    return user;
  }
}
