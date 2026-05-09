package io.lfdt.paladin.sdk.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class Query {

  private final List<Op> equal = new ArrayList<>();
  private final List<Op> neq = new ArrayList<>();
  private final List<Op> like = new ArrayList<>();
  private final List<Op> lessThan = new ArrayList<>();
  private final List<Op> lessThanOrEqual = new ArrayList<>();
  private final List<Op> greaterThan = new ArrayList<>();
  private final List<Op> greaterThanOrEqual = new ArrayList<>();
  private final List<MultiOp> in = new ArrayList<>();
  private final List<MultiOp> nin = new ArrayList<>();
  private final List<Query> or = new ArrayList<>();
  private Op nullOp;
  private Integer limit;
  private final List<String> sort = new ArrayList<>();

  public List<Op> getEqual() { return equal; }
  public List<Op> getNeq() { return neq; }
  public List<Op> getLike() { return like; }
  public List<Op> getLessThan() { return lessThan; }
  public List<Op> getLessThanOrEqual() { return lessThanOrEqual; }
  public List<Op> getGreaterThan() { return greaterThan; }
  public List<Op> getGreaterThanOrEqual() { return greaterThanOrEqual; }
  public List<MultiOp> getIn() { return in; }
  public List<MultiOp> getNin() { return nin; }
  public List<Query> getOr() { return or; }
  public Op getNull() { return nullOp; }
  public Integer getLimit() { return limit; }
  public List<String> getSort() { return sort; }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final Query q = new Query();

    public Builder equal(String field, Object value) {
      q.equal.add(new Op(field, value, null, null));
      return this;
    }

    public Builder notEqual(String field, Object value) {
      q.neq.add(new Op(field, value, null, null));
      return this;
    }

    public Builder like(String field, String value) {
      q.like.add(new Op(field, value, null, null));
      return this;
    }

    public Builder lessThan(String field, Object value) {
      q.lessThan.add(new Op(field, value, null, null));
      return this;
    }

    public Builder lessThanOrEqual(String field, Object value) {
      q.lessThanOrEqual.add(new Op(field, value, null, null));
      return this;
    }

    public Builder greaterThan(String field, Object value) {
      q.greaterThan.add(new Op(field, value, null, null));
      return this;
    }

    public Builder greaterThanOrEqual(String field, Object value) {
      q.greaterThanOrEqual.add(new Op(field, value, null, null));
      return this;
    }

    public Builder in(String field, List<Object> values) {
      q.in.add(new MultiOp(field, values, null, null));
      return this;
    }

    public Builder notIn(String field, List<Object> values) {
      q.nin.add(new MultiOp(field, values, null, null));
      return this;
    }

    public Builder isNull(String field) {
      q.nullOp = new Op(field, null, null, null);
      return this;
    }

    public Builder or(Query... branches) {
      for (Query b : branches) {
        q.or.add(b);
      }
      return this;
    }

    public Builder limit(int limit) {
      q.limit = limit;
      return this;
    }

    public Builder sort(String... sortKeys) {
      for (String s : sortKeys) {
        q.sort.add(s);
      }
      return this;
    }

    public Query build() {
      return q;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Op(String field, Object value, Boolean not, Boolean caseInsensitive) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MultiOp(String field, List<Object> values, Boolean not, Boolean caseInsensitive) {}
}
