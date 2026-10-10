package com.khoamcw.payloadforge.payload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.khoamcw.payloadforge.PayloadForge;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Versioned JSON storage in {gameDir}/payloadforge/. One file per payload.
 * Never throws for bad files on load: they are skipped and logged.
 */
public final class PayloadStorage {
	public static final int SCHEMA_VERSION = 2;
	private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private PayloadStorage() {
	}

	public static Path rootDir() {
		return FabricLoader.getInstance().getGameDir().resolve("payloadforge");
	}

	public static Path payloadsDir() {
		return rootDir().resolve("payloads");
	}

	public static void ensureDirs() throws IOException {
		Files.createDirectories(payloadsDir());
	}

	/** Creates config.json with defaults if it does not exist. */
	public static void ensureConfig() {
		try {
			ensureDirs();
			Path cfg = rootDir().resolve("config.json");
			if (!Files.exists(cfg)) {
				JsonObject o = new JsonObject();
				o.addProperty("schemaVersion", SCHEMA_VERSION);
				o.addProperty("maxTntPerDeployment", 256);
				writeAtomic(cfg, GSON.toJson(o));
			}
		} catch (Exception e) {
			PayloadForge.LOGGER.error("Could not create PayloadForge config", e);
		}
	}

	public static List<PayloadDefinition> loadAll() {
		List<PayloadDefinition> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		try {
			ensureDirs();
		} catch (IOException e) {
			PayloadForge.LOGGER.error("Could not create payload directory", e);
			return result;
		}
		List<Path> files = new ArrayList<>();
		try (Stream<Path> s = Files.list(payloadsDir())) {
			s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(files::add);
		} catch (IOException e) {
			PayloadForge.LOGGER.error("Could not list payload directory", e);
			return result;
		}
		for (Path p : files) {
			try {
				PayloadDefinition def = read(p);
				if (seen.add(def.id)) {
					result.add(def);
				} else {
					PayloadForge.LOGGER.warn("Skipping duplicate payload id {} in {}", def.id, p.getFileName());
				}
			} catch (Exception e) {
				PayloadForge.LOGGER.warn("Skipping invalid payload file {}: {}", p.getFileName(), e.getMessage());
			}
		}
		return result;
	}

	static PayloadDefinition read(Path file) throws IOException {
		JsonObject o;
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(r);
			if (!root.isJsonObject()) {
				throw new IOException("root is not an object");
			}
			o = root.getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException("malformed JSON: " + e.getMessage(), e);
		}
		try {
			int version = o.get("schemaVersion").getAsInt();
			if (version < 1 || version > SCHEMA_VERSION) {
				throw new IOException("unsupported schemaVersion " + version);
			}
			String id = o.get("id").getAsString();
			if (!SAFE_ID.matcher(id).matches()) {
				throw new IOException("invalid id");
			}
			String name = o.get("name").getAsString();
			long created = o.has("createdAt") ? o.get("createdAt").getAsLong() : 0L;
			long modified = o.has("modifiedAt") ? o.get("modifiedAt").getAsLong() : created;
			PayloadDefinition def = new PayloadDefinition(id, name, created, modified);
			JsonArray arr = o.getAsJsonArray("entries");
			for (JsonElement el : arr) {
				JsonObject e = el.getAsJsonObject();
				String eid = e.has("id") ? e.get("id").getAsString() : java.util.UUID.randomUUID().toString();
				double ex = e.get("x").getAsDouble(), ey = e.get("y").getAsDouble(), ez = e.get("z").getAsDouble();
				if (version == 1) {
					// v1 used origin (0.5,0.5,0.5); v2 uses (0,0,0). BigDecimal avoids 0.6-0.5=0.0999999...
					ex = shift(ex);
					ey = shift(ey);
					ez = shift(ez);
				}
				def.entries.add(new TntEntry(eid, ex, ey, ez, e.get("fuse").getAsInt()));
			}
			String err = def.validate();
			if (err != null) {
				throw new IOException(err);
			}
			return def;
		} catch (IOException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new IOException("bad or missing field: " + e.getMessage(), e);
		}
	}

	private static double shift(double v) {
		return java.math.BigDecimal.valueOf(v).subtract(java.math.BigDecimal.valueOf(0.5)).doubleValue();
	}

	public static void write(PayloadDefinition def) throws IOException {
		if (!SAFE_ID.matcher(def.id).matches()) {
			throw new IOException("unsafe payload id");
		}
		ensureDirs();
		JsonObject o = new JsonObject();
		o.addProperty("schemaVersion", SCHEMA_VERSION);
		o.addProperty("id", def.id);
		o.addProperty("name", def.name);
		o.addProperty("createdAt", def.createdAt);
		o.addProperty("modifiedAt", def.modifiedAt);
		JsonArray arr = new JsonArray();
		for (TntEntry e : def.entries) {
			JsonObject eo = new JsonObject();
			eo.addProperty("id", e.id);
			eo.addProperty("x", e.x);
			eo.addProperty("y", e.y);
			eo.addProperty("z", e.z);
			eo.addProperty("fuse", e.fuse);
			arr.add(eo);
		}
		o.add("entries", arr);
		writeAtomic(payloadsDir().resolve(def.id + ".json"), GSON.toJson(o));
	}

	public static void delete(String id) throws IOException {
		if (!SAFE_ID.matcher(id).matches()) {
			throw new IOException("unsafe payload id");
		}
		Files.deleteIfExists(payloadsDir().resolve(id + ".json"));
	}

	/** Write to a temp file, then move over the target, so a crash never leaves a half-written file. */
	private static void writeAtomic(Path target, String content) throws IOException {
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
