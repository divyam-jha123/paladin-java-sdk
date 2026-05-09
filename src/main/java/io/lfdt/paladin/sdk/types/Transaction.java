package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Transaction(
    String id,
    String created,
    String idempotencyKey,
    TransactionType type,
    String domain,
    String function,
    String from,
    String to,
    Map<String, Object> data,
    String abiReference,
    String submitMode) {}
