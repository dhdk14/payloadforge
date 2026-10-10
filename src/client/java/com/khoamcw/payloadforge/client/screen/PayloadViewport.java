package com.khoamcw.payloadforge.client.screen;

import com.khoamcw.payloadforge.payload.TntEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Orbit-camera 3D preview of a payload, projected onto the GUI.
 *
 * Shows fixed WORLD axes (never rotated by the dispenser): +X = east, +Y = up, +Z = south.
 * Origin (0,0,0) is the centre of the block in front of the dispenser. Entries are drawn at their
 * exact configured positions (a 0.98-block TNT cube each); overlapping entries are NOT separated.
 * Default view looks north: +X to the right, +Y up, +Z towards the viewer.
 *
 * Mouse: drag = rotate, scroll = zoom, click = select the nearest TNT.
 */
final class PayloadViewport {
	private static final double TNT_HALF = 0.49;
	private static final int FULL_DETAIL_LIMIT = 150;
	private static final int LABEL_LIMIT = 40;

	int x, y, w, h;
	IntConsumer onSelect;

	private double yaw = -0.6, pitch = 0.45, dist = 8;
	private boolean dragging;
	private boolean fitted;
	private List<TntEntry> entries = List.of();
	private int selected = -1;

	// per-frame camera cache
	private double cosY, sinY, cosP, sinP, focal;
	private int cx, cy;

	void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	/** The list is read live, so edits show immediately without rebuilding anything. */
	void setEntries(List<TntEntry> entries, int selected) {
		this.entries = entries;
		this.selected = selected;
		if (!fitted && !entries.isEmpty()) {
			fit();
			fitted = true;
		}
	}

	void resetFit() {
		fitted = false;
	}

	private void fit() {
		double r = 1;
		for (TntEntry e : entries) {
			r = Math.max(r, Math.sqrt(e.x * e.x + e.y * e.y + e.z * e.z));
		}
		dist = Math.max(5, Math.min(300, r * 2.2 + 4));
	}

	boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	boolean isDragging() {
		return dragging;
	}

	boolean click(double mx, double my, int button) {
		if (!contains(mx, my) || button != 0) {
			return false;
		}
		dragging = true;
		prepare();
		int best = -1;
		double bestD = 12 * 12;
		for (int i = 0; i < entries.size(); i++) {
			TntEntry e = entries.get(i);
			double[] p = proj(e.x, e.y, e.z);
			if (p == null) {
				continue;
			}
			double d = (p[0] - mx) * (p[0] - mx) + (p[1] - my) * (p[1] - my);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		if (best >= 0 && onSelect != null) {
			onSelect.accept(best);
		}
		return true;
	}

	void drag(double dx, double dy) {
		yaw += dx * 0.01;
		pitch = Math.max(-1.55, Math.min(1.55, pitch + dy * 0.01));
	}

	void release() {
		dragging = false;
	}

	boolean scroll(double mx, double my, double amount) {
		if (!contains(mx, my)) {
			return false;
		}
		dist = Math.max(1.5, Math.min(400, dist * Math.pow(0.88, amount)));
		return true;
	}

	private void prepare() {
		cosY = Math.cos(yaw);
		sinY = Math.sin(yaw);
		cosP = Math.cos(pitch);
		sinP = Math.sin(pitch);
		focal = Math.min(w, h) * 1.2;
		cx = x + w / 2;
		cy = y + h / 2;
	}

	/** @return {screenX, screenY, depth} or null when behind the camera. */
	private double[] proj(double px, double py, double pz) {
		pz = -pz; // world Z points south (towards the default viewer); view depth points north
		double x1 = px * cosY + pz * sinY;
		double z1 = -px * sinY + pz * cosY;
		double y2 = py * cosP - z1 * sinP;
		double z2 = py * sinP + z1 * cosP;
		double d = z2 + dist;
		if (d < 0.3) {
			return null;
		}
		double s = focal / d;
		return new double[] {cx + x1 * s, cy - y2 * s, d};
	}

	void render(GuiGraphicsExtractor g, Font font) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF808080);
		g.fill(x, y, x + w, y + h, 0xFF101010);
		prepare();
		g.enableScissor(x, y, x + w, y + h);

		// floor grid at y = -0.5 (bottom of the origin block)
		for (int i = -4; i <= 4; i++) {
			line(g, -4.5, -0.5, i + 0.0, 4.5 - 0.0, -0.5, i + 0.0, 0xFF262626);
			line(g, i + 0.0, -0.5, -4.5, i + 0.0, -0.5, 4.5, 0xFF262626);
		}
		// the block in front of the dispenser, centred on the origin
		box(g, 0, 0, 0, 0.5, 0xFF555555);

		// axes
		line(g, 0, 0, 0, 2.2, 0, 0, 0xFFFF4444);
		line(g, 0, 0, 0, 0, 2.2, 0, 0xFF44FF44);
		line(g, 0, 0, 0, 0, 0, 2.2, 0xFF4488FF);
		label(g, font, 2.4, 0, 0, "X (east)", 0xFFFF4444);
		label(g, font, 0, 2.4, 0, "Y (up)", 0xFF44FF44);
		label(g, font, 0, 0, 2.4, "Z (south)", 0xFF4488FF);

		int n = entries.size();
		boolean detail = n <= FULL_DETAIL_LIMIT;
		for (int i = 0; i < n; i++) {
			if (i == selected) {
				continue;
			}
			TntEntry e = entries.get(i);
			if (detail) {
				box(g, e.x, e.y, e.z, TNT_HALF, 0xFFE05050);
			} else {
				double[] p = proj(e.x, e.y, e.z);
				if (p != null) {
					g.fill((int) p[0] - 1, (int) p[1] - 1, (int) p[0] + 2, (int) p[1] + 2, 0xFFE05050);
				}
			}
			if (n <= LABEL_LIMIT) {
				label(g, font, e.x, e.y + TNT_HALF, e.z, "#" + (i + 1), 0xFFBBBBBB);
			}
		}
		if (selected >= 0 && selected < n) {
			TntEntry e = entries.get(selected);
			box(g, e.x, e.y, e.z, TNT_HALF, 0xFFFFFF55);
			box(g, e.x, e.y, e.z, TNT_HALF + 0.03, 0xFFFFFF55);
			double[] p = proj(e.x, e.y, e.z);
			if (p != null) {
				g.fill((int) p[0] - 2, (int) p[1] - 2, (int) p[0] + 3, (int) p[1] + 3, 0xFFFFFF55);
			}
			label(g, font, e.x, e.y + TNT_HALF, e.z, "#" + (selected + 1) + " fuse " + e.fuse, 0xFFFFFF55);
		}

		// origin marker
		double[] o = proj(0, 0, 0);
		if (o != null) {
			g.fill((int) o[0] - 2, (int) o[1] - 2, (int) o[0] + 3, (int) o[1] + 3, 0xFFFFFFFF);
		}
		g.disableScissor();
		g.text(font, "Drag: rotate   Scroll: zoom   Click: select", x + 4, y + h - 11, 0xFF777777);
	}

	private void label(GuiGraphicsExtractor g, Font font, double px, double py, double pz, String s, int color) {
		double[] p = proj(px, py, pz);
		if (p != null) {
			g.text(font, s, (int) p[0] + 3, (int) p[1] - 9, color);
		}
	}

	/** Wireframe cube centred at (px,py,pz) with the given half-size. */
	private void box(GuiGraphicsExtractor g, double px, double py, double pz, double half, int color) {
		double[][] c = new double[8][];
		for (int i = 0; i < 8; i++) {
			c[i] = proj(px + ((i & 1) == 0 ? -half : half), py + ((i & 2) == 0 ? -half : half),
					pz + ((i & 4) == 0 ? -half : half));
		}
		for (int i = 0; i < 8; i++) {
			for (int bit = 1; bit <= 4; bit <<= 1) {
				if ((i & bit) == 0) {
					double[] a = c[i], b = c[i | bit];
					if (a != null && b != null) {
						line2d(g, (int) Math.round(a[0]), (int) Math.round(a[1]), (int) Math.round(b[0]),
								(int) Math.round(b[1]), color);
					}
				}
			}
		}
	}

	private void line(GuiGraphicsExtractor g, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
		double[] a = proj(x0, y0, z0), b = proj(x1, y1, z1);
		if (a != null && b != null) {
			line2d(g, (int) Math.round(a[0]), (int) Math.round(a[1]), (int) Math.round(b[0]),
					(int) Math.round(b[1]), color);
		}
	}

	/** 1px line drawn as horizontal/vertical runs, so far fewer fill calls than per-pixel drawing. */
	private static void line2d(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
		if (dx > 4000 || dy > 4000) {
			return;
		}
		if (dx == 0 && dy == 0) {
			g.fill(x0, y0, x0 + 1, y0 + 1, color);
			return;
		}
		if (dx >= dy) {
			int step = x1 > x0 ? 1 : -1;
			double slope = (double) (y1 - y0) / (x1 - x0);
			int runStart = x0, runY = y0;
			for (int i = 0; i <= dx; i++) {
				int xx = x0 + i * step;
				int yy = (int) Math.round(y0 + (xx - x0) * slope);
				if (yy != runY) {
					g.fill(Math.min(runStart, xx - step), runY, Math.max(runStart, xx - step) + 1, runY + 1, color);
					runStart = xx;
					runY = yy;
				}
			}
			g.fill(Math.min(runStart, x1), runY, Math.max(runStart, x1) + 1, runY + 1, color);
		} else {
			int step = y1 > y0 ? 1 : -1;
			double slope = (double) (x1 - x0) / (y1 - y0);
			int runStart = y0, runX = x0;
			for (int i = 0; i <= dy; i++) {
				int yy = y0 + i * step;
				int xx = (int) Math.round(x0 + (yy - y0) * slope);
				if (xx != runX) {
					g.fill(runX, Math.min(runStart, yy - step), runX + 1, Math.max(runStart, yy - step) + 1, color);
					runStart = yy;
					runX = xx;
				}
			}
			g.fill(runX, Math.min(runStart, y1), runX + 1, Math.max(runStart, y1) + 1, color);
		}
	}
}
