package com.comag.shoresmp.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Your settings. Saved to config/shoresmp-tiertagger.json
public class TierConfig {

    // ---- settings ----
    public boolean modEnabled = true;
    public boolean tablist = true;
    public boolean nametags = true;
    public boolean icons = true;
    public boolean retired = true;
    public boolean showPeaks = true;

    // NEW: real data from the bot
    public boolean useSampleData = false;                 // true = the old fake data
    public String apiUrl = "https://tableware-grimacing-crudely.ngrok-free.dev";       // where the bot's API lives

    public List<String> priority = new ArrayList<>(List.of("Crystal", "SMP", "Mace", "NethOP", "Sword"));

    // Colors are stored as hex text like "FFCF4A"
    public Map<String, String> colors = defaultColors();

    // ---- defaults ----
    private static final List<String> MODES = List.of("Crystal", "SMP", "Mace", "NethOP", "Sword");

    public static final String[] COLOR_KEYS = {"HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5", "Retired"};

    public static Map<String, String> defaultColors() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("HT1", "FFCF4A");
        m.put("LT1", "D5B355");
        m.put("HT2", "A4B3C7");
        m.put("LT2", "888D95");
        m.put("HT3", "DD8849");
        m.put("LT3", "B36830");
        for (String t : new String[]{"HT4", "LT4", "HT5", "LT5"}) m.put(t, "8A8AA6");
        m.put("Retired", "A2D6FF");
        return m;
    }

    // ---- load / save ----
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TierConfig instance = new TierConfig();

    public static TierConfig get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("shoresmp-tiertagger.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    TierConfig loaded = GSON.fromJson(r, TierConfig.class);
                    if (loaded != null) instance = loaded;
                }
            }
        } catch (Exception e) {
            instance = new TierConfig(); // broken file -> start fresh
        }
        instance.repair();
        save();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(file())) {
            GSON.toJson(instance, w);
        } catch (Exception ignored) {
        }
    }

    // Fills in anything missing (older config files won't have the new settings)
    public void repair() {
        if (apiUrl == null || apiUrl.isBlank()) apiUrl = "https://tableware-grimacing-crudely.ngrok-free.dev";

        if (priority == null) priority = new ArrayList<>();
        priority.removeIf(p -> !MODES.contains(p));
        List<String> seen = new ArrayList<>();
        for (String p : priority) if (!seen.contains(p)) seen.add(p);
        for (String m : MODES) if (!seen.contains(m)) seen.add(m);
        priority = seen;

        if (colors == null) colors = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : defaultColors().entrySet()) {
            String v = colors.get(e.getKey());
            if (v == null || !v.matches("[0-9A-Fa-f]{6}")) colors.put(e.getKey(), e.getValue());
        }
    }

    public static void resetColors() {
        instance.colors = defaultColors();
    }

    // The color for a tier key ("HT1" ... "LT5" or "Retired") as 0xRRGGBB
    public static int color(String key) {
        String v = instance.colors.get(key);
        if (v == null) v = defaultColors().getOrDefault(key, "FFFFFF");
        try {
            return Integer.parseInt(v, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}