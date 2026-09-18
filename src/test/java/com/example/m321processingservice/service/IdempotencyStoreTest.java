package com.example.m321processingservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyStoreTest {

    @Test
    void firstOccurrenceOfAnEventIdIsNotADuplicate() {
        IdempotencyStore store = new IdempotencyStore();

        assertThat(store.isDuplicate("event-1")).isFalse();
    }

    @Test
    void secondOccurrenceOfTheSameEventIdIsADuplicate() {
        IdempotencyStore store = new IdempotencyStore();

        store.isDuplicate("event-1");

        assertThat(store.isDuplicate("event-1")).isTrue();
    }
}
