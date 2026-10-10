package com.khoamcw.payloadforge;

import com.khoamcw.payloadforge.payload.PayloadManager;
import com.khoamcw.payloadforge.deploy.PayloadDispenserBehavior;
import com.khoamcw.payloadforge.item.PayloadForgeItems;
import com.khoamcw.payloadforge.network.PayloadNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.level.block.DispenserBlock;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayloadForge implements ModInitializer {
	public static final String MOD_ID = "payloadforge";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		PayloadManager.get().loadBlocking();
		PayloadForgeItems.register();
		PayloadNetworking.register();
		DispenserBlock.registerBehavior(PayloadForgeItems.PAYLOAD_ITEM, new PayloadDispenserBehavior());
		LOGGER.info("PayloadForge initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
