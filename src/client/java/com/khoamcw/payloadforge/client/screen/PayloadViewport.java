package com.khoamcw.payloadforge.client.screen;

import com.khoamcw.payloadforge.payload.TntEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Orbit-camera 3D preview of a payload, projected onto the GUI.
 *
 * Fixed WORLD axes: X = east, Y = up, Z = south (never rotated by the dispenser direction).
 * Origin (0,0,0) is the centre of the block in front of the dispenser. Entries are drawn at their exact
 * configured positions as textured 0.98-block TNT cubes; overlapping entries are NOT separated.
 * Only the selected entry gets an outline.
 *
 * Mouse: drag = rotate, scroll = zoom, click = select the nearest TNT.
 */
final class PayloadViewport {
	private static final double TNT_HALF = 0.49;
	private static final int TEXTURED_LIMIT = 30;
	private static final int FLAT_LIMIT = 150;
	private static final int LABEL_LIMIT = 40;
	private static final int FILL_BUDGET = 7000;
	private static final int MAX_DOTS = 3000;

	// faces: +Z south, -Z north, +X east, -X west, +Y top, -Y bottom
	private static final int[][] NORMAL = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
	// corners as seen from outside: top-left, top-right, bottom-right, bottom-left
	private static final int[][][] CORNER = {
			{{-1, 1, 1}, {1, 1, 1}, {1, -1, 1}, {-1, -1, 1}},
			{{1, 1, -1}, {-1, 1, -1}, {-1, -1, -1}, {1, -1, -1}},
			{{1, 1, 1}, {1, 1, -1}, {1, -1, -1}, {1, -1, 1}},
			{{-1, 1, -1}, {-1, 1, 1}, {-1, -1, 1}, {-1, -1, -1}},
			{{-1, 1, -1}, {1, 1, -1}, {1, 1, 1}, {-1, 1, 1}},
			{{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}}
	};
	private static final int[] FACE_TEX = {TntTextures.SIDE, TntTextures.SIDE, TntTextures.SIDE, TntTextures.SIDE,
			TntTextures.TOP, TntTextures.BOTTOM};
	private static final double[] SHADE = {0.8, 0.8, 0.6, 0.6, 1.0, 0.5};

	int x, y, w, h;
	IntConsumer onSelect;

	private double yaw = 0.55, pitch = 0.5, dist = 8;
	private boolean dragging;
	private boolean fitted;
	private List<TntEntry> entries = List.of();
	private int selected = -1;

	// per-frame cache
	private double cosY, sinY, cosP, sinP, focal;
	private int cx, cy, fills;

	PayloadViewport() {
		TntTextures.invalidate();
	}

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
		double bestD = 14 * 14;
		double bestDepth = Double.MAX_VALUE;
		for (int i = 0; i < entries.size(); i++) {
			TntEntry e = entries.get(i);
			double[] p = proj(e.x, e.y, e.z);
			if (p == null) {
				continue;
			}
			double d = (p[0] - mx) * (p[0] - mx) + (p[1] - my) * (p[1] - my);
			// nearest to the cursor; ties (overlap) go to the one closer to the camera
			if (d < bestD || (d == bestD && p[2] < bestDepth)) {
				bestD = d;
				bestDepth = p[2];
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

	/** World (X east, Y up, Z south) to camera space, before the camera distance is added. */
	private double[] view(double px, double py, double pz) {
		pz = -pz; // camera looks north at yaw 0, so depth grows toward -Z
		double x1 = px * cosY + pz * sinY;
		double z1 = -px * sinY + pz * cosY;
		double y2 = py * cosP - z1 * sinP;
		double z2 = py * sinP + z1 * cosP;
		return new double[] {x1, y2, z2};
	}

	/** @return {screenX, screenY, depth} or null when behind the camera. */
	private double[] proj(double px, double py, double pz) {
		double[] v = view(px, py, pz);
		double d = v[2] + dist;
		if (d < 0.3) {
			return null;
		}
		double s = focal / d;
		return new double[] {cx + v[0] * s, cy - v[1] * s, d};
	}

	void render(GuiGraphicsExtractor g, Font font) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF808080);
		g.fill(x, y, x + w, y + h, 0xFF101010);
		prepare();
		fills = 0;
		g.enableScissor(x, y, x + w, y + h);

		// floor grid at y = -0.5 and axes
		for (int i = -4; i <= 4; i++) {
			line(g, -4, -0.5, i, 4, -0.5, i, 0xFF262626);
			line(g, i, -0.5, -4, i, -0.5, 4, 0xFF262626);
		}
		line(g, 0, 0, 0, 2.2, 0, 0, 0xFFFF4444);
		line(g, 0, 0, 0, 0, 2.2, 0, 0xFF44FF44);
		line(g, 0, 0, 0, 0, 0, 2.2, 0xFF4488FF);

		int n = entries.size();
		if (n <= FLAT_LIMIT) {
			Integer[] order = new Integer[n];
			double[] depth = new double[n];
			for (int i = 0; i < n; i++) {
				order[i] = i;
				TntEntry e = entries.get(i);
				depth[i] = view(e.x, e.y, e.z)[2];
			}
			Arrays.sort(order, (a, b) -> Double.compare(depth[b], depth[a])); // far first
			boolean textured = n <= TEXTURED_LIMIT;
			for (int k = 0; k < n; k++) {
				TntEntry e = entries.get(order[k]);
				if (fills > FILL_BUDGET) {
					dot(g, e, 0xFFB8442C);
				} else {
					cube(g, e.x, e.y, e.z, TNT_HALF, textured);
				}
			}
		} else {
			int stride = Math.max(1, n / MAX_DOTS);
			for (int i = 0; i < n; i += stride) {
				dot(g, entries.get(i), 0xFFB8442C);
			}
			if (selected >= 0 && selected < n) {
				TntEntry e = entries.get(selected);
				cube(g, e.x, e.y, e.z, TNT_HALF, true);
			}
		}

		// outline ONLY for the selected entry, drawn on top
		if (selected >= 0 && selected < n) {
			TntEntry e = entries.get(selected);
			wire(g, e.x, e.y, e.z, TNT_HALF + 0.01, 0xFFFFFF55);
			wire(g, e.x, e.y, e.z, TNT_HALF + 0.04, 0xFFFFFF55);
		}

		// origin marker and labels
		double[] o = proj(0, 0, 0);
		if (o != null) {
			g.fill((int) o[0] - 2, (int) o[1] - 2, (int) o[0] + 3, (int) o[1] + 3, 0xFFFFFFFF);
		}
		label(g, font, 0, 0, 0, "Origin", 0xFFFFFFFF);
		label(g, font, 2.4, 0, 0, "X (east)", 0xFFFF4444);
		label(g, font, 0, 2.4, 0, "Y (up)", 0xFF44FF44);
		label(g, font, 0, 0, 2.4, "Z (south)", 0xFF4488FF);
		if (n <= LABEL_LIMIT) {
			for (int i = 0; i < n; i++) {
				if (i != selected) {
					TntEntry e = entries.get(i);
					label(g, font, e.x, e.y + TNT_HALF, e.z, "#" + (i + 1), 0xFFBBBBBB);
				}
			}
		}
		if (selected >= 0 && selected < n) {
			TntEntry e = entries.get(selected);
			label(g, font, e.x, e.y + TNT_HALF, e.z, "#" + (selected + 1) + " fuse " + e.fuse, 0xFFFFFF55);
		}
		g.disableScissor();
		g.text(font, "Drag: rotate   Scroll: zoom   Click: select", x + 4, y + h - 11, 0xFF777777);
	}

	private void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		fills++;
		g.fill(x0, y0, x1, y1, color);
	}

	private void dot(GuiGraphicsExtractor g, TntEntry e, int color) {
		double[] p = proj(e.x, e.y, e.z);
		if (p != null) {
			fill(g, (int) p[0] - 1, (int) p[1] - 1, (int) p[0] + 2, (int) p[1] + 2, color);
		}
	}

	private void label(GuiGraphicsExtractor g, Font font, double px, double py, double pz, String s, int color) {
		double[] p = proj(px, py, pz);
		if (p != null) {
			g.text(font, s, (int) p[0] + 3, (int) p[1] - 9, color);
		}
	}

	/** Solid TNT cube: only the faces turned toward the camera, each textured or flat shaded. */
	private void cube(GuiGraphicsExtractor g, double px, double py, double pz, double half, boolean textured) {
		for (int f = 0; f < 6; f++) {
			int[] nrm = NORMAL[f];
			double[] nv = view(nrm[0], nrm[1], nrm[2]);
			double[] cv = view(px + nrm[0] * half, py + nrm[1] * half, pz + nrm[2] * half);
			// camera sits at (0,0,-dist) in camera space; the face is visible if its normal points at it
			if (nv[0] * cv[0] + nv[1] * cv[1] + nv[2] * (cv[2] + dist) >= 0) {
				continue;
			}
			double[][] p = new double[4][];
			boolean ok = true;
			for (int k = 0; k < 4; k++) {
				int[] c = CORNER[f][k];
				p[k] = proj(px + c[0] * half, py + c[1] * half, pz + c[2] * half);
				if (p[k] == null) {
					ok = false;
					break;
				}
			}
			if (ok) {
				face(g, p, f, textured);
			}
		}
	}

	private void face(GuiGraphicsExtractor g, double[][] p, int f, boolean textured) {
		double shade = SHADE[f];
		TntTextures.Tex t = TntTextures.get(FACE_TEX[f]);
		double size = 0;
		for (int k = 0; k < 4; k++) {
			double[] a = p[k], b = p[(k + 1) & 3];
			size = Math.max(size, Math.hypot(a[0] - b[0], a[1] - b[1]));
		}
		int cells = (int) Math.max(2, Math.min(16, size / 3));
		if (!textured || t == null || size < 6 || fills + cells * cells > FILL_BUDGET) {
			int base = t != null ? t.avg : TntTextures.fallbackColor(FACE_TEX[f]);
			poly(g, p, shadeColor(base, shade));
			return;
		}
		double dux = p[1][0] - p[0][0], duy = p[1][1] - p[0][1];
		double dvx = p[3][0] - p[0][0], dvy = p[3][1] - p[0][1];
		double cw = Math.max(Math.max(Math.abs(dux), Math.abs(dvx)), (Math.abs(dux) + Math.abs(dvx)) * 0.75) / cells;
		double ch = Math.max(Math.max(Math.abs(duy), Math.abs(dvy)), (Math.abs(duy) + Math.abs(dvy)) * 0.75) / cells;
		for (int j = 0; j < cells; j++) {
			double v = (j + 0.5) / cells;
			int ty = Math.min(t.h - 1, (int) (v * t.h));
			for (int i = 0; i < cells; i++) {
				double u = (i + 0.5) / cells;
				int tx = Math.min(t.w - 1, (int) (u * t.w));
				double topX = p[0][0] + (p[1][0] - p[0][0]) * u, topY = p[0][1] + (p[1][1] - p[0][1]) * u;
				double botX = p[3][0] + (p[2][0] - p[3][0]) * u, botY = p[3][1] + (p[2][1] - p[3][1]) * u;
				double sx = topX + (botX - topX) * v, sy = topY + (botY - topY) * v;
				int x0 = (int) Math.floor(sx - cw / 2), x1 = (int) Math.ceil(sx + cw / 2);
				int y0 = (int) Math.floor(sy - ch / 2), y1 = (int) Math.ceil(sy + ch / 2);
				if (x1 <= x0) {
					x1 = x0 + 1;
				}
				if (y1 <= y0) {
					y1 = y0 + 1;
				}
				fill(g, x0, y0, x1, y1, shadeColor(t.argb[ty * t.w + tx], shade));
			}
		}
	}

	private static int shadeColor(int argb, double s) {
		int r = (int) (((argb >> 16) & 255) * s), gr = (int) (((argb >> 8) & 255) * s), b = (int) ((argb & 255) * s);
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}

	/** Fills a convex quad with one colour using horizontal scanlines. */
	private void poly(GuiGraphicsExtractor g, double[][] p, int color) {
		double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (double[] q : p) {
			minY = Math.min(minY, q[1]);
			maxY = Math.max(maxY, q[1]);
		}
		int y0 = (int) Math.floor(minY), y1 = (int) Math.ceil(maxY);
		if (y1 - y0 > 3000) {
			return;
		}
		for (int yy = y0; yy < y1; yy++) {
			double yc = yy + 0.5, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
			for (int e = 0; e < 4; e++) {
				double[] a = p[e], b = p[(e + 1) & 3];
				if ((a[1] <= yc && b[1] > yc) || (b[1] <= yc && a[1] > yc)) {
					double xx = a[0] + (yc - a[1]) / (b[1] - a[1]) * (b[0] - a[0]);
					lo = Math.min(lo, xx);
					hi = Math.max(hi, xx);
				}
			}
			if (hi > lo) {
				fill(g, (int) Math.floor(lo), yy, Math.max((int) Math.ceil(hi), (int) Math.floor(lo) + 1), yy + 1, color);
			}
		}
	}

	/** Wireframe cube, used only for the selected entry. */
	private void wire(GuiGraphicsExtractor g, double px, double py, double pz, double half, int color) {
		double[][] c = new double[8][];
		for (int i = 0; i < 8; i++) {
			c[i] = proj(px + ((i & 1) == 0 ? -half : half), py + ((i & 2) == 0 ? -half : half),
					pz + ((i & 4) == 0 ? -half : half));
		}
		for (int i = 0; i < 8; i++) {
			for (int bit = 1; bit <= 4; bit <<= 1) {
				if ((i & bit) == 0 && c[i] != null && c[i | bit] != null) {
					line2d(g, (int) Math.round(c[i][0]), (int) Math.round(c[i][1]),
							(int) Math.round(c[i | bit][0]), (int) Math.round(c[i | bit][1]), color);
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
