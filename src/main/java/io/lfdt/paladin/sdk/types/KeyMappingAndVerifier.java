package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeyMappingAndVerifier(
    String identifier,
    String keyHandle,
    List<KeyPath> path,
    KeyVerifier verifier,
    String wallet) {

  public record KeyPath(int index, String name) {}

  public record KeyVerifier(String verifier, String type, String algorithm) {}
}
