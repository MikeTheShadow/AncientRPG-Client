package dev.ancientrpg.protocol;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

/** Public release identity, never proof that a client is trustworthy. */
public record Compatibility(
    int minecraftProtocol, int wireVersion, String release, String manifest) {
  public Compatibility {
    Objects.requireNonNull(release);
    Objects.requireNonNull(manifest);
    if (minecraftProtocol < 0 || wireVersion < 1) {
      throw new IllegalArgumentException("Invalid protocol version");
    }
    if (!release.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")
        || release.getBytes(StandardCharsets.UTF_8).length > 64) {
      throw new IllegalArgumentException("Invalid release identifier");
    }
    if (!manifest.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Manifest must be lowercase SHA-256");
    }
  }

  public byte[] manifestBytes() {
    return HexFormat.of().parseHex(manifest);
  }
}
