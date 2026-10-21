package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.ShapeFileValidator;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole gate shapes} says, read by key from {@code lang/} (#26).
 *
 * <p>The registry is mocked, so nothing touches the shapes folder: each answer it can give (a valid
 * file, one with no, one or two problems, a switch that could not be saved) is set up and sent through
 * the real command, to an English admin against the concatenation the code built before the move, and
 * to a qq admin, so a call site reading the wrong recipient shows as English. The problem count's
 * plural is checked either side of one.
 */
class GateShapesMessagesTest
{
    private static final String TOLLAN = "Tollan";
    private static final String FILE = "Tollan.shape";
    private static final String BAD_ROW = "bad row";
    private static final String NO_DHD = "no DHD";
    private static final String DISABLE = "disable";
    private static final String ENABLE = "enable";
    private static final String VALIDATE = "validate";
    private static final String RELOAD = "reload";
    private static final String NOWHERE = "Nowhere";

    private MockedStatic<StargateShapeRegistry> registry;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        registry = mockStatic(StargateShapeRegistry.class);
        final ConcurrentMap<String, StargateShape> loaded = new ConcurrentHashMap<>(Map.of(TOLLAN, new StargateShape()));
        registry.when(StargateShapeRegistry::getStargateShapes).thenReturn(loaded);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        registry.close();
        PluginTestSupport.remove();
    }

    private static void shapes(final CommandSender sender, final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "gate";
        args[1] = "shapes";
        System.arraycopy(rest, 0, args, 2, rest.length);
        new GateShapesCommand().execute(sender, args);
    }

    private static ShapeFileValidator.Result result(final boolean valid, final List<String> problems)
    {
        final ShapeFileValidator.Result result = mock(ShapeFileValidator.Result.class);
        when(result.isValid()).thenReturn(valid);
        when(result.getShapeName()).thenReturn(TOLLAN);
        when(result.getProblems()).thenReturn(problems);
        return result;
    }

    /** An unknown action, an unloaded name, both switches, one unsaved, and a reload of everything. */
    private void switchAndReload(final Player player)
    {
        registry.when(() -> StargateShapeRegistry.setEnabled(anyString(), anyBoolean())).thenReturn(Boolean.TRUE);
        shapes(player, "frobnicate");
        shapes(player, DISABLE, NOWHERE);
        shapes(player, ENABLE, "tollan");
        shapes(player, DISABLE, TOLLAN);
        registry.when(() -> StargateShapeRegistry.setEnabled(anyString(), anyBoolean())).thenReturn(Boolean.FALSE);
        shapes(player, DISABLE, TOLLAN);
        shapes(player, RELOAD);
    }

    /** Validates and reloads a good file, then a file with one problem, two, and none it could name. */
    private void checkFiles(final Player player)
    {
        // Each result built before its stubbing starts: a mock stubbed inside thenReturn throws.
        final ShapeFileValidator.Result good = result(true, List.of());
        final ShapeFileValidator.Result one = result(false, List.of(BAD_ROW));
        final ShapeFileValidator.Result two = result(false, List.of(BAD_ROW, NO_DHD));
        final ShapeFileValidator.Result none = result(false, List.of());
        registry.when(() -> StargateShapeRegistry.validateShapeFile(FILE)).thenReturn(good);
        registry.when(() -> StargateShapeRegistry.reloadShapeFile(FILE)).thenReturn(good);
        shapes(player, VALIDATE, TOLLAN);
        shapes(player, RELOAD, TOLLAN);
        registry.when(() -> StargateShapeRegistry.validateShapeFile(FILE)).thenReturn(one);
        registry.when(() -> StargateShapeRegistry.reloadShapeFile(FILE)).thenReturn(two);
        shapes(player, VALIDATE, TOLLAN);
        shapes(player, RELOAD, TOLLAN);
        registry.when(() -> StargateShapeRegistry.validateShapeFile(FILE)).thenReturn(none);
        shapes(player, VALIDATE, TOLLAN);
    }

    /** The old report's opening, copied from the code before the move. */
    private static String oldFound(final int size, final boolean wasReload)
    {
        return ChatText.error(ChatText.name(FILE) + ": " + ChatText.bad(size + " problem" + (size == 1 ? "" : "s"))
            + " found" + (wasReload ? "; the version loaded before is unchanged." : "."));
    }

    @Test
    void switchingAndReloadingReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        switchAndReload(player);

        verify(player).sendMessage(ChatText.error("No such shapes command: " + ChatText.command("frobnicate") + ". Try "
            + ChatText.command(RELOAD) + ", " + ChatText.command(VALIDATE) + ", " + ChatText.command(ENABLE)
            + " or " + ChatText.command(DISABLE) + "."));
        verify(player).sendMessage(ChatText.error("No shape called " + ChatText.name(NOWHERE) + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(TOLLAN) + " can be built again."));
        verify(player, times(2)).sendMessage(ChatText.info(ChatText.name(TOLLAN)
            + " can no longer be built. Gates already standing keep working."));
        verify(player).sendMessage(ChatText.error("It could not be saved, so a restart brings it back. See the console."));
        verify(player).sendMessage(ChatText.info("Reloaded every shape in the shapes/gate directory."));
    }

    @Test
    void switchingAndReloadingAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        switchAndReload(player);

        verify(player).sendMessage(ChatText.error("QQ no action " + ChatText.command("frobnicate") + " "
            + ChatText.command(RELOAD) + " " + ChatText.command(VALIDATE) + " " + ChatText.command(ENABLE) + " "
            + ChatText.command(DISABLE) + "."));
        verify(player).sendMessage(ChatText.error("QQ no shape " + ChatText.name(NOWHERE) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(TOLLAN) + " enabled."));
        verify(player, times(2)).sendMessage(ChatText.info("QQ " + ChatText.name(TOLLAN) + " disabled."));
        verify(player).sendMessage(ChatText.error("QQ not saved."));
        verify(player).sendMessage(ChatText.info("QQ reloaded all."));
    }

    @Test
    void fileReportsReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        checkFiles(player);

        // In order: validate says it is fine, and only reload says what it loaded.
        final InOrder said = inOrder(player);
        said.verify(player).sendMessage(ChatText.info(ChatText.name(FILE) + ": " + ChatText.good("OK") + "."));
        said.verify(player).sendMessage(ChatText.info(ChatText.name(FILE) + ": " + ChatText.good("OK") + ", loaded as "
            + ChatText.name(TOLLAN) + "."));
        verify(player).sendMessage(oldFound(1, false));
        verify(player).sendMessage(oldFound(2, true));
        verify(player).sendMessage(oldFound(0, false));
        verify(player, times(2)).sendMessage(ChatText.info("  - " + BAD_ROW));
        verify(player).sendMessage(ChatText.info("  - " + NO_DHD));
    }

    @Test
    void fileReportsAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        checkFiles(player);

        final InOrder said = inOrder(player);
        said.verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(FILE) + " " + ChatText.good("QQok") + "."));
        said.verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(FILE) + " " + ChatText.good("QQok") + " as "
            + ChatText.name(TOLLAN) + "."));
        verify(player).sendMessage(ChatText.error("QQ " + ChatText.name(FILE) + " " + ChatText.bad("QQ1one") + "."));
        verify(player).sendMessage(ChatText.error("QQ " + ChatText.name(FILE) + " " + ChatText.bad("QQ2other") + " kept."));
        verify(player).sendMessage(ChatText.error("QQ " + ChatText.name(FILE) + " " + ChatText.bad("QQ0other") + "."));
        verify(player, times(2)).sendMessage(ChatText.info("QQ - bad row"));
        verify(player).sendMessage(ChatText.info("QQ - no DHD"));
    }
}
