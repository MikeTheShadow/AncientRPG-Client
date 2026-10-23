package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import dev.ancientrpg.protocol.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class SkillPayloadTest {
  @Test
  void fabricAdaptersUseExactSharedBytesAndBoundBeforeCopy() {
    var request = new SkillRequest(42, Skill.PIERCING_THRUST, false);
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      SkillPayloads.Use.CODEC.encode(buffer, new SkillPayloads.Use(request));
      byte[] actual = new byte[buffer.readableBytes()];
      buffer.getBytes(0, actual);
      assertArrayEquals(SkillWire.encodeRequest(request), actual);
      assertEquals(request, SkillPayloads.Use.CODEC.decode(buffer).request());
      buffer.clear();
      var unlock = new SkillRequest(43, Skill.TWIN_FANG, true);
      SkillPayloads.Unlock.CODEC.encode(buffer, new SkillPayloads.Unlock(unlock));
      assertEquals(unlock, SkillPayloads.Unlock.CODEC.decode(buffer).request());
      buffer.clear();
      var state =
          new SkillWire.State(
              42, SkillWire.Reason.LOCKED, 5, 1, 4, List.of(0L, 0L, 0L, 0L, 0L), null);
      SkillPayloads.State.CODEC.encode(buffer, new SkillPayloads.State(state));
      assertEquals(state, SkillPayloads.State.CODEC.decode(buffer).value());
      buffer.clear();
      var event =
          new SkillWire.Event(
              5,
              1,
              SkillWire.Phase.HIT,
              new SkillWire.Action(1, Skill.QUICK_CUT, 1, 0, 0, 1),
              List.of(new SkillWire.Hit(2, 10, 90)));
      SkillPayloads.Event.CODEC.encode(buffer, new SkillPayloads.Event(event));
      assertEquals(event, SkillPayloads.Event.CODEC.decode(buffer).value());
      buffer.clear().writeZero(1025);
      assertThrows(IllegalArgumentException.class, () -> SkillPayloads.State.CODEC.decode(buffer));
      assertEquals(0, buffer.readerIndex());
      assertThrows(IllegalArgumentException.class, () -> SkillPayloads.Event.CODEC.decode(buffer));
      assertEquals(0, buffer.readerIndex());
    } finally {
      buffer.release();
    }
  }
}
