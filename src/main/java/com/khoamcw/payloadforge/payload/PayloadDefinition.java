package com.khoamcw.payloadforge.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A named group of TNT entries. The id is the stable reference; the name is only a label. */
public final class PayloadDefinition {
	public static final int MAX_NAME_LENGTH = 64;
	public static final int MAX_ENTRIES = 10_000;

	public final String id;
	public String name;
	public final List<TntEntry> entries = new ArrayList<>();
	public long createdAt;
	public long modifiedAt;

	public PayloadDefinition(String id, String name, long createdAt, long modifiedAt) {
		this.id = id;
		this.name = name;
		this.createdAt = createdAt;
		this.modifiedAt = modifiedAt;
	}

	public static PayloadDefinition createNew(String name) {
		long now = System.currentTimeMillis();
		PayloadDefinition def = new PayloadDefinition(UUID.randomUUID().toString(), name, now, now);
		def.entries.add(TntEntry.createDefault());
		return def;
	}

	public PayloadDefinition copy() {
		PayloadDefinition c = new PayloadDefinition(id, name, createdAt, modifiedAt);
		for (TntEntry e : entries) {
			c.entries.add(e.copy());
		}
		return c;
	}

	/** @return null if valid, otherwise a human readable error. */
	public String validate() {
		if (name == null || name.isBlank()) {
			return "Payload name cannot be empty";
		}
		if (name.length() > MAX_NAME_LENGTH) {
			return "Payload name is too long (max " + MAX_NAME_LENGTH + ")";
		}
		if (entries.size() > MAX_ENTRIES) {
			return "Too many TNT entries (max " + MAX_ENTRIES + ")";
		}
		for (int i = 0; i < entries.size(); i++) {
			String err = entries.get(i).validate();
			if (err != null) {
				return "TNT #" + (i + 1) + ": " + err;
			}
		}
		return null;
	}
}
