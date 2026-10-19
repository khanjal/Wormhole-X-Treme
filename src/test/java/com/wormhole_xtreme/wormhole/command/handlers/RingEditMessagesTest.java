package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What ring edit, allow, deny and owner say, read by key from {@code lang/} (#26).
 *
 * <p>Each line is sent through the real command twice. Once to an English player, against the
 * concatenation the code built before the move, copied from it, so a reworded template or a swapped
 * argument shows as a changed message. Once to a player whose client reads the test language qq, so a
 * call site that reads the server's language instead of the player's shows as English.
 */
class RingEditMessagesTest
{
    private static final String WORLD = "world";
    private static final String PAIR_ID = "aaaa0001";
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String RECIPIENT = "11111111-2222-3333-4444-555555555555";
    private static final String ID = ChatText.name(PAIR_ID);
    private static final String GRACE = ChatText.name("Grace");

    private World world;
    private RingPair pair;
    private MockedStatic<RingYamlManager> yaml;
    private MockedStatic<ConfigManager> config;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp()
    {
        RingManager.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        pair = registeredPair(PAIR_ID, OWNER, 0);

        yaml = mockStatic(RingYamlManager.class);
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(0));
        config.when(ConfigManager::getRingDefaultLight).thenReturn(Material.SEA_LANTERN);
        config.when(ConfigManager::getRingDefaultFlash).thenReturn(Material.GLOWSTONE);
        config.when(ConfigManager::getRingDefaultStyle).thenReturn(RingStyle.SEQUENTIAL);

        final OfflinePlayer grace = mock(OfflinePlayer.class);
        when(grace.getUniqueId()).thenReturn(UUID.fromString(RECIPIENT));
        when(grace.getName()).thenReturn("Grace");
        when(grace.hasPlayedBefore()).thenReturn(Boolean.TRUE);
        final OfflinePlayer ghost = mock(OfflinePlayer.class);
        when(ghost.hasPlayedBefore()).thenReturn(Boolean.FALSE);
        bukkit = mockStatic(Bukkit.class);
        answerByName("Grace", grace);
        answerByName("Ghost", ghost);
    }

    @AfterEach
    void tearDown()
    {
        bukkit.close();
        config.close();
        yaml.close();
        RingManager.clear();
    }

    // RingCommand.findPlayer looks players up by name, deprecated or not, so the stub must too.
    @SuppressWarnings("deprecation")
    private void answerByName(final String name, final OfflinePlayer answer)
    {
        bukkit.when(() -> Bukkit.getOfflinePlayer(name)).thenReturn(answer);
    }

    /** A registered pair, its A end at {@code at} 64 {@code at}. */
    private RingPair registeredPair(final String id, final String owner, final int at)
    {
        final Ring a = new Ring(at, 64, at, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        final Ring b = new Ring(at + 200, 64, at + 200, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        final RingPair made = new RingPair(id, WORLD, a, b);
        made.setOwner(owner);
        made.setOwnerName("Justin");
        made.setAccess(RingAccess.PUBLIC);
        RingManager.addPair(made, 4);
        return made;
    }

    /** The pair's owner, whose client reads {@code locale}, standing far from any ring. */
    private Player ownerReading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("Justin");
        when(player.getUniqueId()).thenReturn(UUID.fromString(OWNER));
        when(player.getWorld()).thenReturn(world);
        when(player.isOp()).thenReturn(Boolean.FALSE);
        when(player.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        standAt(player, 500, 64, 500);
        return player;
    }

    private void standAt(final Player player, final int x, final int y, final int z)
    {
        when(player.getLocation()).thenReturn(new Location(world, x, y, z));
    }

    private static void run(final Player player, final String... args)
    {
        new RingCommand().execute(player, args);
    }

    private static void edit(final Player player, final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "ring";
        args[1] = "edit";
        System.arraycopy(rest, 0, args, 2, rest.length);
        run(player, args);
    }

    /** Each way edit refuses, before and after a field is read, against the old expression. */
    @Test
    void theEditRefusalsReadAsTheyDidBefore()
    {
        final Player player = ownerReading("en");

        edit(player, "access", "private");
        edit(player, PAIR_ID, "colour", "red");
        edit(player, PAIR_ID, "name", "Tower");
        edit(player, PAIR_ID, "access", "sideways");
        edit(player, PAIR_ID, "style", "diagonal");
        edit(player, PAIR_ID, "ring", "stone");
        edit(player, PAIR_ID, "built", "stone");
        pair.setOwner(RECIPIENT);
        edit(player, PAIR_ID, "access", "private");

        verify(player).sendMessage(ChatText.error("Stand in a ring, or name a pair by its id."));
        verify(player).sendMessage(ChatText.error("Fields are: " + ChatText.command("ring") + ", "
            + ChatText.command("light") + ", " + ChatText.command("flash") + ", " + ChatText.command("built") + ", "
            + ChatText.command("name") + ", " + ChatText.command("access") + ", " + ChatText.command("style") + ", "
            + ChatText.command("reset") + "."));
        verify(player).sendMessage(ChatText.error("Stand in the ring you want to name. Naming a pair by id "
            + "would call both ends the same thing."));
        verify(player).sendMessage(ChatText.error("Access is " + ChatText.command("public") + " or "
            + ChatText.command("private") + "."));
        verify(player).sendMessage(ChatText.error("Style is " + ChatText.command("fast") + " (rings climb together) or "
            + ChatText.command("slow") + " (one at a time); " + ChatText.command("concurrent") + " and "
            + ChatText.command("sequential") + " work too."));
        verify(player).sendMessage(ChatText.error("The travelling ring has to be a slab, so it can move half a block "
            + "at a time."));
        verify(player).sendMessage(ChatText.error("The laid-in slab has to be a slab, since reset puts it back."));
        verify(player).sendMessage(ChatText.error("That is not your ring pair."));
    }

    /** The same refusals reach a qq player in qq. */
    @Test
    void theEditRefusalsAreInThePlayersLanguage()
    {
        final Player player = ownerReading("qq");

        edit(player, "access", "private");
        edit(player, PAIR_ID, "colour", "red");
        edit(player, PAIR_ID, "name", "Tower");
        edit(player, PAIR_ID, "access", "sideways");
        edit(player, PAIR_ID, "style", "diagonal");
        edit(player, PAIR_ID, "ring", "stone");
        edit(player, PAIR_ID, "built", "stone");
        pair.setOwner(RECIPIENT);
        edit(player, PAIR_ID, "access", "private");

        verify(player).sendMessage(ChatText.error("QQ stand in one."));
        verify(player).sendMessage(ChatText.error("QQ fields " + ChatText.command("ring") + " " + ChatText.command("light")
            + " " + ChatText.command("flash") + " " + ChatText.command("built") + " " + ChatText.command("name") + " "
            + ChatText.command("access") + " " + ChatText.command("style") + " " + ChatText.command("reset") + "."));
        verify(player).sendMessage(ChatText.error("QQ stand in it."));
        verify(player).sendMessage(ChatText.error("QQ access " + ChatText.command("public") + " "
            + ChatText.command("private") + "?"));
        verify(player).sendMessage(ChatText.error("QQ style " + ChatText.command("fast") + " " + ChatText.command("slow")
            + " " + ChatText.command("concurrent") + " " + ChatText.command("sequential") + "?"));
        verify(player).sendMessage(ChatText.error("QQ ring not slab."));
        verify(player).sendMessage(ChatText.error("QQ built not slab."));
        verify(player).sendMessage(ChatText.error("QQ not yours."));
    }

    /** What each setting says once it is changed, against the old expression. */
    @Test
    void theEditConfirmationsReadAsTheyDidBefore()
    {
        final Player player = ownerReading("en");
        standAt(player, 0, 65, 0);

        edit(player, "name", "Tower");
        edit(player, "name", "");
        edit(player, PAIR_ID, "access", "private");
        edit(player, PAIR_ID, "style", "slow");
        edit(player, PAIR_ID, "ring", "oak_slab");
        edit(player, PAIR_ID, "built", "oak_slab");

        verify(player).sendMessage(ChatText.info("This ring is now " + ChatText.name("Tower") + "."));
        verify(player).sendMessage(ChatText.info("Name cleared."));
        verify(player).sendMessage(ChatText.info("Access set to " + ChatText.value("PRIVATE") + "."));
        verify(player).sendMessage(ChatText.info("Style set to " + ChatText.value("SEQUENTIAL") + "."));
        verify(player).sendMessage(ChatText.info("Ring material set to " + ChatText.material("OAK_SLAB") + "."));
        verify(player).sendMessage(ChatText.info("Built material set to " + ChatText.material("OAK_SLAB") + "."));
    }

    /** The same confirmations reach a qq player in qq, a name set told apart from one cleared. */
    @Test
    void theEditConfirmationsAreInThePlayersLanguage()
    {
        final Player player = ownerReading("qq");
        standAt(player, 0, 65, 0);

        edit(player, "name", "Tower");
        edit(player, "name", "");
        edit(player, PAIR_ID, "access", "private");
        edit(player, PAIR_ID, "style", "slow");
        edit(player, PAIR_ID, "ring", "oak_slab");
        edit(player, PAIR_ID, "built", "oak_slab");

        verify(player).sendMessage(ChatText.info("QQ name " + ChatText.name("Tower") + "."));
        verify(player).sendMessage(ChatText.info("QQ name cleared."));
        verify(player).sendMessage(ChatText.info("QQ access set " + ChatText.value("PRIVATE") + "."));
        verify(player).sendMessage(ChatText.info("QQ style set " + ChatText.value("SEQUENTIAL") + "."));
        verify(player).sendMessage(ChatText.info("QQ ring set " + ChatText.material("OAK_SLAB") + "."));
        verify(player).sendMessage(ChatText.info("QQ built set " + ChatText.material("OAK_SLAB") + "."));
    }

    /**
     * The light edits ask {@code Material.isBlock()}, which goes through the block registry from 1.20.6
     * and throws without a server, so they run only where the API answers it, as on the 1.20 build.
     */
    private static boolean registryAnswersIsBlock()
    {
        try
        {
            return Material.STONE.isBlock();
        }
        catch (final Exception | LinkageError e)
        {
            return false;
        }
    }

    /** The light refusal and the pad and flash confirmations, against the old expression. */
    @Test
    void theLightEditsReadAsTheyDidBefore()
    {
        assumeTrue(registryAnswersIsBlock(), "this API asks a server's registry whether a material is a block");
        final Player player = ownerReading("en");

        edit(player, PAIR_ID, "light", "diamond");
        edit(player, PAIR_ID, "light", "sea_lantern");
        edit(player, PAIR_ID, "flash", "redstone_block");

        verify(player).sendMessage(ChatText.error(ChatText.bad("diamond") + " is not a block."));
        verify(player).sendMessage(ChatText.info("Pad light set to " + ChatText.material("SEA_LANTERN") + "."));
        verify(player).sendMessage(ChatText.info("Transport light set to " + ChatText.material("REDSTONE_BLOCK") + "."));
    }

    /** The same light lines reach a qq player in qq, pad told apart from flash. */
    @Test
    void theLightEditsAreInThePlayersLanguage()
    {
        assumeTrue(registryAnswersIsBlock(), "this API asks a server's registry whether a material is a block");
        final Player player = ownerReading("qq");

        edit(player, PAIR_ID, "light", "diamond");
        edit(player, PAIR_ID, "light", "sea_lantern");
        edit(player, PAIR_ID, "flash", "redstone_block");

        verify(player).sendMessage(ChatText.error("QQ " + ChatText.bad("diamond") + " not block."));
        verify(player).sendMessage(ChatText.info("QQ pad set " + ChatText.material("SEA_LANTERN") + "."));
        verify(player).sendMessage(ChatText.info("QQ flash set " + ChatText.material("REDSTONE_BLOCK") + "."));
    }

    /** Reset of the end underfoot and of a pair named by id, against the old expression. */
    @Test
    void resetReadsAsItDidBeforeForOneEndAndForBoth()
    {
        final Player player = ownerReading("en");
        pair.getEndA().setBuiltMaterial(Material.OAK_SLAB);

        edit(player, PAIR_ID, "reset");
        standAt(player, 0, 65, 0);
        edit(player, "reset");

        final String tail = ", " + ChatText.material("SEA_LANTERN") + " pad, " + ChatText.material("GLOWSTONE")
            + " flash, " + ChatText.value("SEQUENTIAL") + " deploy";
        verify(player).sendMessage(ChatText.info("Reset to " + "the slab each end was laid in" + tail + " for both ends."));
        verify(player).sendMessage(ChatText.info("Reset to " + ChatText.material("OAK_SLAB") + " rings" + tail
            + " for this end."));
        verify(player, times(2))
            .sendMessage(ChatText.info("Access, allow list, names and owner are untouched."));
    }

    /** The same reset lines reach a qq player in qq, one end told apart from both. */
    @Test
    void resetIsInThePlayersLanguage()
    {
        final Player player = ownerReading("qq");
        pair.getEndA().setBuiltMaterial(Material.OAK_SLAB);

        edit(player, PAIR_ID, "reset");
        standAt(player, 0, 65, 0);
        edit(player, "reset");

        final String tail = ChatText.material("SEA_LANTERN") + " " + ChatText.material("GLOWSTONE") + " "
            + ChatText.value("SEQUENTIAL") + ".";
        verify(player).sendMessage(ChatText.info("QQ reset both " + tail));
        verify(player).sendMessage(ChatText.info("QQ reset end " + ChatText.material("OAK_SLAB") + " " + tail));
        verify(player, times(2)).sendMessage(ChatText.info("QQ untouched."));
    }

    /** Allow and deny, each answer either way, and their refusals, against the old expression. */
    @Test
    void allowAndDenyReadAsTheyDidBefore()
    {
        final Player player = ownerReading("en");

        run(player, "ring", "allow", "Grace", PAIR_ID);
        run(player, "ring", "allow", "Grace", PAIR_ID);
        run(player, "ring", "deny", "Grace", PAIR_ID);
        run(player, "ring", "deny", "Grace", PAIR_ID);
        run(player, "ring", "allow", "Ghost", PAIR_ID);
        pair.setOwner(RECIPIENT);
        run(player, "ring", "deny", "Grace", PAIR_ID);

        verify(player).sendMessage(ChatText.info(GRACE + " may now use " + ID + "."));
        verify(player).sendMessage(ChatText.info(GRACE + " could already use it."));
        verify(player).sendMessage(ChatText.info(GRACE + " may no longer use " + ID + "."));
        verify(player).sendMessage(ChatText.info(GRACE + " was not on the list."));
        verify(player).sendMessage(ChatText.error("No player called " + ChatText.name("Ghost") + " has been on this server."));
        verify(player).sendMessage(ChatText.error("That is not your ring pair."));
    }

    /** The same allow and deny lines reach a qq player in qq. */
    @Test
    void allowAndDenyAreInThePlayersLanguage()
    {
        final Player player = ownerReading("qq");

        run(player, "ring", "allow", "Grace", PAIR_ID);
        run(player, "ring", "allow", "Grace", PAIR_ID);
        run(player, "ring", "deny", "Grace", PAIR_ID);
        run(player, "ring", "deny", "Grace", PAIR_ID);
        run(player, "ring", "deny", "Ghost", PAIR_ID);
        pair.setOwner(RECIPIENT);
        run(player, "ring", "allow", "Grace", PAIR_ID);

        verify(player).sendMessage(ChatText.info("QQ allowed " + GRACE + " " + ID + "."));
        verify(player).sendMessage(ChatText.info("QQ already " + GRACE + "."));
        verify(player).sendMessage(ChatText.info("QQ denied " + GRACE + " " + ID + "."));
        verify(player).sendMessage(ChatText.info("QQ not listed " + GRACE + "."));
        verify(player).sendMessage(ChatText.error("QQ never seen " + ChatText.name("Ghost") + "."));
        verify(player).sendMessage(ChatText.error("QQ not yours."));
    }

    /** Handing a pair over, private and public, against the old expression. */
    @Test
    void handingOverReadsAsItDidBefore()
    {
        final Player player = ownerReading("en");

        run(player, "ring", "owner", "Grace", PAIR_ID);
        final RingPair secret = registeredPair("bbbb0002", OWNER, 1000);
        secret.setAccess(RingAccess.PRIVATE);
        run(player, "ring", "owner", "Grace", "bbbb0002");

        verify(player).sendMessage(ChatText.info("Handed " + ID + " to " + GRACE + "."));
        verify(player).sendMessage(ChatText.info("Handed " + ChatText.name("bbbb0002") + " to " + GRACE
            + ". It is private, so you no longer have access to it yourself."));
    }

    /** The same hand-over lines reach a qq player in qq, private told apart from public. */
    @Test
    void handingOverIsInThePlayersLanguage()
    {
        final Player player = ownerReading("qq");

        run(player, "ring", "owner", "Grace", PAIR_ID);
        final RingPair secret = registeredPair("bbbb0002", OWNER, 1000);
        secret.setAccess(RingAccess.PRIVATE);
        run(player, "ring", "owner", "Grace", "bbbb0002");

        verify(player).sendMessage(ChatText.info("QQ handed " + ID + " " + GRACE + "."));
        verify(player).sendMessage(ChatText.info("QQ handed " + ChatText.name("bbbb0002") + " " + GRACE + " private."));
    }

    /**
     * Each way a hand-over is refused, against the old expression. The recipient's limit reads the same
     * at one pair as at two, as it did, "1 ring pairs" and all.
     */
    @Test
    void theOwnerRefusalsReadAsTheyDidBefore()
    {
        final Player player = ownerReading("en");
        refuseEachWay(player);

        verify(player).sendMessage(ChatText.error("No player called " + ChatText.name("Ghost") + " has been on this server."));
        verify(player).sendMessage(ChatText.error(GRACE + " already has " + ChatText.value("1")
            + " ring pairs, which is the limit."));
        verify(player).sendMessage(ChatText.error(GRACE + " already has " + ChatText.value("2")
            + " ring pairs, which is the limit."));
        verify(player).sendMessage(ChatText.error(GRACE + " already owns that pair."));
        verify(player).sendMessage(ChatText.error("That is not your ring pair to give away."));
    }

    /** The same refusals reach a qq player in qq, a limit of one told apart from a limit of two. */
    @Test
    void theOwnerRefusalsAreInThePlayersLanguage()
    {
        final Player player = ownerReading("qq");
        refuseEachWay(player);

        verify(player).sendMessage(ChatText.error("QQ never seen " + ChatText.name("Ghost") + "."));
        verify(player).sendMessage(ChatText.error("QQ " + GRACE + " limit " + ChatText.value("1") + " one."));
        verify(player).sendMessage(ChatText.error("QQ " + GRACE + " limit " + ChatText.value("2") + " other."));
        verify(player).sendMessage(ChatText.error("QQ " + GRACE + " owns it."));
        verify(player).sendMessage(ChatText.error("QQ not yours to give."));
    }

    /** Runs owner into each refusal: a stranger, Grace at a limit of one then two, Grace's own pair, then not ours. */
    private void refuseEachWay(final Player player)
    {
        run(player, "ring", "owner", "Ghost", PAIR_ID);
        registeredPair("cccc0003", RECIPIENT, 2000);
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(1));
        run(player, "ring", "owner", "Grace", PAIR_ID);
        registeredPair("dddd0004", RECIPIENT, 3000);
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(2));
        run(player, "ring", "owner", "Grace", PAIR_ID);
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(0));
        // As an operator, or Grace's pair would be refused as not ours before whose it is came up.
        when(player.isOp()).thenReturn(Boolean.TRUE);
        run(player, "ring", "owner", "Grace", "cccc0003");
        when(player.isOp()).thenReturn(Boolean.FALSE);
        pair.setOwner(UUID.randomUUID().toString());
        run(player, "ring", "owner", "Grace", PAIR_ID);
    }
}
