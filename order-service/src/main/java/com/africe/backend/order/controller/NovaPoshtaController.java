package com.africe.backend.order.controller;

import com.africe.backend.common.dto.NovaCityResponse;
import com.africe.backend.common.dto.NovaWarehouseResponse;
import com.africe.backend.order.service.NovaPoshtaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders/nova-poshta")
@RequiredArgsConstructor
public class NovaPoshtaController {

    private final NovaPoshtaClient novaPoshtaClient;

    @GetMapping("/cities")
    public List<NovaCityResponse> searchCities(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        return novaPoshtaClient.searchCities(q, limit);
    }

    @GetMapping("/warehouses")
    public List<NovaWarehouseResponse> getWarehouses(
            @RequestParam String cityRef,
            @RequestParam(defaultValue = "50") int limit) {
        return novaPoshtaClient.getWarehouses(cityRef, limit);
    }
}
