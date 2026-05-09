package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TransactionInput {

  private String idempotencyKey;
  private TransactionType type;
  private String domain;
  private String function;
  private String from;
  private String to;
  private Map<String, Object> data;
  private String abiReference;
  private JsonNode abi;
  private String bytecode;
  private List<String> dependsOn;

  public String getIdempotencyKey() { return idempotencyKey; }
  public TransactionType getType() { return type; }
  public String getDomain() { return domain; }
  public String getFunction() { return function; }
  public String getFrom() { return from; }
  public String getTo() { return to; }
  public Map<String, Object> getData() { return data; }
  public String getAbiReference() { return abiReference; }
  public JsonNode getAbi() { return abi; }
  public String getBytecode() { return bytecode; }
  public List<String> getDependsOn() { return dependsOn; }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final TransactionInput tx = new TransactionInput();

    public Builder idempotencyKey(String v) { tx.idempotencyKey = v; return this; }
    public Builder type(TransactionType v) { tx.type = v; return this; }
    public Builder domain(String v) { tx.domain = v; return this; }
    public Builder function(String v) { tx.function = v; return this; }
    public Builder from(String v) { tx.from = v; return this; }
    public Builder to(String v) { tx.to = v; return this; }
    @SuppressWarnings("unchecked")
    public Builder data(Map<String, ?> v) { tx.data = (Map<String, Object>) v; return this; }
    public Builder abiReference(String v) { tx.abiReference = v; return this; }
    public Builder abi(JsonNode v) { tx.abi = v; return this; }
    public Builder bytecode(String v) { tx.bytecode = v; return this; }
    public Builder dependsOn(List<String> v) { tx.dependsOn = v; return this; }

    public TransactionInput build() {
      if (tx.type == null) {
        throw new IllegalStateException("TransactionInput.type is required");
      }
      if (tx.from == null || tx.from.isBlank()) {
        throw new IllegalStateException("TransactionInput.from is required");
      }
      return tx;
    }
  }
}
