package com.vertx.template.service.impl;

import com.google.inject.Inject;
import com.vertx.template.model.User;
import com.vertx.template.service.UserService;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;

public class UserServiceImpl implements UserService {

  @Inject
  public UserServiceImpl() {
    // 无参构造函数，便于Guice创建实例
  }

  @Override
  public Future<List<User>> getUsers() {
    List<User> users = new ArrayList<>();
    users.add(new User("1", "Alice"));
    users.add(new User("2", "Bob"));
    return Future.succeededFuture(users);
  }

  @Override
  public Future<User> getUserById(String id) {
    return Future.succeededFuture(new User(id, "User-" + id));
  }
}
