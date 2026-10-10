package com.khoamcw.payloadforge.payload;

import java.util.UUID;

/**
 * One primed TNT in a payload.
 *
 * Coordinates are in block units in the payload's LOCAL space, where (0.5, 0.5, 0.5) is the
 * reference point (the centre of the dispenser block). They are stored exactly as entered.
 * Fuse is the initial fuse in ticks.
 */
public final class TntEntry {
	/** Vanilla persists the TNT fuse as a short, so larger values would wrap around when saved. */
	public static final int MAX_FUSE = Short.MAX_VALUE;
	public static final double DEFAULT_X = 0.5, DEFAULT_Y = 0.5, DEFAULT_Z = 0.5;
	public static final int DEFAULT_FUSE = 80;

	public String id;
	public double x;
	public double y;
	public double z;
	public int fuse;

	public TntEntry(String id, double x, double y, double z, int fuse) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.z = z;
		this.fuse = fuse;
	}

	public static TntEntry createDefault() {
		return new TntEntry(UUID.randomUUID().toString(), DEFAULT_X, DEFAULT_Y, DEFAULT_Z, DEFAULT_FUSE);
	}

	public TntEntry copy() {
		return new TntEntry(id, x, y, z, fuse);
	}

	/** @return null if valid, otherwise a human readable error. */
	public String validate() {
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			return "Coordinates must be finite numbers";
		}
		if (fuse < 0 || fuse > MAX_FUSE) {
			return "Fuse must be an integer from 0 to " + MAX_FUSE;
		}
		if (id == null || id.isBlank()) {
			return "Entry has no id";
		}
		return null;
	}
}
