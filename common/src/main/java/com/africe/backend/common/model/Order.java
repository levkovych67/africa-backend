package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("orders")
public class Order {

    @Id
    private String id;

    private String firstName;
    private String lastName;
    @Indexed
    private String email;
    private String phone;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    @Indexed
    private OrderStatus status;
    private ShippingDetails shippingDetails;
    private String comment;

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;
}
