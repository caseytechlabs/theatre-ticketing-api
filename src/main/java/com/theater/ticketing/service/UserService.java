package com.theater.ticketing.service;

import com.theater.ticketing.dto.request.CreateUserRequest;
import com.theater.ticketing.dto.response.UserResponse;
import com.theater.ticketing.model.UserRole;

import java.util.List;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
    UserResponse getUser(String userId);
    List<UserResponse> getAllUsers();
    List<UserResponse> getUsersByRole(UserRole role);
    void deleteUser(String userId);
}
