package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TransactionCall {

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
  private Object block;
  private String dataFormat;

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
  public Object getBlock() { return block; }
  public String getDataFormat() { return dataFormat; }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final TransactionCall c = new TransactionCall();

    public Builder type(TransactionType v) { c.type = v; return this; }
    public Builder domain(String v) { c.domain = v; return this; }
    public Builder function(String v) { c.function = v; return this; }
    public Builder from(String v) { c.from = v; return this; }
    public Builder to(String v) { c.to = v; return this; }
    @SuppressWarnings("unchecked")
    public Builder data(Map<String, ?> v) { c.data = (Map<String, Object>) v; return this; }
    public Builder abiReference(String v) { c.abiReference = v; return this; }
    public Builder abi(JsonNode v) { c.abi = v; return this; }
    public Builder bytecode(String v) { c.bytecode = v; return this; }
    public Builder dependsOn(List<String> v) { c.dependsOn = v; return this; }
    public Builder block(Object v) { c.block = v; return this; }
    public Builder dataFormat(String v) { c.dataFormat = v; return this; }

    public TransactionCall build() {
      if (c.type == null) {
        throw new IllegalStateException("TransactionCall.type is required");
      }
      return c;
    }
  }
}
