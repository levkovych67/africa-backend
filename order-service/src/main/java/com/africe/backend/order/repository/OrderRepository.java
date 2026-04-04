package com.africe.backend.order.repository;

import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByIdAndAccessToken(String id, String accessToken);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String email, String firstName, String lastName, Pageable pageable);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant before);
}
