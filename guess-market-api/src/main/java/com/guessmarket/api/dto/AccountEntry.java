package com.guessmarket.api.dto;

public record AccountEntry(long sequence, String description, double change, double balance) {
}
