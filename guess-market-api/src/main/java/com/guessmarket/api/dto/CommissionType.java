package com.guessmarket.api.dto;

public enum CommissionType {
    ON_PURCHASE("On purchase"),
    ON_CLOSE("On close");

    private final String displayName;

    CommissionType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

