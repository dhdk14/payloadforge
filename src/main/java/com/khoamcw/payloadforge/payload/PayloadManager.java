package com.khoamcw.payloadforge.payload;

import com.khoamcw.payloadforge.PayloadForge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** In-memory payload library backed by PayloadStorage. Disk writes happen on a background thread. */
public final class PayloadManager {
	private static final PayloadManager INSTANCE = new PayloadManager();

	private final Map<String, PayloadDefinition> payloads = new LinkedHashMap<>();
	private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "PayloadForge-IO");
		t.setDaemon(true);
		return t;
	});

	private PayloadManager() {
	}

	public static PayloadManager get() {
		return INSTANCE;
	}

	/** Called once at mod initialisation (startup, not the render loop). */
	public void loadBlocking() {
		PayloadStorage.ensureConfig();
		replaceAll(PayloadStorage.loadAll());
		PayloadForge.LOGGER.info("Loaded {} payload(s) from {}", count(), PayloadStorage.payloadsDir());
	}

	/** Reloads from disk off-thread (after pending writes finish), then runs onDone on the given executor. */
	public void reloadAsync(Executor callbackExecutor, Runnable onDone) {
		io.execute(() -> {
			List<PayloadDefinition> loaded = PayloadStorage.loadAll();
			replaceAll(loaded);
			callbackExecutor.execute(onDone);
		});
	}

	private synchronized void replaceAll(List<PayloadDefinition> list) {
		payloads.clear();
		for (PayloadDefinition d : list) {
			payloads.put(d.id, d);
		}
	}

	public synchronized int count() {
		return payloads.size();
	}

	/** Snapshot copies sorted by name (case-insensitive), then id. */
	public synchronized List<PayloadDefinition> list() {
		List<PayloadDefinition> out = new ArrayList<>(payloads.size());
		for (PayloadDefinition d : payloads.values()) {
			out.add(d.copy());
		}
		out.sort(Comparator.comparing((PayloadDefinition d) -> d.name.toLowerCase()).thenComparing(d -> d.id));
		return out;
	}

	/** @return a copy of the payload, or null if it does not exist (e.g. deleted). */
	public synchronized PayloadDefinition get(String id) {
		PayloadDefinition d = payloads.get(id);
		return d == null ? null : d.copy();
	}

	public PayloadDefinition create(String name) {
		PayloadDefinition def = PayloadDefinition.createNew(name);
		store(def);
		return def.copy();
	}

	/** @return an error message, or null on success. Invalid payloads are not stored. */
	public String save(PayloadDefinition def) {
		String err = def.validate();
		if (err != null) {
			return err;
		}
		PayloadDefinition c = def.copy();
		c.modifiedAt = System.currentTimeMillis();
		store(c);
		return null;
	}

	public PayloadDefinition duplicate(String id) {
		PayloadDefinition src = get(id);
		if (src == null) {
			return null;
		}
		long now = System.currentTimeMillis();
		String name = src.name + " (copy)";
		if (name.length() > PayloadDefinition.MAX_NAME_LENGTH) {
			name = name.substring(0, PayloadDefinition.MAX_NAME_LENGTH);
		}
		PayloadDefinition copy = new PayloadDefinition(java.util.UUID.randomUUID().toString(), name, now, now);
		for (TntEntry e : src.entries) {
			copy.entries.add(new TntEntry(java.util.UUID.randomUUID().toString(), e.x, e.y, e.z, e.fuse));
		}
		store(copy);
		return copy.copy();
	}

	public void delete(String id) {
		synchronized (this) {
			payloads.remove(id);
		}
		io.execute(() -> {
			try {
				PayloadStorage.delete(id);
			} catch (Exception e) {
				PayloadForge.LOGGER.error("Failed to delete payload {}", id, e);
			}
		});
	}

	private void store(PayloadDefinition def) {
		synchronized (this) {
			payloads.put(def.id, def);
		}
		PayloadDefinition snapshot = def.copy();
		io.execute(() -> {
			try {
				PayloadStorage.write(snapshot);
			} catch (Exception e) {
				PayloadForge.LOGGER.error("Failed to save payload {}", snapshot.id, e);
			}
		});
	}
}
