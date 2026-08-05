package com.example.attendancesystem.security;

import com.example.attendancesystem.entity.User;
import com.example.attendancesystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) {

        String normalizedUserId = userId.trim().toUpperCase();

        User user = userRepository
                .findByUserIdIgnoreCaseAndActiveTrue(normalizedUserId)
                .or(() -> userRepository.findByEmailIgnoreCaseAndActiveTrue(normalizedUserId))
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + userId)
                );

        return new org.springframework.security.core.userdetails.User(
                user.getUserId(),
                user.getPassword(),
                user.isActive(),
                true,
                true,
                true,
                List.of(user.getRole())
        );
    }
}
