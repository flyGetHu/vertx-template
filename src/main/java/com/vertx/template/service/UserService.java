package com.vertx.template.service;

import com.google.inject.ImplementedBy;
import com.vertx.template.model.User;
import com.vertx.template.service.impl.UserServiceImpl;
import io.vertx.core.Future;
import java.util.List;

@ImplementedBy(UserServiceImpl.class)
public interface UserService {
  Future<List<User>> getUsers();

  Future<User> getUserById(String id);
}
