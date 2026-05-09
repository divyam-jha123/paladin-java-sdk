package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum TransactionType {
  @JsonProperty("public") PUBLIC,
  @JsonProperty("private") PRIVATE
}
