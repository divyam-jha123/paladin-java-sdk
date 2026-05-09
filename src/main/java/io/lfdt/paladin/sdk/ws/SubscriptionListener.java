package io.lfdt.paladin.sdk.ws;

@FunctionalInterface
public interface SubscriptionListener {

  void onEvent(WebSocketSender sender, SubscriptionEvent event);

  default void onOpen() {}

  default void onClose(int code, String reason) {}

  default void onError(Throwable t) {}
}
