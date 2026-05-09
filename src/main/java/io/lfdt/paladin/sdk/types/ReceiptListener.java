package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ReceiptListener {

  private String name;
  private Filters filters;
  private Options options;

  public String getName() { return name; }
  public Filters getFilters() { return filters; }
  public Options getOptions() { return options; }

  public static Builder builder() {
    return new Builder();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Filters(Long sequenceAbove, TransactionType type, String domain) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Options(Boolean domainReceipts, String incompleteStateReceiptBehavior) {}

  public static final class Builder {
    private final ReceiptListener l = new ReceiptListener();

    public Builder name(String v) { l.name = v; return this; }
    public Builder filters(Filters v) { l.filters = v; return this; }
    public Builder options(Options v) { l.options = v; return this; }

    public ReceiptListener build() {
      if (l.name == null || l.name.isBlank()) {
        throw new IllegalStateException("ReceiptListener.name is required");
      }
      return l;
    }
  }
}
