package com.khoamcw.payloadforge.client.screen;

import com.khoamcw.payloadforge.payload.PayloadDefinition;
import com.khoamcw.payloadforge.payload.PayloadManager;
import com.khoamcw.payloadforge.payload.TntEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payload editor. The left side edits the name and TNT entries.
 * Local coordinates are in blocks; (0.5, 0.5, 0.5) is the dispenser reference point.
 * The 3D viewport on the right is added in Phase 5.
 */
public class PayloadEditorScreen extends Screen {
	private final Screen parent;
	private final PayloadDefinition def;
	private final ListPane list = new ListPane();
	private int selected = -1;
	private boolean suppress;
	private String status = "";
	private int statusColor = 0xFFAAAAAA;

	private EditBox nameBox, xBox, yBox, zBox, fuseBox;
	private Button removeBtn, upBtn, downBtn;
	private int leftW;

	public PayloadEditorScreen(Screen parent, PayloadDefinition def) {
		super(Component.literal("Edit Payload"));
		this.parent = parent;
		this.def = def.copy();
		if (!this.def.entries.isEmpty()) {
			selected = 0;
		}
	}

	@Override
	protected void init() {
		int margin = 10;
		leftW = Math.max(200, (int) (this.width * 0.5));
		int gap = 4;

		nameBox = new EditBox(this.font, margin, 28, leftW, 18, Component.literal("Name"));
		nameBox.setMaxLength(PayloadDefinition.MAX_NAME_LENGTH);
		nameBox.setValue(def.name);
		nameBox.setResponder(v -> {
			def.name = v;
			status = "";
		});
		addRenderableWidget(nameBox);

		list.setBounds(margin, 52, leftW, this.height - 52 - 104);

		int bw = (leftW - gap * 3) / 4;
		int rowY = this.height - 98;
		addRenderableWidget(Button.builder(Component.literal("Add TNT"), b -> addEntry())
				.bounds(margin, rowY, bw, 20).build());
		removeBtn = addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeEntry())
				.bounds(margin + (bw + gap), rowY, bw, 20).build());
		upBtn = addRenderableWidget(Button.builder(Component.literal("Up"), b -> move(-1))
				.bounds(margin + (bw + gap) * 2, rowY, bw, 20).build());
		downBtn = addRenderableWidget(Button.builder(Component.literal("Down"), b -> move(1))
				.bounds(margin + (bw + gap) * 3, rowY, bw, 20).build());

		int boxY = this.height - 52;
		xBox = field(margin, boxY, bw, "X");
		yBox = field(margin + (bw + gap), boxY, bw, "Y");
		zBox = field(margin + (bw + gap) * 2, boxY, bw, "Z");
		fuseBox = field(margin + (bw + gap) * 3, boxY, bw, "Fuse");

		int bw3 = (this.width - margin * 2 - gap * 2) / 3;
		int by = this.height - 26;
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save(false))
				.bounds(margin, by, bw3, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save & Close"), b -> save(true))
				.bounds(margin + bw3 + gap, by, bw3, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(margin + (bw3 + gap) * 2, by, bw3, 20).build());

		refreshList();
		loadFields();
	}

	private EditBox field(int x, int y, int w, String label) {
		EditBox box = new EditBox(this.font, x, y, w, 18, Component.literal(label));
		box.setMaxLength(32);
		box.setResponder(v -> applyFields());
		addRenderableWidget(box);
		return box;
	}

	private void refreshList() {
		List<String> rows = new ArrayList<>();
		for (int i = 0; i < def.entries.size(); i++) {
			TntEntry e = def.entries.get(i);
			rows.add("#" + (i + 1) + "  (" + PayloadScreen.fmt(e.x) + ", " + PayloadScreen.fmt(e.y) + ", "
					+ PayloadScreen.fmt(e.z) + ")  fuse " + e.fuse);
		}
		list.setRows(rows, selected);
		boolean has = selected >= 0;
		removeBtn.active = has;
		upBtn.active = has && selected > 0;
		downBtn.active = has && selected < def.entries.size() - 1;
	}

	private void loadFields() {
		suppress = true;
		boolean has = selected >= 0 && selected < def.entries.size();
		for (EditBox b : new EditBox[] {xBox, yBox, zBox, fuseBox}) {
			b.setEditable(has);
		}
		if (has) {
			TntEntry e = def.entries.get(selected);
			xBox.setValue(PayloadScreen.fmt(e.x));
			yBox.setValue(PayloadScreen.fmt(e.y));
			zBox.setValue(PayloadScreen.fmt(e.z));
			fuseBox.setValue(Integer.toString(e.fuse));
		} else {
			xBox.setValue("");
			yBox.setValue("");
			zBox.setValue("");
			fuseBox.setValue("");
		}
		suppress = false;
	}

	/** Applies the field values to the selected entry only if every field is valid. */
	private void applyFields() {
		if (suppress || selected < 0 || selected >= def.entries.size()) {
			return;
		}
		try {
			double x = parseCoord(xBox.getValue(), "X");
			double y = parseCoord(yBox.getValue(), "Y");
			double z = parseCoord(zBox.getValue(), "Z");
			int fuse = parseFuse(fuseBox.getValue());
			TntEntry e = def.entries.get(selected);
			e.x = x;
			e.y = y;
			e.z = z;
			e.fuse = fuse;
			status = "";
			refreshList();
		} catch (IllegalArgumentException ex) {
			status = ex.getMessage();
			statusColor = 0xFFFF5555;
		}
	}

	private static double parseCoord(String s, String axis) {
		double v;
		try {
			v = Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(axis + " must be a number (decimals and negatives allowed)");
		}
		if (!Double.isFinite(v)) {
			throw new IllegalArgumentException(axis + " must be a finite number");
		}
		return v;
	}

	private static int parseFuse(String s) {
		int v;
		try {
			v = Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Fuse must be a whole number");
		}
		if (v < 0 || v > TntEntry.MAX_FUSE) {
			throw new IllegalArgumentException("Fuse must be 0 to " + TntEntry.MAX_FUSE);
		}
		return v;
	}

	private void select(int i) {
		selected = i;
		refreshList();
		loadFields();
	}

	private void addEntry() {
		if (def.entries.size() >= PayloadDefinition.MAX_ENTRIES) {
			status = "Entry limit reached";
			statusColor = 0xFFFF5555;
			return;
		}
		TntEntry e = TntEntry.createDefault();
		e.id = UUID.randomUUID().toString();
		def.entries.add(e);
		select(def.entries.size() - 1);
	}

	private void removeEntry() {
		if (selected < 0 || selected >= def.entries.size()) {
			return;
		}
		def.entries.remove(selected);
		select(def.entries.isEmpty() ? -1 : Math.min(selected, def.entries.size() - 1));
	}

	private void move(int dir) {
		int j = selected + dir;
		if (selected < 0 || j < 0 || j >= def.entries.size()) {
			return;
		}
		TntEntry t = def.entries.get(selected);
		def.entries.set(selected, def.entries.get(j));
		def.entries.set(j, t);
		select(j);
	}

	private void save(boolean close) {
		String err = PayloadManager.get().save(def);
		if (err != null) {
			status = err;
			statusColor = 0xFFFF5555;
			return;
		}
		status = "Saved";
		statusColor = 0xFF55FF55;
		if (close) {
			this.minecraft.setScreen(parent);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && list.contains(event.x(), event.y())) {
			int i = list.indexAt(event.x(), event.y());
			if (i >= 0) {
				select(i);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (list.contains(x, y)) {
			list.scrollBy(scrollY);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
		g.text(this.font, "Name", 10, 18, 0xFFAAAAAA);
		list.render(g, this.font, mouseX, mouseY);

		int labelY = this.height - 63;
		int gap = 4;
		int bw = (leftW - gap * 3) / 4;
		String[] labels = {"X", "Y", "Z", "Fuse (ticks)"};
		for (int i = 0; i < 4; i++) {
			g.text(this.font, labels[i], 10 + (bw + gap) * i, labelY, 0xFFAAAAAA);
		}
		if (!status.isEmpty()) {
			g.text(this.font, status, 10, this.height - 74, statusColor);
		}

		int vx = 10 + leftW + 12;
		int vw = this.width - 10 - vx;
		int vy = 28;
		int vh = this.height - 28 - 34;
		g.fill(vx - 1, vy - 1, vx + vw + 1, vy + vh + 1, 0xFF808080);
		g.fill(vx, vy, vx + vw, vy + vh, 0xFF101010);
		g.centeredText(this.font, "3D viewport: coming in Phase 5", vx + vw / 2, vy + vh / 2 - 8, 0xFF777777);
		g.centeredText(this.font, "Local origin (0.5, 0.5, 0.5) = dispenser centre", vx + vw / 2, vy + vh / 2 + 4,
				0xFF555555);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
