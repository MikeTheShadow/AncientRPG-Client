package dev.ancientrpg.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Canonical fingerprint of explicitly selected public definitions and asset bytes. */
public final class ContentManifest {
  private ContentManifest() {}

  public static String fingerprint(Map<String, byte[]> entries) {
    MessageDigest digest = sha256();
    digest.update("AncientRPG-content-v1\n".getBytes(StandardCharsets.US_ASCII));
    TreeMap<String, byte[]> sorted = new TreeMap<>(entries);
    digest.update(integer(sorted.size()));
    sorted.forEach(
        (path, content) -> {
          if (!path.matches("[a-z0-9_-]+(/[a-z0-9_.-]+)*")
              || path.contains("..")
              || content == null) {
            throw new IllegalArgumentException("Invalid public manifest entry");
          }
          byte[] name = path.getBytes(StandardCharsets.US_ASCII);
          digest.update(integer(name.length));
          digest.update(name);
          digest.update(integer(content.length));
          digest.update(content);
        });
    return HexFormat.of().formatHex(digest.digest());
  }

  private static byte[] integer(int value) {
    return ByteBuffer.allocate(4).putInt(value).array();
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("JDK is missing SHA-256", impossible);
    }
  }
}
