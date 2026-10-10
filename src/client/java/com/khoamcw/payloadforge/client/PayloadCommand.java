package com.khoamcw.payloadforge.client;

import com.khoamcw.payloadforge.client.screen.PayloadScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Registers the client-side /payload command. */
public final class PayloadCommand {
	private static boolean pendingOpen;

	private PayloadCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("payload").executes(ctx -> {
					// Open on the next client tick so the chat screen has finished closing.
					pendingOpen = true;
					return 1;
				})));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingOpen) {
				pendingOpen = false;
				client.setScreen(new PayloadScreen());
			}
		});
	}
}
