package dev.ancientrpg.protocol;

import org.junit.jupiter.api.Test;

class ProtocolTest {
  @Test
  void wireContractsRejectInvalidInputsAndMatchGoldenFixtures() {
    ProtocolChecks.run();
  }
}
