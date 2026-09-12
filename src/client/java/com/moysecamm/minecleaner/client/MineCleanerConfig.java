package com.moysecamm.minecleaner.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MineCleanerConfig {

	public static final String[] LANG_CODES = {
			"system", "en_us", "ru_ru", "de_de", "fr_fr", "es_es", "it_it",
			"uk_ua", "pl_pl", "pt_br", "ja_jp", "zh_cn", "ko_kr", "tr_tr"
	};
	public static final String[] LANG_NAMES = {
			"System", "English", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439", "Deutsch",
			"Fran\u00e7ais", "Espa\u00f1ol", "Italiano",
			"\u0423\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430", "Polski",
			"Portugu\u00eas (BR)", "\u65e5\u672c\u8a9e", "\u7b80\u4f53\u4e2d\u6587",
			"\ud55c\uad6d\uc5b4", "T\u00fcrk\u00e7e"
	};

	public boolean enableWorlds = true;
	public boolean enableServers = true;
	public boolean enablePacks = true;
	public boolean enableShaders = true;
	public String language = "system";
	public String worldSort = "date";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Map<String, String>> langCache = new HashMap<>();
	private static MineCleanerConfig instance;

	public static MineCleanerConfig get() {
		if (instance == null) {
			instance = new MineCleanerConfig();
			instance.load();
		}
		return instance;
	}

	public static void reset() {
		instance = null;
	}

	public void save() {
		try {
			Path file = configPath();
			if (file == null) return;
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void load() {
		try {
			Path file = configPath();
			if (file == null || !Files.exists(file)) return;
			JsonObject obj = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			if (obj.has("enableWorlds")) enableWorlds = obj.get("enableWorlds").getAsBoolean();
			if (obj.has("enableServers")) enableServers = obj.get("enableServers").getAsBoolean();
			if (obj.has("enablePacks")) enablePacks = obj.get("enablePacks").getAsBoolean();
			if (obj.has("enableShaders")) enableShaders = obj.get("enableShaders").getAsBoolean();
			if (obj.has("language")) language = obj.get("language").getAsString();
			if (obj.has("worldSort")) worldSort = obj.get("worldSort").getAsString();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static Path configPath() {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.gameDirectory == null) return null;
			return mc.gameDirectory.toPath().resolve("config").resolve("minecleaner.json");
		} catch (Exception e) {
			return null;
		}
	}

	// ==================== LOCALIZATION ====================

	public static Component text(String key, Object... args) {
		MineCleanerConfig cfg = get();
		String code = cfg.language;
		if (code == null || code.isEmpty() || "system".equals(code)) {
			return Component.translatable(key, args);
		}
		String value = lang(code).get(key);
		if (value != null) {
			return Component.literal(args.length == 0 ? value : String.format(value, args));
		}
		return Component.translatable(key, args);
	}

	private static Map<String, String> lang(String code) {
		Map<String, String> cached = langCache.get(code);
		if (cached != null) return cached;
		Map<String, String> map = new HashMap<>();
		try (InputStream in = MineCleanerConfig.class.getResourceAsStream("/assets/minecleaner/lang/" + code + ".json")) {
			if (in != null) {
				Type type = new TypeToken<Map<String, String>>() {}.getType();
				Map<String, String> read = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
				if (read != null) map.putAll(read);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		langCache.put(code, map);
		return map;
	}

	public static String langName(String code) {
		for (int i = 0; i < LANG_CODES.length; i++) {
			if (LANG_CODES[i].equals(code)) return LANG_NAMES[i];
		}
		return code;
	}

	public static int langIndex(String code) {
		for (int i = 0; i < LANG_CODES.length; i++) {
			if (LANG_CODES[i].equals(code)) return i;
		}
		return 0;
	}
}