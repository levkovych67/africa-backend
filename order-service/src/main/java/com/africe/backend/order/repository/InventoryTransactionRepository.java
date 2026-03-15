package com.africe.backend.order.repository;

import com.africe.backend.common.model.InventoryTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventoryTransactionRepository extends MongoRepository<InventoryTransaction, String> {
}
