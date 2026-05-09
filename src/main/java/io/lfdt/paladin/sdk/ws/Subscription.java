package io.lfdt.paladin.sdk.ws;

public record Subscription(SubscriptionType type, String name) {

  public Subscription {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Subscription.name is required");
    }
  }
}
