package io.lfdt.paladin.sdk.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TransactionTypesTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void transactionInputBuilderRequiresTypeAndFrom() {
    assertThatThrownBy(() -> TransactionInput.builder().from("alice").build())
        .hasMessageContaining("type is required");
    assertThatThrownBy(() -> TransactionInput.builder().type(TransactionType.PUBLIC).build())
        .hasMessageContaining("from is required");
  }

  @Test
  void transactionTypeEnumWireValues() throws Exception {
    String publicJson = mapper.writeValueAsString(TransactionType.PUBLIC);
    String privateJson = mapper.writeValueAsString(TransactionType.PRIVATE);
    assertThat(publicJson).isEqualTo("\"public\"");
    assertThat(privateJson).isEqualTo("\"private\"");
  }
}
