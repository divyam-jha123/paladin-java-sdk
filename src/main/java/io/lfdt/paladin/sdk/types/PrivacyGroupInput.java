package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PrivacyGroupInput {

  private String domain;
  private List<String> members = new ArrayList<>();
  private String name;
  private Map<String, String> configuration;
  private Map<String, String> properties;

  public String getDomain() { return domain; }
  public List<String> getMembers() { return members; }
  public String getName() { return name; }
  public Map<String, String> getConfiguration() { return configuration; }
  public Map<String, String> getProperties() { return properties; }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final PrivacyGroupInput p = new PrivacyGroupInput();

    public Builder domain(String v) { p.domain = v; return this; }
    public Builder members(List<String> v) { p.members = v; return this; }
    public Builder name(String v) { p.name = v; return this; }
    public Builder configuration(Map<String, String> v) { p.configuration = v; return this; }
    public Builder properties(Map<String, String> v) { p.properties = v; return this; }

    public PrivacyGroupInput build() {
      if (p.domain == null || p.domain.isBlank()) {
        throw new IllegalStateException("PrivacyGroupInput.domain is required");
      }
      if (p.members == null || p.members.isEmpty()) {
        throw new IllegalStateException("PrivacyGroupInput.members must not be empty");
      }
      return p;
    }
  }
}
