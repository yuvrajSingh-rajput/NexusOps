package com.nexusops.shared.events;

public interface EventPublisher {
    void publish(EventEnvelope<?> envelope);
}

