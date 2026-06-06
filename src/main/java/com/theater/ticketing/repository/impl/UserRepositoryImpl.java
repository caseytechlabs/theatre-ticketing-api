package com.theater.ticketing.repository.impl;

import com.theater.ticketing.model.User;
import com.theater.ticketing.model.UserRole;
import com.theater.ticketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private static final String HASH_PREFIX  = "theater:user:";
    private static final String NAME_PREFIX  = "theater:user:username:";
    private static final String ALL_SET      = "theater:users";

    private final StringRedisTemplate redisTemplate;

    @Override
    public User save(User user) {
        String key = HASH_PREFIX + user.getId();
        Map<String, String> hash = new HashMap<>();
        hash.put("id",           user.getId());
        hash.put("username",     user.getUsername());
        hash.put("email",        user.getEmail());
        hash.put("passwordHash", user.getPasswordHash());
        hash.put("role",         user.getRole().name());
        if (user.getCreatedAt() != null) hash.put("createdAt", String.valueOf(user.getCreatedAt().toEpochMilli()));

        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.opsForValue().set(NAME_PREFIX + user.getUsername(), user.getId());
        redisTemplate.opsForSet().add(ALL_SET, user.getId());
        return user;
    }

    @Override
    public Optional<User> findById(String userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(HASH_PREFIX + userId);
        return entries.isEmpty() ? Optional.empty() : Optional.of(map(entries));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String userId = redisTemplate.opsForValue().get(NAME_PREFIX + username);
        if (userId == null) return Optional.empty();
        return findById(userId);
    }

    @Override
    public List<User> findAll() {
        Set<String> ids = redisTemplate.opsForSet().members(ALL_SET);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return ids.stream().map(this::findById)
                .filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findByRole(UserRole role) {
        return findAll().stream()
                .filter(u -> u.getRole() == role)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String userId) {
        findById(userId).ifPresent(user -> {
            redisTemplate.delete(NAME_PREFIX + user.getUsername());
            redisTemplate.opsForSet().remove(ALL_SET, userId);
        });
        redisTemplate.delete(HASH_PREFIX + userId);
    }

    @Override
    public boolean existsByUsername(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(NAME_PREFIX + username));
    }

    private User map(Map<Object, Object> e) {
        String millis = (String) e.get("createdAt");
        return User.builder()
                .id((String) e.get("id"))
                .username((String) e.get("username"))
                .email((String) e.get("email"))
                .passwordHash((String) e.get("passwordHash"))
                .role(UserRole.valueOf((String) e.get("role")))
                .createdAt(millis != null ? Instant.ofEpochMilli(Long.parseLong(millis)) : null)
                .build();
    }
}
