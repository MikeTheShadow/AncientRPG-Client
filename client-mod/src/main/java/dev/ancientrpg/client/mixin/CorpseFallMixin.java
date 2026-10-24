package dev.ancientrpg.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ancientrpg.client.CorpseClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class CorpseFallMixin {
  @Redirect(
      method = "setupRotations",
      at = @At(value = "FIELD", target = "Lcom/mojang/math/Axis;ZP:Lcom/mojang/math/Axis;"))
  private Axis ancientrpg$backwardFall(
      LivingEntityRenderState state, PoseStack poses, float bodyYaw, float scale) {
    return state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar
            && CorpseClient.isBody(avatar.id)
        ? Axis.XP
        : Axis.ZP;
  }
}
