package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreparedTransaction(
    String id,
    String domain,
    String to,
    JsonNode transaction,
    JsonNode states,
    JsonNode metadata) {}
