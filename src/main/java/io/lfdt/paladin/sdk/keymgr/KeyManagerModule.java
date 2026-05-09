package io.lfdt.paladin.sdk.keymgr;

import com.fasterxml.jackson.core.type.TypeReference;
import io.lfdt.paladin.sdk.internal.RpcModule;
import io.lfdt.paladin.sdk.query.Query;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import io.lfdt.paladin.sdk.types.EthAddress;
import io.lfdt.paladin.sdk.types.KeyMappingAndVerifier;
import io.lfdt.paladin.sdk.types.WalletInfo;
import java.util.List;

public final class KeyManagerModule extends RpcModule {

  public KeyManagerModule(JsonRpcTransport transport) {
    super(transport);
  }

  public List<WalletInfo> wallets() {
    return call("keymgr_wallets", List.of(), new TypeReference<List<WalletInfo>>() {});
  }

  public KeyMappingAndVerifier resolveKey(String identifier, String algorithm, String verifierType) {
    return call("keymgr_resolveKey", List.of(identifier, algorithm, verifierType), KeyMappingAndVerifier.class);
  }

  public EthAddress resolveEthAddress(String identifier) {
    return call("keymgr_resolveEthAddress", List.of(identifier), EthAddress.class);
  }

  public KeyMappingAndVerifier reverseKeyLookup(String algorithm, String verifierType, String verifier) {
    return call("keymgr_reverseKeyLookup", List.of(algorithm, verifierType, verifier), KeyMappingAndVerifier.class);
  }

  public List<KeyMappingAndVerifier> queryKeys(Query query) {
    return call("keymgr_queryKeys", List.of(query), new TypeReference<List<KeyMappingAndVerifier>>() {});
  }

  public String sign(String keyIdentifier, String algorithm, String verifierType, String payloadType, String payload) {
    return call("keymgr_sign", List.of(keyIdentifier, algorithm, verifierType, payloadType, payload), String.class);
  }
}
