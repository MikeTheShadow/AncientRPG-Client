package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.ThirdPersonCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Options.class)
public abstract class ThirdPersonCameraMixin {
  @ModifyVariable(method = "setCameraType", at = @At("HEAD"), argsOnly = true)
  private CameraType ancientrpg$perspective(CameraType requested) {
    return ThirdPersonCamera.constrain(requested);
  }
}
