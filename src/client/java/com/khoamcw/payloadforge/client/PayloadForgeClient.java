package com.khoamcw.payloadforge.client;

import net.fabricmc.api.ClientModInitializer;

public class PayloadForgeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PayloadCommand.register();
	}
}