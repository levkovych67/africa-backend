package com.africe.backend.auth.repository;

import com.africe.backend.common.model.AdminUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AdminUserRepository extends MongoRepository<AdminUser, String> {

    Optional<AdminUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
