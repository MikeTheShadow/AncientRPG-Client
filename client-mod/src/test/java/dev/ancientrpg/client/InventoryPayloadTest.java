package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import dev.ancientrpg.protocol.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class InventoryPayloadTest {
  @Test
  void adapterUsesSharedLayoutAndRejectsOversizeBeforeCopy() {
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      var request = new InventoryRequest(12, 4, false, 7, 45);
      InventoryPayloads.Request.CODEC.encode(buffer, new InventoryPayloads.Request(request));
      byte[] actual = new byte[buffer.readableBytes()];
      buffer.getBytes(0, actual);
      assertArrayEquals(InventoryWire.encodeRequest(request), actual);
      assertEquals(request, InventoryPayloads.Request.CODEC.decode(buffer).value());
      buffer.clear().writeZero(218);
      assertThrows(
          IllegalArgumentException.class, () -> InventoryPayloads.State.CODEC.decode(buffer));
      assertEquals(0, buffer.readerIndex());
    } finally {
      buffer.release();
    }
  }
}
