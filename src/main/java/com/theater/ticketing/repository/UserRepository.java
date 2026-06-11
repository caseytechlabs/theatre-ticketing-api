package com.theater.ticketing.repository;

import com.theater.ticketing.model.User;
import com.theater.ticketing.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    // Three Redis structures per user:
    //   theater:user:<id>              → Hash  (full user data, keyed by UUID)
    //   theater:user:username:<name>   → String (UUID lookup index by username)
    //   theater:users                  → Set   (all user IDs for findAll/findByRole)
    private static final String HASH_PREFIX = "theater:user:";
    private static final String NAME_PREFIX = "theater:user:username:";
    private static final String ALL_SET     = "theater:users";

    private final StringRedisTemplate redisTemplate;

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

    public Optional<User> findById(String userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(HASH_PREFIX + userId);
        return entries.isEmpty() ? Optional.empty() : Optional.ofNullable(map(entries));
    }

    public Optional<User> findByUsername(String username) {
        // Two-hop lookup: username → UUID (String key), then UUID → User (Hash)
        String userId = redisTemplate.opsForValue().get(NAME_PREFIX + username);
        if (userId == null) return Optional.empty();
        return findById(userId);
    }

    public List<User> findAll() {
        Set<String> ids = redisTemplate.opsForSet().members(ALL_SET);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return ids.stream().map(this::findById)
                .filter(Optional::isPresent).map(Optional::get)
                .toList();
    }

    public List<User> findByRole(UserRole role) {
        return findAll().stream()
                .filter(u -> u.getRole() == role)
                .toList();
    }

    public void delete(String userId) {
        findById(userId).ifPresent(user -> {
            redisTemplate.delete(NAME_PREFIX + user.getUsername());
            redisTemplate.opsForSet().remove(ALL_SET, userId);
        });
        redisTemplate.delete(HASH_PREFIX + userId);
    }

    public boolean existsByUsername(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(NAME_PREFIX + username));
    }

    private User map(Map<Object, Object> e) {
        String roleStr = (String) e.get("role");
        UserRole role;
        try {
            role = UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return User.builder()
                .id((String) e.get("id"))
                .username((String) e.get("username"))
                .email((String) e.get("email"))
                .passwordHash((String) e.get("passwordHash"))
                .role(role)
                .createdAt(RedisUtils.parseInstant((String) e.get("createdAt")))
                .build();
    }
}
