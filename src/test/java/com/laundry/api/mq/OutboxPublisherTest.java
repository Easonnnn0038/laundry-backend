package com.laundry.api.mq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxPublisherTest {
    @Test
    void retryBackoffIsBounded() {
        assertEquals(5, OutboxPublisher.nextDelaySeconds(1));
        assertEquals(10, OutboxPublisher.nextDelaySeconds(2));
        assertEquals(300, OutboxPublisher.nextDelaySeconds(8));
        assertEquals(300, OutboxPublisher.nextDelaySeconds(100));
    }
}
