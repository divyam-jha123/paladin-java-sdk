package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionReceipt(
    long blockNumber,
    String id,
    long sequence,
    boolean success,
    String transactionHash,
    String source,
    String domain,
    String contractAddress,
    JsonNode states,
    JsonNode domainReceipt,
    String domainReceiptError,
    String failureMessage) {}
