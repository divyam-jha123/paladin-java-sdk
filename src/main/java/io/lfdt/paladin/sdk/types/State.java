package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record State(
    String id,
    String created,
    String domain,
    String schema,
    String contractAddress,
    JsonNode data) {}
