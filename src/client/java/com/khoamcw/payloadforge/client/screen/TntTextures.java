package com.khoamcw.payloadforge.client.screen;

import com.khoamcw.payloadforge.PayloadForge;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Optional;

/** The vanilla TNT block textures (current resource pack), read once for the viewport. */
final class TntTextures {
	static final int SIDE = 0, TOP = 1, BOTTOM = 2;
	private static final String[] NAMES = {"tnt_side", "tnt_top", "tnt_bottom"};
	private static final int[] FALLBACK = {0xFFB8442C, 0xFFC9B7A0, 0xFF8C3A2A};

	static final class Tex {
		final int w, h;
		final int[] argb;
		final int avg;

		Tex(int w, int h, int[] argb, int avg) {
			this.w = w;
			this.h = h;
			this.argb = argb;
			this.avg = avg;
		}
	}

	private static Tex[] cache;

	private TntTextures() {
	}

	/** Forget loaded textures so a changed resource pack is picked up next time. */
	static void invalidate() {
		cache = null;
	}

	/** @return the texture, or null if it could not be read (use fallbackColor). */
	static Tex get(int which) {
		if (cache == null) {
			load();
		}
		return cache[which];
	}

	static int fallbackColor(int which) {
		return FALLBACK[which];
	}

	private static void load() {
		Tex[] t = new Tex[3];
		for (int i = 0; i < 3; i++) {
			try {
				Optional<Resource> r = Minecraft.getInstance().getResourceManager()
						.getResource(Identifier.withDefaultNamespace("textures/block/" + NAMES[i] + ".png"));
				if (r.isEmpty()) {
					continue;
				}
				NativeImage img = null;
				try (InputStream in = r.get().open()) {
					img = NativeImage.read(in);
					int w = img.getWidth(), h = img.getHeight();
					int[] abgr = img.getPixelsABGR();
					int[] argb = new int[w * h];
					long sr = 0, sg = 0, sb = 0;
					int n = 0;
					for (int k = 0; k < argb.length; k++) {
						int p = abgr[k];
						int a = p >>> 24, b = (p >> 16) & 255, g = (p >> 8) & 255, rr = p & 255;
						argb[k] = (a << 24) | (rr << 16) | (g << 8) | b;
						if (a > 127) {
							sr += rr;
							sg += g;
							sb += b;
							n++;
						}
					}
					int avg = n == 0 ? FALLBACK[i]
							: 0xFF000000 | ((int) (sr / n) << 16) | ((int) (sg / n) << 8) | (int) (sb / n);
					t[i] = new Tex(w, h, argb, avg);
				} finally {
					if (img != null) {
						img.close();
					}
				}
			} catch (Exception e) {
				PayloadForge.LOGGER.warn("Could not load TNT texture {}: {}", NAMES[i], e.getMessage());
			}
		}
		cache = t;
	}
}
