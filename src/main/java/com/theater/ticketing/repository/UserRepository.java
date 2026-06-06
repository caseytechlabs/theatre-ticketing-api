package com.theater.ticketing.repository;

import com.theater.ticketing.model.User;
import com.theater.ticketing.model.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String userId);
    Optional<User> findByUsername(String username);
    List<User> findAll();
    List<User> findByRole(UserRole role);
    void delete(String userId);
    boolean existsByUsername(String username);
}
