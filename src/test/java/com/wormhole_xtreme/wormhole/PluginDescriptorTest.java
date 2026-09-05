package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The version the server reports comes from the build, not from a second copy of it.
 *
 * <p>plugin.yml is what Bukkit reads, and it used to carry a hardcoded version. That drifted:
 * the pom said 1.1.0, a release was tagged and cut as 1.1.0, and the running server announced
 * itself as 1.0.0 — in the enable line, in every log line, and to anything asking the plugin
 * its version. Nothing failed, so nothing pointed it out.
 *
 * <p>plugin.yml now takes the version from the pom by resource filtering, and these read the
 * filtered copy under target/classes rather than the source, because the source is the
 * placeholder and the built file is what ships.
 */
class PluginDescriptorTest
{
    private static final Path BUILT_PLUGIN_YML = Paths.get("target/classes/plugin.yml");

    private static String valueOf(final String key) throws Exception
    {
        assertTrue(Files.exists(BUILT_PLUGIN_YML),
            "expected a filtered plugin.yml at " + BUILT_PLUGIN_YML.toAbsolutePath()
                + "; tests run after resources are processed, so this should exist");
        for (final String line : Files.readAllLines(BUILT_PLUGIN_YML))
        {
            if (line.startsWith(key + ":"))
            {
                return line.substring(key.length() + 1).trim();
            }
        }
        return null;
    }

    @Test
    void theBuiltDescriptorCarriesARealVersion() throws Exception
    {
        final String version = valueOf("version");

        assertNotNull(version, "plugin.yml must declare a version");
        assertFalse(version.contains("${"),
            "the version should have been substituted at build time, but plugin.yml still says " + version);
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"),
            "expected a version number, got " + version);
    }

    @Test
    void theDescriptorVersionMatchesTheProjectVersion() throws Exception
    {
        // The whole point: one place declares the version and everything else follows it.
        // Read the pom directly rather than trusting the two to agree.
        final List<String> pom = Files.readAllLines(Paths.get("pom.xml"));
        String projectVersion = null;
        for (int i = 0; (i < pom.size()) && (projectVersion == null); i++)
        {
            // The project's own version is the one before <packaging>/<name>, ahead of any
            // plugin or dependency version, so take the first at the top of the file.
            final String line = pom.get(i).trim();
            if (line.startsWith("<version>") && line.endsWith("</version>") && (i < 15))
            {
                projectVersion = line.substring("<version>".length(), line.length() - "</version>".length());
            }
        }
        assertNotNull(projectVersion, "could not find the project version in pom.xml");

        assertEquals(projectVersion, valueOf("version"),
            "the version the server reports should be the version the project was built as");
    }

    @Test
    void theDescriptorStillNamesItsMainClassAndApi() throws Exception
    {
        // Filtering rewrites this file at build time, so the rest of it is worth a glance:
        // a broken plugin.yml is a plugin the server refuses to load at all.
        assertEquals("WormholeXTreme", valueOf("name"));
        assertEquals("com.wormhole_xtreme.wormhole.WormholeXTreme", valueOf("main"));
        assertNotNull(valueOf("api-version"), "api-version should survive filtering");
    }
}
