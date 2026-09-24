package com.wormhole_xtreme.wormhole.config;

import java.util.logging.Level;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * The Class Setting.
 * Based on class "Setting" from MinecartMania by Afforess.
 */
class Setting
{

    /** The name. */
    private final ConfigKeys name;

    /** The desc. */
    private final String desc;

    /** The value. */
    private Object value;

    /** The plugin. */
    private final String plugin;

    /**
     * Instantiates a new setting.
     * 
     * @param plugin
     *            the section it belongs to, which is always the plugin's own
     */
    protected Setting(final ConfigKeys name, final Object value, final String desc, final String plugin)
    {
        this.name = name;
        this.desc = desc;
        this.value = value;
        this.plugin = plugin;
    }

    /**
     * Gets the boolean value.
     */
    public boolean getBooleanValue()
    {
        return ((Boolean) value).booleanValue();
    }

    /**
     * Gets the description.
     */
    public String getDescription()
    {
        return desc;
    }

    /**
     * Gets the double value.
     */
    public double getDoubleValue()
    {
        return ((Double) value).doubleValue();
    }

    /**
     * Gets the int value.
     */
    public int getIntValue()
    {
        return ((Integer) value).intValue();
    }

    /**
     * Gets the level.
     */
    public Level getLevel()
    {
        return Level.parse((String) value);
    }

    /**
     * Gets the material value.
     */
    public Material getMaterialValue()
    {
        return (Material) value;
    }

    /**
     * Gets the name.
     */
    public ConfigKeys getName()
    {
        return name;
    }


    /**
     * Gets the plugin name.
     */
    public String getPluginName()
    {
        return plugin;
    }

    /**
     * Gets the string value.
     */
    public String getStringValue()
    {
        return (String) value;
    }

    /**
     * Gets the value.
     */
    public Object getValue()
    {
        return value;
    }

    /**
     * Sets the value.
     */
    public void setValue(final Object value)
    {
        this.value = value;
    }
}
