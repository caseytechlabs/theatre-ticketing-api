package com.theater.ticketing.service.impl;

import com.theater.ticketing.dto.request.CreateUserRequest;
import com.theater.ticketing.dto.response.UserResponse;
import com.theater.ticketing.exception.UserNotFoundException;
import com.theater.ticketing.exception.UsernameAlreadyExistsException;
import com.theater.ticketing.model.User;
import com.theater.ticketing.model.UserRole;
import com.theater.ticketing.repository.UserRepository;
import com.theater.ticketing.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .createdAt(Instant.now())
                .build();
        userRepository.save(user);
        return toResponse(user);
    }

    @Override
    public UserResponse getUser(String userId) {
        return userRepository.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public void deleteUser(String userId) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        userRepository.delete(userId);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
