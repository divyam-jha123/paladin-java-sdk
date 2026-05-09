package io.lfdt.paladin.sdk.ws;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SubscriptionType {
  RECEIPTS("receipts"),
  BLOCKCHAIN_EVENTS("blockchainevents"),
  MESSAGES("messages");

  private final String wire;

  SubscriptionType(String wire) {
    this.wire = wire;
  }

  @JsonValue
  public String wire() {
    return wire;
  }
}
