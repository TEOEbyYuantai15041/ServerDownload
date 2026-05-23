package com.teoe.wdl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {
    public static int diameter = 5000;
    public static boolean scanNether = false;
    public static boolean saveChests = true;
    public static String botPrefix = "";
    public static String fakePlayerPrefix = "";

    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "teoe_wdl_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    diameter = data.diameter;
                    scanNether = data.scanNether;
                    saveChests = data.saveChests;
                    botPrefix = data.botPrefix != null ? data.botPrefix : "";
                    fakePlayerPrefix = data.fakePlayerPrefix != null ? data.fakePlayerPrefix : "";
                }
            } catch (Exception e) {
                ModLogger.log("Failed to load config: " + e.getMessage());
            }
        } else {
            save(); // Create default config
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            ConfigData data = new ConfigData();
            data.diameter = diameter;
            data.scanNether = scanNether;
            data.saveChests = saveChests;
            data.botPrefix = botPrefix;
            data.fakePlayerPrefix = fakePlayerPrefix;
            GSON.toJson(data, writer);
        } catch (IOException e) {
            ModLogger.log("Failed to save config: " + e.getMessage());
        }
    }

    private static class ConfigData {
        int diameter = 5000;
        boolean scanNether = false;
        boolean saveChests = true;
        String botPrefix = "";
        String fakePlayerPrefix = "";
    }
}
