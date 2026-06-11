package com.theater.ticketing;

import com.theater.ticketing.model.User;
import com.theater.ticketing.model.UserRole;
import com.theater.ticketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seed("admin",   "admin@theater.com",   "admin123",   UserRole.ADMIN);
        seed("client1", "client1@theater.com", "client123",  UserRole.CLIENT);
        seed("client2", "client2@theater.com", "client123",  UserRole.CLIENT);
        log.info("Seeded default users (admin / client1 / client2)");
    }

    private void seed(String username, String email, String password, UserRole role) {
        if (userRepository.existsByUsername(username)) return;
        userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .createdAt(Instant.now())
                .build());
    }
}
