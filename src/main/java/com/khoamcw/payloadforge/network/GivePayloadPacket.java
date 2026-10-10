package com.khoamcw.payloadforge.network;

import com.khoamcw.payloadforge.PayloadForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "give me the payload item for this payload id". */
public record GivePayloadPacket(String payloadId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GivePayloadPacket> TYPE =
			new CustomPacketPayload.Type<>(PayloadForge.id("give_payload"));
	public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, GivePayloadPacket> CODEC =
			StreamCodec.composite(ByteBufCodecs.STRING_UTF8, GivePayloadPacket::payloadId, GivePayloadPacket::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
