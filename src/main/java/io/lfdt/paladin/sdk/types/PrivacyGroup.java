package io.lfdt.paladin.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrivacyGroup(
    String id,
    String domain,
    String created,
    String name,
    List<String> members,
    Map<String, String> properties,
    Map<String, String> configuration,
    String contractAddress,
    String genesisTransaction,
    String genesisSchema,
    String genesisSalt) {}
