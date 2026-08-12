package com.guessmarket.api.dto;

import java.util.List;

public record EventSummary(
        int id,
        String name,
        String description,
        int commissionPercentage,
        CommissionType commissionType,
        List<String> optionNames,
        EventStatus status) {

    public EventSummary {
        optionNames = List.copyOf(optionNames);
    }
}

