package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.SkillClient;
import dev.ancientrpg.client.SkillPresentation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class SkillMovementMixin {
  @Inject(method = "move", at = @At("HEAD"), cancellable = true)
  private void ancientrpg$move(MoverType type, Vec3 delta, CallbackInfo ci) {
    if (SkillPresentation.interceptMove((LocalPlayer) (Object) this)) ci.cancel();
  }

  @Inject(method = "applyInput", at = @At("HEAD"))
  private void ancientrpg$input(CallbackInfo ci) {
    if (SkillClient.INSTANCE.committed())
      ((LocalPlayer) (Object) this).input.keyPresses =
          net.minecraft.world.entity.player.Input.EMPTY;
  }
}
