package io.lfdt.paladin.sdk.ws;

public interface WebSocketSender {

  void ack(String subscriptionId);

  void nack(String subscriptionId);

  String subscriptionName(String subscriptionId);
}
