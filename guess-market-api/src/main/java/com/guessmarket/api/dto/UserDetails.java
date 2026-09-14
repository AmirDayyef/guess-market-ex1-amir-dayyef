package com.guessmarket.api.dto;

import java.util.List;

public record UserDetails(
        UserSummary summary,
        List<UserEventDetails> events) {

    public UserDetails {
        events = List.copyOf(events);
    }
}
