package com.khoamcw.payloadforge.payload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.khoamcw.payloadforge.PayloadForge;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Global settings from {gameDir}/payloadforge/config.json. Bad or missing values fall back to defaults. */
public final class PayloadConfig {
	private static volatile PayloadConfig current = new PayloadConfig(256);

	/** Safety limit: payloads with more TNT than this are refused at deployment. */
	public final int maxTntPerDeployment;

	private PayloadConfig(int maxTntPerDeployment) {
		this.maxTntPerDeployment = maxTntPerDeployment;
	}

	public static PayloadConfig get() {
		return current;
	}

	public static void load() {
		int max = 256;
		Path cfg = PayloadStorage.rootDir().resolve("config.json");
		if (Files.exists(cfg)) {
			try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
				JsonElement root = JsonParser.parseReader(r);
				if (root.isJsonObject()) {
					JsonObject o = root.getAsJsonObject();
					if (o.has("maxTntPerDeployment")) {
						max = Math.max(1, o.get("maxTntPerDeployment").getAsInt());
					}
				}
			} catch (Exception e) {
				PayloadForge.LOGGER.warn("Could not read PayloadForge config.json, using defaults: {}", e.getMessage());
			}
		}
		current = new PayloadConfig(max);
	}
}
