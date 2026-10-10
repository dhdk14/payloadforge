package com.khoamcw.payloadforge.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Minimal scrollable single-selection text list drawn directly by a screen. */
final class ListPane {
	private static final int ROW_H = 14;

	int x, y, w, h;
	int selected = -1;
	private double scroll;
	private List<String> rows = new ArrayList<>();

	void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		clamp();
	}

	void setRows(List<String> rows, int selected) {
		this.rows = rows;
		this.selected = selected;
		clamp();
	}

	boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** @return row index at the mouse position, or -1. */
	int indexAt(double mx, double my) {
		if (!contains(mx, my)) {
			return -1;
		}
		int i = (int) ((my - y - 2 + scroll) / ROW_H);
		return i >= 0 && i < rows.size() ? i : -1;
	}

	void scrollBy(double amount) {
		scroll -= amount * ROW_H;
		clamp();
	}

	private void clamp() {
		double max = Math.max(0, rows.size() * ROW_H + 4 - h);
		scroll = Math.max(0, Math.min(scroll, max));
	}

	void render(GuiGraphicsExtractor g, Font font, int mx, int my) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF808080);
		g.fill(x, y, x + w, y + h, 0xFF101010);
		g.enableScissor(x, y, x + w, y + h);
		int hover = indexAt(mx, my);
		for (int i = 0; i < rows.size(); i++) {
			int ry = y + 2 + i * ROW_H - (int) scroll;
			if (ry + ROW_H < y || ry > y + h) {
				continue;
			}
			if (i == selected) {
				g.fill(x, ry, x + w, ry + ROW_H, 0xFF3A5FCD);
			} else if (i == hover) {
				g.fill(x, ry, x + w, ry + ROW_H, 0xFF303030);
			}
			g.text(font, rows.get(i), x + 4, ry + 3, 0xFFFFFFFF);
		}
		g.disableScissor();
	}
}
