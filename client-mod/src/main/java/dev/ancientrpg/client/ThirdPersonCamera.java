package dev.ancientrpg.client;

import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** Third-person showcase trial, active only on a matching AncientRPG connection. */
public final class ThirdPersonCamera {
  private static final CameraPerspectivePolicy POLICY = new CameraPerspectivePolicy();
  private static boolean activeSession;

  private ThirdPersonCamera() {}

  static void install(BooleanSupplier compatibleSession) {
    ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, client) -> {
          activeSession = compatibleSession.getAsBoolean();
          synchronize(client);
        });
    ClientTickEvents.START_CLIENT_TICK.register(ThirdPersonCamera::synchronize);
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          activeSession = false;
          apply(client, false);
        });
  }

  public static CameraType constrain(CameraType requested) {
    return POLICY.constrain(requested);
  }

  private static void synchronize(Minecraft client) {
    apply(client, activeSession && client.getConnection() != null);
  }

  private static void apply(Minecraft client, boolean ancientRpgSession) {
    CameraType current = client.options.getCameraType();
    CameraType desired = POLICY.update(ancientRpgSession, current);
    if (current != desired) {
      client.options.setCameraType(desired);
      client.gameRenderer.checkEntityPostEffect(
          desired.isFirstPerson() ? client.getCameraEntity() : null);
    }
  }
}
