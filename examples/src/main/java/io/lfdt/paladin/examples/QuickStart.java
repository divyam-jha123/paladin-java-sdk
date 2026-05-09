package io.lfdt.paladin.examples;

import io.lfdt.paladin.sdk.PaladinClient;
import io.lfdt.paladin.sdk.types.Algorithms;
import io.lfdt.paladin.sdk.types.ReceiptListener;
import io.lfdt.paladin.sdk.types.Verifiers;
import io.lfdt.paladin.sdk.types.WalletInfo;
import io.lfdt.paladin.sdk.ws.PaladinWebSocketClient;
import io.lfdt.paladin.sdk.ws.SubscriptionEvent;
import io.lfdt.paladin.sdk.ws.SubscriptionListener;
import io.lfdt.paladin.sdk.ws.SubscriptionType;
import io.lfdt.paladin.sdk.ws.WebSocketSender;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class QuickStart {

  public static void main(String[] args) throws Exception {
    String url = args.length > 0 ? args[0] : "http://localhost:31548";
    String wsUrl = args.length > 1
        ? args[1]
        : url.replace(":31548", ":31549").replaceFirst("^http", "ws") + "/ws";

    String listenerName = "java-sdk-quickstart";

    try (PaladinClient client = PaladinClient.builder()
        .url(url)
        .basicAuth("paladin", "paladin")
        .build()) {

      System.out.println("nodeName  = " + client.transportRpc().nodeName());
      System.out.println("transports= " + client.transportRpc().localTransports());
      System.out.println("domains   = " + client.domain().listDomains());
      System.out.println("blockHead = " + client.bidx().getConfirmedBlockHeight());

      List<WalletInfo> wallets = client.keymgr().wallets();
      System.out.println("wallets   = " + wallets);
      String addr = client.ptx().resolveVerifier("alice", Algorithms.ECDSA_SECP256K1, Verifiers.ETH_ADDRESS);
      System.out.println("alice -> " + addr);

      if (client.ptx().getReceiptListener(listenerName).isPresent()) {
        client.ptx().deleteReceiptListener(listenerName);
      }
      client.ptx().createReceiptListener(ReceiptListener.builder()
          .name(listenerName)
          .filters(new ReceiptListener.Filters(0L, null, null))
          .build());
      System.out.println("listener    = " + listenerName + " (recreated, replays from seq 0)");
    }

    CountDownLatch firstBatch = new CountDownLatch(1);
    try (PaladinWebSocketClient ws = PaladinWebSocketClient.builder()
        .url(wsUrl)
        .basicAuth("paladin", "paladin")
        .subscribe(SubscriptionType.RECEIPTS, listenerName)
        .listener(new SubscriptionListener() {
          @Override
          public void onEvent(WebSocketSender sender, SubscriptionEvent event) {
            int n = event.result().path("receipts").size();
            System.out.println("ws batch [" + event.subscriptionName() + "] " + n + " receipts");
            sender.ack(event.subscriptionId());
            firstBatch.countDown();
          }
        })
        .build()) {
      ws.connect().get();
      System.out.println("ws connected, waiting for batch...");
      if (!firstBatch.await(15, TimeUnit.SECONDS)) {
        System.out.println("ws no batch within 15s (chain may be quiescent)");
      }
    }
  }

  private QuickStart() {}
}
