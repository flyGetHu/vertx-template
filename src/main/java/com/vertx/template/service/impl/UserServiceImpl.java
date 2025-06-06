package com.vertx.template.service.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.model.dto.UserDto;
import com.vertx.template.service.UserService;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class UserServiceImpl implements UserService {

  @Inject
  public UserServiceImpl() {
    // 无参构造函数，便于依赖注入创建实例
  }

  @Override
  public Future<List<UserDto>> getUsers() {
    List<UserDto> users = new ArrayList<>();
    users.add(new UserDto("1", "Alice"));
    users.add(new UserDto("2", "Bob"));
    return Future.succeededFuture(users);
  }

  @Override
  public Future<UserDto> getUserById(String id) {
    return Future.succeededFuture(new UserDto(id, "User-" + id));
  }
}
