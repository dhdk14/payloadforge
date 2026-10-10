package com.khoamcw.payloadforge.item;

import com.khoamcw.payloadforge.PayloadForge;
import com.khoamcw.payloadforge.payload.PayloadDefinition;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * payloadforge:payload_item. Stacks to 64. Stores only a payload id reference
 * (payloadforge:payload_id component); the definition lives in global storage.
 */
public final class PayloadForgeItems {
	public static DataComponentType<String> PAYLOAD_ID;
	public static Item PAYLOAD_ITEM;

	private PayloadForgeItems() {
	}

	public static void register() {
		PAYLOAD_ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, PayloadForge.id("payload_id"),
				DataComponentType.<String>builder()
						.persistent(Codec.STRING)
						.networkSynchronized(ByteBufCodecs.STRING_UTF8)
						.build());

		Identifier id = PayloadForge.id("payload_item");
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		PAYLOAD_ITEM = Registry.register(BuiltInRegistries.ITEM, key,
				new Item(new Item.Properties().setId(key).stacksTo(64)));
	}

	/** A one-item stack linked to the payload; its displayed name is exactly the payload name. */
	public static ItemStack createStack(PayloadDefinition def) {
		ItemStack stack = new ItemStack(PAYLOAD_ITEM, 1);
		stack.set(PAYLOAD_ID, def.id);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(def.name).withStyle(s -> s.withItalic(false)));
		return stack;
	}

	/** @return the linked payload id, or null if this is not a linked payload stack. */
	public static String getPayloadId(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() != PAYLOAD_ITEM) {
			return null;
		}
		return stack.get(PAYLOAD_ID);
	}
}
