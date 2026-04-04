package com.africe.backend.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueDayDto(
        LocalDate date,
        BigDecimal revenue
) {}
