package io.lfdt.paladin.sdk.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcError(int code, String message, JsonNode data) {}
