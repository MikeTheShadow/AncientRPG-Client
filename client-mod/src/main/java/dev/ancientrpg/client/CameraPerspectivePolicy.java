package dev.ancientrpg.client;

import net.minecraft.client.CameraType;

/** Remembers the external perspective once, without changing gameplay or camera look. */
final class CameraPerspectivePolicy {
  private CameraType previous;

  CameraType update(boolean ancientRpgSession, CameraType current) {
    if (ancientRpgSession) {
      if (previous == null) previous = current;
      return CameraType.THIRD_PERSON_BACK;
    }
    if (previous == null) return current;
    CameraType restore = previous;
    previous = null;
    return restore;
  }

  CameraType constrain(CameraType requested) {
    return previous == null ? requested : CameraType.THIRD_PERSON_BACK;
  }
}
