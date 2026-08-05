package com.example.attendancesystem.repository;

import com.example.attendancesystem.entity.Role;
import com.example.attendancesystem.entity.User;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>,
        JpaSpecificationExecutor<User> {

    Optional<User> findByUserIdIgnoreCase(String userId);

    Optional<User> findByEmailIgnoreCase(String email);

    Page<User> findByRoleIn(List<Role> roles, Pageable pageable);

    Optional<User> findByUserIdIgnoreCaseAndActiveTrue(String userId);

    Optional<User> findByEmailIgnoreCaseAndActiveTrue(String email);

    boolean existsByUserIdIgnoreCase(String userId);

    Page<User> findByRole(Role role, Pageable pageable);

    Page<User> findByActive(boolean active, Pageable pageable);

    Page<User> findAllByOrderByIdDesc(Pageable pageable);

    long countByRole(Role role);

    long countByRoleAndActiveTrue(Role role);

    long countByActive(boolean active);
}
