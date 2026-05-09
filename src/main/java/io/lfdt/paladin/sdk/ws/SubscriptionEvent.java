package io.lfdt.paladin.sdk.ws;

import com.fasterxml.jackson.databind.JsonNode;

public record SubscriptionEvent(
    String method,
    String subscriptionId,
    String subscriptionName,
    JsonNode result) {}
