package com.khoamcw.payloadforge.client.screen;

import com.khoamcw.payloadforge.network.GivePayloadPacket;
import com.khoamcw.payloadforge.payload.PayloadDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.khoamcw.payloadforge.payload.PayloadManager;
import com.khoamcw.payloadforge.payload.TntEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Main PayloadForge screen: payload library list and actions. */
public class PayloadScreen extends Screen {
	private final ListPane list = new ListPane();
	private List<PayloadDefinition> payloads = new ArrayList<>();
	private String selectedId;
	private boolean confirmDelete;
	private Button editBtn, dupBtn, deleteBtn, getBtn;
	private String status = "";

	public PayloadScreen() {
		super(Component.literal("PayloadForge"));
	}

	@Override
	protected void init() {
		payloads = PayloadManager.get().list();
		if (selectedId != null && payloads.stream().noneMatch(p -> p.id.equals(selectedId))) {
			selectedId = null;
		}

		int margin = 10;
		int listW = Math.max(120, (int) (this.width * 0.4));
		list.setBounds(margin, 30, listW, this.height - 30 - 56);

		int gap = 4;
		int bw = (this.width - margin * 2 - gap * 3) / 4;
		int row1 = this.height - 50;
		int row2 = this.height - 26;
		addRenderableWidget(Button.builder(Component.literal("Create Payload"), b -> create())
				.bounds(margin, row1, bw, 20).build());
		editBtn = addRenderableWidget(Button.builder(Component.literal("Edit Payload"), b -> edit())
				.bounds(margin + (bw + gap), row1, bw, 20).build());
		dupBtn = addRenderableWidget(Button.builder(Component.literal("Duplicate"), b -> duplicate())
				.bounds(margin + (bw + gap) * 2, row1, bw, 20).build());
		deleteBtn = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> delete())
				.bounds(margin + (bw + gap) * 3, row1, bw, 20).build());
		getBtn = addRenderableWidget(Button.builder(Component.literal("Get Payload Item"), b -> getItem())
				.bounds(margin, row2, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reload"), b -> reload())
				.bounds(margin + (bw + gap), row2, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(margin + (bw + gap) * 3, row2, bw, 20).build());
		refresh();
	}

	private void refresh() {
		List<String> rows = new ArrayList<>();
		int sel = -1;
		for (int i = 0; i < payloads.size(); i++) {
			PayloadDefinition p = payloads.get(i);
			rows.add(p.name + "  (" + p.entries.size() + " TNT)");
			if (p.id.equals(selectedId)) {
				sel = i;
			}
		}
		list.setRows(rows, sel);
		boolean has = sel >= 0;
		editBtn.active = has;
		dupBtn.active = has;
		deleteBtn.active = has;
		getBtn.active = has;
		deleteBtn.setMessage(Component.literal(confirmDelete ? "Confirm delete?" : "Delete"));
	}

	private PayloadDefinition selected() {
		return selectedId == null ? null : PayloadManager.get().get(selectedId);
	}

	private void create() {
		PayloadDefinition def = PayloadManager.get().create("New Payload");
		this.minecraft.setScreen(new PayloadEditorScreen(this, def));
	}

	private void getItem() {
		if (selectedId == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(GivePayloadPacket.TYPE)) {
			status = "Not connected to a world with PayloadForge";
			return;
		}
		ClientPlayNetworking.send(new GivePayloadPacket(selectedId));
		status = "Requested payload item (see chat)";
	}

	private void edit() {
		PayloadDefinition def = selected();
		if (def != null) {
			this.minecraft.setScreen(new PayloadEditorScreen(this, def));
		}
	}

	private void duplicate() {
		if (selectedId == null) {
			return;
		}
		PayloadDefinition copy = PayloadManager.get().duplicate(selectedId);
		if (copy != null) {
			selectedId = copy.id;
		}
		confirmDelete = false;
		payloads = PayloadManager.get().list();
		refresh();
	}

	private void delete() {
		if (selectedId == null) {
			return;
		}
		if (!confirmDelete) {
			confirmDelete = true;
		} else {
			PayloadManager.get().delete(selectedId);
			selectedId = null;
			confirmDelete = false;
			payloads = PayloadManager.get().list();
		}
		refresh();
	}

	private void reload() {
		status = "Reloading...";
		PayloadManager.get().reloadAsync(this.minecraft, () -> {
			status = "Reloaded " + PayloadManager.get().count() + " payload(s)";
			if (this.minecraft.screen == this) {
				payloads = PayloadManager.get().list();
				if (selectedId != null && payloads.stream().noneMatch(p -> p.id.equals(selectedId))) {
					selectedId = null;
				}
				confirmDelete = false;
				refresh();
			}
		});
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && list.contains(event.x(), event.y())) {
			int i = list.indexAt(event.x(), event.y());
			if (i >= 0) {
				selectedId = payloads.get(i).id;
				confirmDelete = false;
				refresh();
				if (doubleClick) {
					edit();
				}
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
		g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		g.text(this.font, "Saved payloads", list.x, 20, 0xFFAAAAAA);
		list.render(g, this.font, mouseX, mouseY);

		int px = list.x + list.w + 12;
		int pw = this.width - 10 - px;
		int py = 30;
		int ph = this.height - 30 - 56;
		g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF808080);
		g.fill(px, py, px + pw, py + ph, 0xFF101010);
		PayloadDefinition sel = selected();
		if (sel == null) {
			g.text(this.font, "Select or create a payload.", px + 6, py + 6, 0xFFAAAAAA);
		} else {
			g.text(this.font, sel.name, px + 6, py + 6, 0xFFFFFFFF);
			g.text(this.font, sel.entries.size() + " TNT entries", px + 6, py + 18, 0xFFAAAAAA);
			int y = py + 34;
			int max = Math.max(0, (ph - 60) / 11);
			for (int i = 0; i < Math.min(max, sel.entries.size()); i++) {
				TntEntry e = sel.entries.get(i);
				g.text(this.font, "#" + (i + 1) + "  " + fmt(e.x) + ", " + fmt(e.y) + ", " + fmt(e.z) + "  fuse " + e.fuse,
						px + 6, y, 0xFFDDDDDD);
				y += 11;
			}
			g.text(this.font, "3D preview: coming in Phase 5", px + 6, py + ph - 14, 0xFF777777);
		}
		if (!status.isEmpty()) {
			g.text(this.font, status, this.width - 10 - this.font.width(status), 20, 0xFFAAAAAA);
		}
	}

	static String fmt(double d) {
		return java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(null);
	}
}
