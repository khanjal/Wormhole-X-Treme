package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * The ground the batch 8 message tests (#26) stand on: a server with the Standard shape and two
 * material groups, readers whose clients speak English or the test language qq, and the words
 * the old code built, as it built them.
 *
 * <p>Chat links are off, so a line with links reaches the reader as the one string it read as
 * before links existed; {@link BuildLinksMessagesTest} turns them on and reads the components.
 */
abstract class BuildPreviewCase
{
    /** The command every preview action follows, as hints print it. */
    static final String PREVIEW = "/wormhole gate preview ";

    /** The actions, in the order they are offered. */
    static final String[] ACTIONS = {"clear", "activate", "iris", "material", "needs", "guide", "layer", "chevrons",
        "dhd", "share", "place"};

    @TempDir
    File tempDir;

    final Wormhole command = new Wormhole();
    Stargate3DShape standard;

    @BeforeEach
    void setUpServer() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        PluginTestSupport.install(plugin);
        standard = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/Standard.shape")).toArray(new String[0]));
        StargateShapeRegistry.getStargateShapes().put("Standard", standard);
        final Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("Standard", Map.of("structure", "OBSIDIAN", "chevron", "REDSTONE_LAMP"));
        groups.put("Atlantis", Map.of("structure", "LAPIS_BLOCK"));
        MaterialGroupRegistry.load(groups);
        ConfigTestSupport.set(ConfigManager.ConfigKeys.CLICKABLE_CHAT, false);
    }

    @AfterEach
    void tearDownServer() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        StargateShapeRegistry.setEnabled("Standard", true);
        StargateShapeRegistry.getStargateShapes().remove("Standard");
        StargateShapeRegistry.getStargateShapes().remove("Odd");
        MaterialGroupRegistry.load(null);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** @return a builder holding every permission, whose client reads {@code locale} */
    static Player reading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOp()).thenReturn(Boolean.TRUE);
        when(player.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        return player;
    }

    /** @return an admin with {@code wormhole.config} and no preview node, whose client reads {@code locale} */
    static Player readingWithoutPreview(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOp()).thenReturn(Boolean.FALSE);
        when(player.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        when(player.hasPermission("wormhole.config")).thenReturn(Boolean.TRUE);
        return player;
    }

    void run(final Player player, final String... args)
    {
        assertTrue(command.onCommand(player, null, "wormhole", args), "every path answers");
    }

    /** A preview command, highlighted, as a message names it. */
    static String hint(final String words)
    {
        return ChatText.command(PREVIEW + words);
    }

    /** What {@code actionList} reads as with nothing clickable: the command, a colon and every action. */
    static String listed()
    {
        return hint("<action>") + ": " + actions();
    }

    /** Every action, white, one space apart. */
    static String actions()
    {
        return Arrays.stream(ACTIONS).map(ChatText::command).collect(Collectors.joining(" "));
    }
}
