package com.khoamcw.payloadforge.deploy;

import com.khoamcw.payloadforge.payload.PayloadConfig;
import com.khoamcw.payloadforge.payload.PayloadDefinition;
import com.khoamcw.payloadforge.payload.TntEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns vanilla primed TNT for a payload. No custom ticking, no custom explosion, no velocity.
 *
 * COORDINATE CONVENTION
 * Payload origin (0, 0, 0) is the centre of the block IN FRONT of the dispenser (the block the
 * dispenser faces). Entry coordinates are offsets in blocks along the fixed WORLD axes:
 * +X = east, +Y = up, +Z = south. They are NEVER rotated: the same payload produces the same
 * layout for a dispenser facing any of the six directions.
 */
public final class PayloadSpawner {
	private PayloadSpawner() {
	}

	public static Vec3 toWorld(BlockPos dispenserPos, Direction facing, TntEntry e) {
		return new Vec3(
				dispenserPos.getX() + facing.getStepX() + 0.5 + e.x,
				dispenserPos.getY() + facing.getStepY() + 0.5 + e.y,
				dispenserPos.getZ() + facing.getStepZ() + 0.5 + e.z);
	}

	/** @return null on success, otherwise the reason nothing was spawned. */
	public static String spawn(ServerLevel level, BlockPos pos, Direction facing, PayloadDefinition def, PayloadConfig cfg) {
		String err = def.validate();
		if (err != null) {
			return "invalid payload: " + err;
		}
		if (def.entries.isEmpty()) {
			return "payload has no TNT";
		}
		if (def.entries.size() > cfg.maxTntPerDeployment) {
			return "payload has " + def.entries.size() + " TNT, limit is " + cfg.maxTntPerDeployment;
		}
		// Build everything first so a failure can never leave half a payload.
		List<PrimedTnt> toSpawn = new ArrayList<>(def.entries.size());
		for (TntEntry e : def.entries) {
			Vec3 p = toWorld(pos, facing, e);
			PrimedTnt tnt = new PrimedTnt(level, p.x, p.y, p.z, null);
			tnt.setDeltaMovement(Vec3.ZERO); // the vanilla constructor adds a random hop; we want none
			tnt.setFuse(e.fuse);
			toSpawn.add(tnt);
		}
		for (PrimedTnt tnt : toSpawn) {
			level.addFreshEntity(tnt);
		}
		return null;
	}
}
