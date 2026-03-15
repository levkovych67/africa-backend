package com.africe.backend.order.repository;

import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByEmailContainingIgnoreCase(String email, Pageable pageable);
}
