package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonValue;

public enum StateStatus {
  AVAILABLE("available"),
  ALL("all"),
  CONFIRMED("confirmed"),
  UNCONFIRMED("unconfirmed"),
  SPENT("spent"),
  LOCKED("locked");

  private final String wire;

  StateStatus(String wire) {
    this.wire = wire;
  }

  @JsonValue
  public String wire() {
    return wire;
  }
}
