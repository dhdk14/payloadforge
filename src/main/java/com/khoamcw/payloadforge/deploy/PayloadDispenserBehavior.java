package com.khoamcw.payloadforge.deploy;

import com.khoamcw.payloadforge.PayloadForge;
import com.khoamcw.payloadforge.item.PayloadForgeItems;
import com.khoamcw.payloadforge.payload.PayloadConfig;
import com.khoamcw.payloadforge.payload.PayloadDefinition;
import com.khoamcw.payloadforge.payload.PayloadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

/** Dispenses a whole payload as primed TNT. On any failure nothing spawns and the item is not consumed. */
public final class PayloadDispenserBehavior implements DispenseItemBehavior {
	@Override
	public ItemStack dispense(BlockSource source, ItemStack stack) {
		ServerLevel level = source.level();
		BlockPos pos = source.pos();
		Direction facing = source.state().getValue(DispenserBlock.FACING);

		String id = PayloadForgeItems.getPayloadId(stack);
		PayloadDefinition def = id == null ? null : PayloadManager.get().get(id);
		String error;
		if (id == null) {
			error = "item has no payload id";
		} else if (def == null) {
			error = "payload " + id + " not found";
		} else {
			error = PayloadSpawner.spawn(level, pos, facing, def, PayloadConfig.get());
		}

		if (error != null) {
			PayloadForge.LOGGER.warn("Dispenser at {} refused payload: {}", pos, error);
			level.levelEvent(1001, pos, 0); // vanilla "dispenser failed" click
			return stack;
		}

		stack.shrink(1);
		level.levelEvent(1000, pos, 0); // dispense sound
		level.levelEvent(2000, pos, facing.get3DDataValue()); // smoke particles
		level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
		return stack;
	}
}
