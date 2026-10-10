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
 * Local origin (0, 0, 0) is the centre of the block IN FRONT of the dispenser (the block the
 * dispenser faces). A local coordinate (x, y, z) is an offset in blocks along the local axes:
 *   local X = "right", local Y = "up", local Z = "forward" (the dispenser facing direction),
 * seen by someone looking along the facing direction. Basis vectors in world axes (right, up, forward):
 *   NORTH: right=+X up=+Y fwd=-Z     SOUTH: right=-X up=+Y fwd=+Z
 *   EAST:  right=+Z up=+Y fwd=+X     WEST:  right=-Z up=+Y fwd=-X
 *   UP:    right=+X up=+Z fwd=+Y     DOWN:  right=+X up=-Z fwd=-Y
 * (always right = forward x up). Rotation is about the reference point only.
 * With orientationMode "world_axes" the offset is added to that origin unrotated.
 */
public final class PayloadSpawner {
	private PayloadSpawner() {
	}

	/** {right, up, forward} as int[3] world vectors. */
	static int[][] basis(Direction facing) {
		return switch (facing) {
			case NORTH -> new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, -1}};
			case SOUTH -> new int[][] {{-1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
			case EAST -> new int[][] {{0, 0, 1}, {0, 1, 0}, {1, 0, 0}};
			case WEST -> new int[][] {{0, 0, -1}, {0, 1, 0}, {-1, 0, 0}};
			case UP -> new int[][] {{1, 0, 0}, {0, 0, 1}, {0, 1, 0}};
			case DOWN -> new int[][] {{1, 0, 0}, {0, 0, -1}, {0, -1, 0}};
		};
	}

	public static Vec3 toWorld(BlockPos pos, Direction facing, boolean relative, TntEntry e) {
		double ox = e.x, oy = e.y, oz = e.z;
		double cx = pos.getX() + facing.getStepX() + 0.5;
		double cy = pos.getY() + facing.getStepY() + 0.5;
		double cz = pos.getZ() + facing.getStepZ() + 0.5;
		if (!relative) {
			return new Vec3(cx + ox, cy + oy, cz + oz);
		}
		int[][] b = basis(facing);
		return new Vec3(
				cx + ox * b[0][0] + oy * b[1][0] + oz * b[2][0],
				cy + ox * b[0][1] + oy * b[1][1] + oz * b[2][1],
				cz + ox * b[0][2] + oy * b[1][2] + oz * b[2][2]);
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
			Vec3 p = toWorld(pos, facing, cfg.dispenserRelative, e);
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
