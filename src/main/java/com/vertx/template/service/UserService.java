package com.vertx.template.service;

import com.vertx.template.model.User;
import io.vertx.core.Future;
import java.util.List;

public interface UserService {
  Future<List<User>> getUsers();

  Future<User> getUserById(String id);
}
