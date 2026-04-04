package com.africe.backend.auth.repository;

import com.africe.backend.common.model.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByAdminId(String adminId);

    void deleteByToken(String token);
}
