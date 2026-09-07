package com.wormhole_xtreme.wormhole.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * The Class ConfigurationFlatFile.
 * Based on class "MinecartFlatFile" from MinecartMania by Afforess.
 */
class ConfigurationFlatFile
{
    /** Static helpers only; never instantiated. */
    private ConfigurationFlatFile()
    {
    }


    /**
     * Creates the new header.
     * 
     * @param output
     *            the output
     * @param title
     *            the title
     * @param subtitle
     *            the subtitle
     * @param firstHeader
     *            the first header
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    protected static void createNewHeader(final BufferedWriter output, final String title, final String subtitle, final boolean firstHeader) throws IOException
    {
        final String linebreak = "-------------------------------";
        if ( !firstHeader)
        {
            output.write("---------------");
            output.newLine();
            output.newLine();
            output.write(linebreak);
            output.newLine();
        }
        output.write(title);
        output.newLine();
        output.write(subtitle);
        output.newLine();
        output.write(linebreak);
        output.newLine();
        output.newLine();
    }

    /**
     * Creates the new setting.
     * 
     * @param output
     *            the output
     * @param name
     *            the name
     * @param value
     *            the value
     * @param description
     *            the description
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    protected static void createNewSetting(final BufferedWriter output, final ConfigKeys name, final String value, final String description) throws IOException
    {
        final String linebreak = "---------------";
        output.append(linebreak);
        output.newLine();
        output.write("Setting: " + name);
        output.newLine();
        output.write("Value: " + value);
        output.newLine();
        output.write("Description:");

        final ArrayList<String> desc = new ArrayList<String>();
        desc.add(0, "");
        final int maxLength = 80;
        final String[] words = description.split(" ");
        int lineNumber = 0;
        for (final String word : words)
        {
            if (desc.get(lineNumber).length() + word.length() < maxLength)
            {
                desc.set(lineNumber, desc.get(lineNumber) + " " + word);
            }
            else
            {
                lineNumber++;
                desc.add(lineNumber, "             " + word);
            }
        }
        for (final String s : desc)
        {
            output.write(s);
            output.newLine();
        }
    }

    /**
     * Reads one line, and the value under it if that line names the setting being looked for.
     *
     * <p>Its own method rather than a try inside the read loop's try: a line the parser
     * cannot make sense of is skipped and the rest of the file still read. A config file
     * carrying a setting name from an older version is exactly that -- {@code valueOf} throws
     * for a name the enum no longer has -- and without this, one stale line would hide every
     * setting written after it.
     *
     * @param raw
     *            the line as read
     * @param reader
     *            the reader, moved on by one line if this line is the setting wanted
     * @param name
     *            the setting being looked for
     * @param defaultVal
     *            what to answer if the value line cannot be read
     * @return the value, or null if this line is not it
     */
    private static String valueIfThisIsTheSetting(final String raw, final BufferedReader reader,
        final ConfigKeys name, final String defaultVal)
    {
        try
        {
            final String line = raw.trim();
            if (!line.contains("Setting:"))
            {
                return null;
            }
            // A line can contain "Setting:" and still have nothing after the colon, and so can
            // the value line below it. Both used to be indexed straight at [1].
            final String[] key = line.split(":");
            if (key.length < 2)
            {
                return null;
            }
            final ConfigKeys keyValue = ConfigKeys.valueOf(key[1].trim());
            // The value is on the line after the key, and is only read for the key we were
            // asked about -- reading it moves the reader on.
            final String valueLine = (keyValue == name) ? reader.readLine() : null;
            if (valueLine == null)
            {
                return null;
            }
            final String[] val = valueLine.split(":");
            return val.length < 2 ? defaultVal.trim() : val[1].trim();
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Error parsing setting enum:" + e.toString());
            return null;
        }
    }

    /**
     * Gets the value from setting.
     * 
     * @param input
     *            the input
     * @param name
     *            the name
     * @param defaultVal
     *            the default val
     * @return the value from setting
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    protected static String getValueFromSetting(final File input, final ConfigKeys name, final String defaultVal) throws IOException
    {

        BufferedReader bufferedReader = null;
        try
        {
            bufferedReader = new BufferedReader(new FileReader(input, StandardCharsets.UTF_8));
            for (String raw = ""; (raw = bufferedReader.readLine()) != null;)
            {
                final String found = valueIfThisIsTheSetting(raw, bufferedReader, name, defaultVal);
                if (found != null)
                {
                    bufferedReader.close();
                    return found;
                }
            }
            bufferedReader.close();

        }
        catch (final FileNotFoundException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, e.getMessage());
        }
        finally
        {
            if (bufferedReader != null)
            {
                bufferedReader.close();
            }
        }
        return defaultVal.trim();
    }
}
