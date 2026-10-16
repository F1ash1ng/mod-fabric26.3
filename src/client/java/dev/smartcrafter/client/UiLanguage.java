package dev.smartcrafter.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Client-only language preference for Smart Crafter's own screens. */
final class UiLanguage {
    private static final Logger LOGGER = LoggerFactory.getLogger("smartcrafter/client-language");
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("smartcrafter-client.properties");
    private static boolean chinese = true;

    private UiLanguage() {
    }

    static void initialize() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(CONFIG_FILE)) {
            properties.load(input);
            chinese = Boolean.parseBoolean(properties.getProperty("chinese", "true"));
        } catch (IOException exception) {
            LOGGER.warn("Could not load the Smart Crafter language preference", exception);
        }
    }

    static boolean isChinese() {
        return chinese;
    }

    static String text(String chineseText, String englishText) {
        return chinese ? chineseText : englishText;
    }

    static String toggleLabel() {
        return chinese ? "English" : "中文";
    }

    static void toggle() {
        chinese = !chinese;
        Properties properties = new Properties();
        properties.setProperty("chinese", Boolean.toString(chinese));
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG_FILE)) {
                properties.store(output, "Smart Crafter screen language");
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save the Smart Crafter language preference", exception);
        }
    }
}
