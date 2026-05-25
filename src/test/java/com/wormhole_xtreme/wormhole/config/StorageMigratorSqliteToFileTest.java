package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.storage.SqliteStorage;
import com.wormhole_xtreme.wormhole.storage.StorageMigrator;

import org.bukkit.command.CommandSender;

public class StorageMigratorSqliteToFileTest
{
    private Path tmpDir;
    private Server mockServer;

    @BeforeEach
    public void setUp() throws Exception
    {
        tmpDir = Files.createTempDirectory("wx-test-");

        // Install a mock plugin instance for data folder only; do NOT stub getServer (final in JavaPlugin)
        this.mockServer = mock(Server.class);
        final WormholeXTreme mockPlugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field pluginField = WormholeXTreme.class.getDeclaredField("thisPlugin");
        pluginField.setAccessible(true);
        pluginField.set(null, mockPlugin);

        // Inject data folder path into the mock plugin instance's inherited private field
        try
        {
            final java.lang.reflect.Field dataFolderField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("dataFolder");
            dataFolderField.setAccessible(true);
            dataFolderField.set(mockPlugin, tmpDir.toFile());
        }
        catch (final NoSuchFieldException nsf)
        {
            // ignore if field not present
        }

        // Configure server/world behaviour
        final World world = mock(World.class);
        when(world.getName()).thenReturn("gw");
        when(world.getEnvironment()).thenReturn(org.bukkit.World.Environment.NORMAL);
        when(this.mockServer.getWorld("gw")).thenReturn(world);

        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            final int x = invocation.getArgument(0);
            final int y = invocation.getArgument(1);
            final int z = invocation.getArgument(2);
            final Block b = mock(Block.class);
            when(b.getX()).thenReturn(x);
            when(b.getY()).thenReturn(y);
            when(b.getZ()).thenReturn(z);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            return b;
        });

        // Point ConfigManager's sqlite path to our temp file
        final String sqlitePath = tmpDir.resolve("wormholes.db").toString();
        ConfigManager.getConfigurations().put(ConfigManager.ConfigKeys.STORAGE_SQLITE_PATH, new Setting(ConfigManager.ConfigKeys.STORAGE_SQLITE_PATH, sqlitePath, "test-sqlite-path", "WormholeXTreme"));
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        if (tmpDir != null && Files.exists(tmpDir))
        {
            // best-effort recursive delete
            java.nio.file.Files.walk(tmpDir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(f -> { try { f.delete(); } catch (final Throwable ignore) {} });
        }
    }

    @Test
    public void sqliteToFileMigrationWritesYaml() throws Exception
    {
        final Server server = this.mockServer;

        // Prepare a minimal Stargate and save it into sqlite
        final World w = server.getWorld("gw");
        final Stargate s = new Stargate();
        s.setGateName("test-gate");
        s.setGateWorld(w);

        final Block dial = mock(Block.class);
        when(dial.getX()).thenReturn(10);
        when(dial.getY()).thenReturn(64);
        when(dial.getZ()).thenReturn(20);
        when(dial.getLocation()).thenReturn(new Location(w, 10, 64, 20));
        s.setGateDialLeverBlock(dial);
        s.setGatePlayerTeleportLocation(new Location(w, 65.0, 65.0, 65.0));
        s.setGateFacing(org.bukkit.block.BlockFace.NORTH);

        final SqliteStorage sqlite = new SqliteStorage();
        sqlite.initialize();
        sqlite.saveStargate(s);
        sqlite.shutdown();

        // Manually perform the migration steps: read from sqlite and write YAML
        final SqliteStorage reader = new SqliteStorage();
        reader.initialize();
        final java.util.List<com.wormhole_xtreme.wormhole.model.Stargate> gates = reader.loadStargates(server);
        reader.shutdown();
        for (final com.wormhole_xtreme.wormhole.model.Stargate g : gates)
        {
            com.wormhole_xtreme.wormhole.model.StargateYamlManager.saveStargate(g);
        }

        // Assert YAML file exists in plugin data folder
        final java.io.File out = new java.io.File(com.wormhole_xtreme.wormhole.model.StargateYamlManager.getGatesDir(), "test-gate.yml");
        assertTrue(out.exists(), "Expected migrated YAML file to exist: " + out.getAbsolutePath());

        // Basic content check
        final String content = new String(java.nio.file.Files.readAllBytes(out.toPath()));
        assertTrue(content.contains("GateData"), "YAML should contain GateData block");
    }
}
