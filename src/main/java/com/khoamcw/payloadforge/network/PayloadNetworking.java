package com.khoamcw.payloadforge.network;

import com.khoamcw.payloadforge.item.PayloadForgeItems;
import com.khoamcw.payloadforge.payload.PayloadDefinition;
import com.khoamcw.payloadforge.payload.PayloadManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PayloadNetworking {
	private PayloadNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(GivePayloadPacket.TYPE, GivePayloadPacket.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GivePayloadPacket.TYPE, (packet, context) -> {
			ServerPlayer player = context.player();
			// Allowed in singleplayer/LAN-host worlds and for creative players.
			if (!(context.server().isSingleplayer() || player.isCreative())) {
				player.sendSystemMessage(Component.literal("[PayloadForge] You need creative mode to get payload items."));
				return;
			}
			PayloadDefinition def = PayloadManager.get().get(packet.payloadId());
			if (def == null) {
				player.sendSystemMessage(Component.literal("[PayloadForge] This world's server has no such payload."));
				return;
			}
			ItemStack stack = PayloadForgeItems.createStack(def);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			player.sendSystemMessage(Component.literal("[PayloadForge] Gave payload item: " + def.name));
		});
	}
}
