package dev.ancientrpg.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ancientrpg.client.CorpseClient;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class CorpseFallMixin {
  @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
  private void ancientrpg$ragdoll(
      LivingEntityRenderState state, PoseStack poses, float bodyYaw, float scale, CallbackInfo ci) {
    var pose = ((FabricRenderState) state).getData(CorpseClient.CORPSE_POSE);
    if (pose == null) return;
    poses.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
    poses.mulPose(Axis.YP.rotation((float) pose.yaw()));
    poses.translate(0, 0, (float) -pose.backward());
    poses.mulPose(Axis.XP.rotation((float) pose.pitch()));
    poses.mulPose(Axis.ZP.rotation((float) pose.roll()));
    ci.cancel();
  }
}
