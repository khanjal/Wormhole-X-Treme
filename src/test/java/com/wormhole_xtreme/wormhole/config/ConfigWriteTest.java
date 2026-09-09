package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.PluginForTests;

/**
 * Writing the running configuration back to config.yml.
 *
 * <p>{@link ConfigPreservesUnownedContentTest} says of itself that it pins the file surgery
 * rather than the file writing, and needs no disk for it. That was the right call while the
 * method built its own path; now that it takes the file it writes, this covers the other
 * half -- and it is the half where an admin's file is at stake.
 *
 * <p>Every clean shutdown runs this. Anything it drops is dropped from a real server's
 * config.yml, once per restart, for as long as they keep restarting.
 */
class ConfigWriteTest
{
    @TempDir
    File directory;

    private File cfg;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginForTests.install(mock(WormholeXTreme.class));
        ConfigTestSupport.loadDefaults();
        cfg = new File(directory, "config.yml");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginForTests.remove();
    }

    private void writeExisting(final String... lines) throws Exception
    {
        Files.write(cfg.toPath(), java.util.Arrays.asList(lines));
    }

    private String written() throws Exception
    {
        return new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
    }

    /** With no file there, one is written from the defaults. */
    @Test
    void aMissingFileIsWrittenFromDefaults() throws Exception
    {
        ConfigurationYAML.writeCurrentConfiguration(cfg);

        assertTrue(cfg.isFile());
        assertTrue(written().contains("timeout-shutdown:"), "the defaults are in it");
    }

    /**
     * Everything the writer does not own survives.
     *
     * <p>An admin's comments, and the whole nested material-groups block, are not settings
     * this writer knows about. Rewriting the file from its own list would take all of it
     * out, once per clean shutdown.
     */
    @Test
    void anAdminsOwnContentSurvives() throws Exception
    {
        writeExisting(
            "# My own note about this server",
            "timeout-shutdown: 42",
            "",
            "gate-material-groups:",
            "  Mine:",
            "    structure: QUARTZ_BLOCK",
            "    portal: WATER");

        ConfigurationYAML.writeCurrentConfiguration(cfg);

        final String out = written();
        assertTrue(out.contains("# My own note about this server"), "comments survive: " + out);
        assertTrue(out.contains("gate-material-groups:"), "the nested block survives");
        assertTrue(out.contains("    structure: QUARTZ_BLOCK"), "and its contents");
    }

    /**
     * A key the file does not have is added, with its explanation.
     *
     * <p>An admin who never sees a setting written down has no way to know it exists.
     */
    @Test
    void aKeyMissingFromTheFileIsAddedWithItsComment() throws Exception
    {
        writeExisting("timeout-shutdown: 42");

        ConfigurationYAML.writeCurrentConfiguration(cfg);

        final String out = written();
        assertTrue(out.contains("# --- Added by WormholeXTreme (missing keys) ---"),
            "the added keys are marked as added: " + out);
        assertTrue(out.contains("timeout-activate:"), "and the key itself is there");
    }

    /**
     * permissions-support-disable is never written by this path.
     *
     * <p>It is left as the admin has it rather than dropped: absent from the writer's list,
     * so an existing line survives the rewrite untouched and no line is invented for a
     * server that never set one.
     */
    @Test
    void permissionsSupportDisableIsNeverWritten() throws Exception
    {
        writeExisting("timeout-shutdown: 42");

        ConfigurationYAML.writeCurrentConfiguration(cfg);

        final List<String> lines = Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8);
        assertFalse(lines.stream().anyMatch(l -> l.startsWith("permissions-support-disable:")),
            "no line is invented for it: " + lines);
    }

    /** And an admin who has set it keeps it. */
    @Test
    void anExistingPermissionsSupportDisableLineSurvives() throws Exception
    {
        writeExisting(
            "timeout-shutdown: 42",
            "permissions-support-disable: true");

        ConfigurationYAML.writeCurrentConfiguration(cfg);

        assertTrue(written().contains("permissions-support-disable: true"),
            "the admin's own setting is left alone");
    }
}
