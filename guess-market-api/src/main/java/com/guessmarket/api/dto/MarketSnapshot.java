package com.guessmarket.api.dto;

import java.util.List;

public record MarketSnapshot(
        List<EventSummary> events,
        List<PublicUser> users,
        UserDetails account,
        List<AccountEntry> accountEntries) {
    public record PublicUser(String name, double balance, boolean marketMaker) {
    }
}
