package dev.ancientrpg.client;

import dev.ancientrpg.protocol.Interaction;
import dev.ancientrpg.protocol.InteractionResult;
import dev.ancientrpg.protocol.PublicContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;

public final class AncientRpgClient implements ClientModInitializer {
  private boolean responded;
  private long sequence;

  @Override
  public void onInitializeClient() {
    SkillClient.INSTANCE.install(() -> sequence++);
    InventoryClient.INSTANCE.install(() -> sequence++);
    PayloadTypeRegistry.clientboundConfiguration()
        .register(DemoPayloads.Hello.TYPE, DemoPayloads.Hello.CODEC);
    PayloadTypeRegistry.serverboundConfiguration()
        .register(DemoPayloads.Hello.TYPE, DemoPayloads.Hello.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(DemoPayloads.Activate.TYPE, DemoPayloads.Activate.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(DemoPayloads.Result.TYPE, DemoPayloads.Result.CODEC);
    ClientConfigurationConnectionEvents.INIT.register((handler, client) -> responded = false);
    ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sequence = 0);
    ClientConfigurationNetworking.registerGlobalReceiver(
        DemoPayloads.Hello.TYPE,
        (payload, context) -> {
          if (responded) {
            context
                .responseSender()
                .disconnect(Component.literal("Duplicate AncientRPG handshake"));
            return;
          }
          responded = true;
          var actual =
              PublicContent.identity(
                  SharedConstants.getProtocolVersion(),
                  BuiltInRegistries.BLOCK.getId(DemoRegistration.block),
                  Block.getId(
                      DemoRegistration.block
                          .defaultBlockState()
                          .setValue(DemoRegistration.ACTIVE, true)),
                  Block.getId(
                      DemoRegistration.block
                          .defaultBlockState()
                          .setValue(DemoRegistration.ACTIVE, false)),
                  BuiltInRegistries.ITEM.getId(DemoRegistration.item),
                  BuiltInRegistries.ITEM.getId(DemoRegistration.sword),
                  dev.ancientrpg.protocol.InventoryCatalog.ITEMS.stream()
                      .filter(i -> i.id() != 1 && i.id() != 10)
                      .mapToInt(
                          i ->
                              BuiltInRegistries.ITEM.getId(
                                  DemoRegistration.inventoryItems.get(i.id())))
                      .toArray());
          // Always send our own identity, never echo an untrusted server declaration.
          context.responseSender().sendPacket(new DemoPayloads.Hello(actual));
        });
    ClientPlayNetworking.registerGlobalReceiver(
        DemoPayloads.Result.TYPE,
        (payload, context) -> {
          var result = payload.result();
          context
              .client()
              .execute(
                  () ->
                      context
                          .client()
                          .gui
                          .hud
                          .setOverlayMessage(
                              Component.literal(
                                  result.status() == InteractionResult.Status.DENIED
                                      ? "Interaction denied"
                                      : "Ancient Switch: " + (result.active() ? "On" : "Off")),
                              true));
        });
    UseBlockCallback.EVENT.register(
        (player, world, hand, hit) -> {
          if (SkillClient.INSTANCE.committed()) return net.minecraft.world.InteractionResult.FAIL;
          if (!world.isClientSide()
              || world.getBlockState(hit.getBlockPos()).getBlock() != DemoRegistration.block) {
            return net.minecraft.world.InteractionResult.PASS;
          }
          if (hand == InteractionHand.MAIN_HAND
              && ClientPlayNetworking.canSend(DemoPayloads.Activate.TYPE)) {
            var pos = hit.getBlockPos();
            ClientPlayNetworking.send(
                new DemoPayloads.Activate(
                    new Interaction(sequence++, pos.getX(), pos.getY(), pos.getZ())));
          }
          return net.minecraft.world.InteractionResult.SUCCESS;
        });
  }
}
