package app.specy.rars.config;

import java.util.HashMap;

public abstract class ConfigMap extends HashMap<String, String>{


    abstract public void reset();

    public int getIntegerValue(String key) {
        return Integer.parseInt(get(key));
    }

    public boolean getBooleanValue(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public void setBooleanValue(String key, boolean value) {
        put(key, Boolean.toString(value));
    }

    public void setIntegerValue(String key, int value) {
        put(key, Integer.toString(value));
    }

}
