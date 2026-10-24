package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.CameraType;
import org.junit.jupiter.api.Test;

class CameraPerspectivePolicyTest {
  @Test
  void unrelatedWorldsKeepEveryNormalCameraChoice() {
    var policy = new CameraPerspectivePolicy();
    for (var type : CameraType.values()) {
      assertEquals(type, policy.update(false, type));
      assertEquals(type, policy.constrain(type));
    }
  }

  @Test
  void allEntryPerspectivesLockRearAndRestoreTheirOriginalValue() {
    for (var original : CameraType.values()) {
      var policy = new CameraPerspectivePolicy();
      assertEquals(CameraType.THIRD_PERSON_BACK, policy.update(true, original));
      for (var requested : CameraType.values())
        assertEquals(CameraType.THIRD_PERSON_BACK, policy.constrain(requested));
      assertEquals(original, policy.update(false, CameraType.THIRD_PERSON_BACK));
      assertEquals(CameraType.FIRST_PERSON, policy.constrain(CameraType.FIRST_PERSON));
    }
  }

  @Test
  void repeatedTicksAndPerspectiveAttemptsDoNotOverwriteTheSavedPreference() {
    var policy = new CameraPerspectivePolicy();
    policy.update(true, CameraType.FIRST_PERSON);
    for (int tick = 0; tick < 100; tick++) {
      assertEquals(CameraType.THIRD_PERSON_BACK, policy.update(true, CameraType.THIRD_PERSON_BACK));
      assertEquals(CameraType.THIRD_PERSON_BACK, policy.constrain(CameraType.THIRD_PERSON_FRONT));
    }
    assertEquals(CameraType.FIRST_PERSON, policy.update(false, CameraType.THIRD_PERSON_BACK));
    assertEquals(
        CameraType.THIRD_PERSON_FRONT, policy.update(false, CameraType.THIRD_PERSON_FRONT));
  }

  @Test
  void aLaterSessionCapturesTheNewExternalPreference() {
    var policy = new CameraPerspectivePolicy();
    policy.update(true, CameraType.FIRST_PERSON);
    policy.update(false, CameraType.THIRD_PERSON_BACK);
    policy.update(true, CameraType.THIRD_PERSON_FRONT);
    assertEquals(CameraType.THIRD_PERSON_FRONT, policy.update(false, CameraType.THIRD_PERSON_BACK));
  }
}
