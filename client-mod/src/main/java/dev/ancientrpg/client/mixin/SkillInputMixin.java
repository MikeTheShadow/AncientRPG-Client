package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.SkillClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientInput.class)
public abstract class SkillInputMixin {
  @Inject(method = "getMoveVector", at = @At("HEAD"), cancellable = true)
  private void ancientrpg$vector(CallbackInfoReturnable<Vec2> cir) {
    if (SkillClient.INSTANCE.committed()) cir.setReturnValue(Vec2.ZERO);
  }

  @Inject(method = "hasForwardImpulse", at = @At("HEAD"), cancellable = true)
  private void ancientrpg$forward(CallbackInfoReturnable<Boolean> cir) {
    if (SkillClient.INSTANCE.committed()) cir.setReturnValue(false);
  }
}
