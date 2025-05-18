package com.vertx.template.service;

import com.vertx.template.model.User;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;

public class UserServiceImpl implements UserService {
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
