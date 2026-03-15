package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("inventory_transactions")
public class InventoryTransaction {

    @Id
    private String id;

    private String productId;
    private String sku;
    private int change;
    private String orderId;
    private Instant createdAt;
}
