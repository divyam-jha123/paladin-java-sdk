package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ActiveFilter {
  ACTIVE("active"),
  INACTIVE("inactive"),
  ANY("any");

  private final String wire;

  ActiveFilter(String wire) {
    this.wire = wire;
  }

  @JsonValue
  public String wire() {
    return wire;
  }
}
