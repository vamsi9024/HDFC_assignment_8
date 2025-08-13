package org.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private final Properties properties = new Properties();

    public AppConfig(String resourceName) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Unable to find " + resourceName);
            }
            properties.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load configuration file.", ex);
        }
    }

    public int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public long getLong(String key) {
        return Long.parseLong(properties.getProperty(key));
    }

    public String getString(String key) {
        return properties.getProperty(key);
    }
}