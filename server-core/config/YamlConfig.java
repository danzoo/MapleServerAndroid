package config;

import android.content.Context;
import com.esotericsoftware.yamlbeans.YamlReader;

import java.io.*;
import java.util.List;


public class YamlConfig {
    public static final String CONFIG_FILE_NAME = "config.yaml";
    public static YamlConfig config = null;
    public List<WorldConfig> worlds;
    public ServerConfig server;

    public static YamlConfig loadConfig(Context context) {
        try {
            File configFile = new File(context.getFilesDir(), "config.yaml");
            FileInputStream inputStream = new FileInputStream(configFile);

            // An on-device config.yaml survives app updates (it is seeded once on first run), so it
            // may contain keys this build does not know about - or lack keys this build added.
            // Ignoring unknown keys keeps an older config loadable instead of aborting at startup;
            // missing keys simply stay null and Server#getInetSocket falls back to the IPv4 values.
            com.esotericsoftware.yamlbeans.YamlConfig yamlBeans =
                    new com.esotericsoftware.yamlbeans.YamlConfig();
            yamlBeans.readConfig.setIgnoreUnknownProperties(true);
            YamlReader reader = new YamlReader(new InputStreamReader(inputStream), yamlBeans);
            YamlConfig config = reader.read(YamlConfig.class);
            reader.close();
            return config;
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not read config file " + YamlConfig.CONFIG_FILE_NAME + ": " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("Could not successfully parse config file " + YamlConfig.CONFIG_FILE_NAME + ": " + e.getMessage());
        }
    }
}
