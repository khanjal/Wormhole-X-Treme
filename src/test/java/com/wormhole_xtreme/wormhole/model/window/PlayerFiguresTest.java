package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * What a far player's stand-in is and how it is dressed (#296, step 2): a Mannequin in their skin
 * from 1.21.9, an armour stand wearing their head before.
 *
 * <p>The plugin compiles against 1.20, where there is no Mannequin, so these tests install a stand-in
 * type with the same methods as Spigot's or Paper's, and the plugin finds them by name exactly as it
 * finds the server's; {@link #theApisOwnMannequinHasEveryMethodThePluginCallsWithItsTypes} checks
 * the real one on whichever API the build is against.
 */
class PlayerFiguresTest
{
    /** Spigot's skin layers, as far as the plugin reaches them. */
    enum Part
    {
        CAPE, HAT
    }

    /** Spigot's Mannequin, as far as the plugin reaches it. */
    interface SpigotMannequin extends LivingEntity
    {
        void setPlayerProfile(PlayerProfile profile);

        void setPose(Pose pose);

        void setImmovable(boolean immovable);

        void setHideDescription(boolean hide);

        void setMainHand(MainHand hand);

        void setModelPartShown(Part part, boolean shown);
    }

    /** Spigot's player, who says which skin layers they show. */
    interface SkinnedPlayer extends Player
    {
        boolean isModelPartShown(Part part);
    }

    /** Paper's ResolvableProfile, made from a player's profile by a public static factory. */
    record Resolvable(Object from)
    {
        public static Resolvable resolvableProfile(final PlayerProfile profile)
        {
            return new Resolvable(profile);
        }
    }

    /** Paper's Mannequin, as far as the plugin reaches it. */
    interface PaperMannequin extends LivingEntity
    {
        void setProfile(Resolvable profile);

        void setPose(Pose pose, boolean fixed);

        void setImmovable(boolean immovable);

        void setDescription(Object description);

        void setMainHand(MainHand hand);
    }

    private WormholeXTreme plugin;
    private Function<VisibleItems.Visible, ItemStack> items;
    private final List<VisibleItems.Visible> made = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        StandIns.removeEverything();
        items = VisibleItems.items;
        VisibleItems.items = seen ->
        {
            made.add(seen);
            return mock(ItemStack.class);
        };
    }

    @AfterEach
    void tearDown() throws Exception
    {
        VisibleItems.items = items;
        PlayerFigures.mannequinFromServer();
        StandIns.removeEverything();
        PluginTestSupport.remove();
    }

    /**
     * Where the server has a Mannequin, a player's stand-in is one; where it has none, before 1.21.9,
     * an armour stand.
     *
     * <p>Spawning a Mannequin on a server without one would fail every time and show nobody.
     */
    @Test
    void aPlayersStandInIsAMannequinWhereTheServerHasOneAndAnArmourStandBefore()
    {
        PlayerFigures.mannequinWith(SpigotMannequin.class);
        assertSame(SpigotMannequin.class, PlayerFigures.kind());
        assertSame(SpigotMannequin.class, StandIns.kindOf(mock(Player.class)), "a player's stand-in is spawned as it");

        PlayerFigures.mannequinWith(null);
        assertSame(ArmorStand.class, PlayerFigures.kind(), "before 1.21.9");
        assertSame(ArmorStand.class, StandIns.kindOf(mock(Player.class)));
    }

    /**
     * Spigot's Mannequin wears the player's skin and skin layers and their name, its "Mannequin" label
     * hidden, can not be pushed, and stands as the player does, with their main hand.
     */
    @Test
    void onSpigotTheMannequinWearsTheirSkinLayersNameHandAndPoseWithItsLabelHidden() throws ReflectiveOperationException
    {
        PlayerFigures.mannequinWith(SpigotMannequin.class);
        final SkinnedPlayer player = mock(SkinnedPlayer.class);
        when(player.getName()).thenReturn("Alex");
        final Object profile = profileOf(player);
        when(player.getPose()).thenReturn(Pose.SNEAKING);
        when(player.getMainHand()).thenReturn(MainHand.LEFT);
        when(player.isModelPartShown(Part.HAT)).thenReturn(true);
        when(player.isModelPartShown(Part.CAPE)).thenReturn(false);
        final SpigotMannequin copy = mock(SpigotMannequin.class);

        PlayerFigures.dress(copy, player, null);

        verify(copy).setCustomName("Alex");
        verify(copy).setCustomNameVisible(true);
        verify(copy).setPlayerProfile((PlayerProfile) profile);
        verify(copy).setHideDescription(true);
        verify(copy).setImmovable(true);
        verify(copy).setMainHand(MainHand.LEFT);
        verify(copy).setPose(Pose.SNEAKING);
        verify(copy).setModelPartShown(Part.HAT, true);
        verify(copy).setModelPartShown(Part.CAPE, false);
    }

    /**
     * Paper's Mannequin takes a ResolvableProfile made from the player's, hides its label with a null
     * description, and keeps its pose with the fixed form.
     */
    @Test
    void onPaperTheMannequinTakesAResolvedProfileANullLabelAndAFixedPose() throws ReflectiveOperationException
    {
        PlayerFigures.mannequinWith(PaperMannequin.class);
        final Player player = player("Alex");
        final Object profile = profileOf(player);
        when(player.getPose()).thenReturn(Pose.SWIMMING);
        when(player.getMainHand()).thenReturn(MainHand.LEFT);
        final PaperMannequin copy = mock(PaperMannequin.class);

        PlayerFigures.dress(copy, player, null);

        verify(copy).setProfile(new Resolvable(profile));
        verify(copy).setDescription(null);
        verify(copy).setImmovable(true);
        verify(copy).setMainHand(MainHand.LEFT);
        verify(copy).setPose(Pose.SWIMMING, true);
        verify(copy).setCustomName("Alex");
    }

    /**
     * A reflective call that throws is logged and the rest of the dressing goes on: a server whose
     * profile call is broken still gets an immovable, label-less, named figure.
     */
    @Test
    void aReflectiveCallThatThrowsIsLoggedAndTheRestStillDressesIt() throws ReflectiveOperationException
    {
        PlayerFigures.mannequinWith(SpigotMannequin.class);
        final Player player = player("Alex");
        profileOf(player);
        final SpigotMannequin copy = mock(SpigotMannequin.class);
        doThrow(new IllegalStateException("bad profile")).when(copy).setPlayerProfile(any());

        PlayerFigures.dress(copy, player, null);

        verify(copy).setImmovable(true);
        verify(copy).setHideDescription(true);
        verify(copy).setPose(Pose.STANDING);
        verify(copy).setCustomName("Alex");
        verify(plugin).prettyLog(eq(Level.WARNING), eq("Could not dress a far player's stand-in"), any(Throwable.class));
    }

    /**
     * Before 1.21.9 the stand-in is an armour stand dressed as a person: seen, arms out, no base plate,
     * full size, not a marker, wearing a fresh head in the player's skin, and named.
     */
    @Test
    void beforeAMannequinTheArmourStandIsSeenArmedFullSizeAndWearsTheirHead() throws ReflectiveOperationException
    {
        PlayerFigures.mannequinWith(null);
        final Player player = player("Alex");
        final Object profile = profileOf(player);
        final ItemStack head = mock(ItemStack.class);
        VisibleItems.items = seen -> ((seen.type() == Material.PLAYER_HEAD) && (seen.skull() == profile)) ? head : null;
        final ArmorStand stand = mock(ArmorStand.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(stand.getEquipment()).thenReturn(worn);

        PlayerFigures.dress(stand, player, null);

        verify(stand).setVisible(true);
        verify(stand).setArms(true);
        verify(stand).setBasePlate(false);
        verify(stand).setSmall(false);
        verify(stand).setMarker(false);
        verify(worn).setHelmet(head);
        verify(stand).setCustomName("Alex");
        verify(stand).setCustomNameVisible(true);
    }

    /** A head that cannot be made leaves the armour stand without one, not the player unshown. */
    @Test
    void aHeadThatCannotBeMadeLeavesTheArmourStandBareHeaded()
    {
        PlayerFigures.mannequinWith(null);
        VisibleItems.items = seen ->
        {
            throw new IllegalStateException("no item factory");
        };
        final ArmorStand stand = mock(ArmorStand.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(stand.getEquipment()).thenReturn(worn);

        PlayerFigures.dress(stand, player("Alex"), null);

        verify(worn, never()).setHelmet(any());
        verify(stand).setArms(true);
    }

    /**
     * The name is the display name without its colours, so a nickname shows as the nickname, and the
     * account name where there is no display name.
     */
    @Test
    void theNameShownIsTheDisplayNameWithoutColoursOrTheirNameWithoutOne()
    {
        final Player nicked = player("Alex");
        when(nicked.getDisplayName()).thenReturn("§aSir Nick§r");
        assertEquals("Sir Nick", PlayerFigures.shownName(nicked));

        final Player plain = player("Alex");
        when(plain.getDisplayName()).thenReturn("§r ");
        assertEquals("Alex", PlayerFigures.shownName(plain), "a display name of colours alone");
        assertEquals("Alex", PlayerFigures.shownName(player("Alex")), "no display name at all");
    }

    /**
     * The name tag follows the player's team on the viewer's scoreboard, as the real player's does:
     * hidden from everyone with {@code never}, from other teams with {@code hideForOtherTeams}, and
     * from the player's own team with {@code hideForOwnTeam}; shown with no team or no rule.
     */
    @Test
    void theNameTagIsHiddenWhereTheirTeamWouldHideItFromThisViewer()
    {
        final Player player = player("Alex");
        final Player viewer = player("Sam");
        final Scoreboard board = mock(Scoreboard.class);
        when(viewer.getScoreboard()).thenReturn(board);
        final Team theirs = mock(Team.class);
        final Team other = mock(Team.class);
        assertTrue(PlayerFigures.nameTagShown(viewer, player), "no team");

        when(board.getEntryTeam("Alex")).thenReturn(theirs);
        when(theirs.getOption(Team.Option.NAME_TAG_VISIBILITY)).thenReturn(Team.OptionStatus.ALWAYS);
        assertTrue(PlayerFigures.nameTagShown(viewer, player), "always");

        when(theirs.getOption(Team.Option.NAME_TAG_VISIBILITY)).thenReturn(Team.OptionStatus.NEVER);
        assertFalse(PlayerFigures.nameTagShown(viewer, player), "never");

        when(theirs.getOption(Team.Option.NAME_TAG_VISIBILITY)).thenReturn(Team.OptionStatus.FOR_OWN_TEAM);
        when(board.getEntryTeam("Sam")).thenReturn(other);
        assertFalse(PlayerFigures.nameTagShown(viewer, player), "shown to their own team only, and the viewer is not on it");
        when(board.getEntryTeam("Sam")).thenReturn(theirs);
        assertTrue(PlayerFigures.nameTagShown(viewer, player), "shown to their own team, the viewer's");

        when(theirs.getOption(Team.Option.NAME_TAG_VISIBILITY)).thenReturn(Team.OptionStatus.FOR_OTHER_TEAMS);
        assertFalse(PlayerFigures.nameTagShown(viewer, player), "hidden from their own team, the viewer's");
        when(board.getEntryTeam("Sam")).thenReturn(null);
        assertTrue(PlayerFigures.nameTagShown(viewer, player), "shown to other teams, and the viewer is on none");

        when(board.getEntryTeam("Alex")).thenThrow(new IllegalStateException("unregistered"));
        assertFalse(PlayerFigures.nameTagShown(viewer, player), "a rule that cannot be read leaves it nameless");
    }

    /** A stand-in whose name tag the viewer would not see carries no name at all, not a hidden one. */
    @Test
    void aStandInWhoseNameTagIsHiddenCarriesNoName()
    {
        PlayerFigures.mannequinWith(null);
        final Player player = player("Alex");
        final Player viewer = player("Sam");
        final Scoreboard board = mock(Scoreboard.class);
        when(viewer.getScoreboard()).thenReturn(board);
        final Team team = mock(Team.class);
        when(board.getEntryTeam("Alex")).thenReturn(team);
        when(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).thenReturn(Team.OptionStatus.NEVER);
        final ArmorStand stand = mock(ArmorStand.class);

        PlayerFigures.dress(stand, player, viewer);

        verify(stand, never()).setCustomName(anyString());
        verify(stand, never()).setCustomNameVisible(anyBoolean());
        verify(stand).setArms(true);
    }

    /** The poses a mannequin takes are kept; any other shows standing. */
    @Test
    void standingSneakingSwimmingSleepingAndGlidingAreKeptAndAnyOtherStands()
    {
        for (final Pose kept : new Pose[] { Pose.SNEAKING, Pose.SWIMMING, Pose.SLEEPING, Pose.FALL_FLYING })
        {
            assertEquals(kept, PlayerFigures.figurePose(kept));
        }
        assertEquals(Pose.STANDING, PlayerFigures.figurePose(Pose.SPIN_ATTACK), "a riptide spin is not a mannequin pose");
        assertEquals(Pose.STANDING, PlayerFigures.figurePose(Pose.DYING));
        assertEquals(Pose.STANDING, PlayerFigures.figurePose(null));
    }

    /**
     * As it follows, a Mannequin takes a new pose the follow it changes, and not again while it holds:
     * a pose sent every two ticks for nothing would be a packet each time for every viewer.
     */
    @Test
    void aPoseIsCopiedTheFollowItChangesAndOnlyThen()
    {
        PlayerFigures.mannequinWith(SpigotMannequin.class);
        final Player player = player("Alex");
        when(player.getPose()).thenReturn(Pose.STANDING);
        final PlayerFigures.Look look = PlayerFigures.Look.of(player, 0L);
        final SpigotMannequin copy = mock(SpigotMannequin.class);

        PlayerFigures.keepInStep(copy, player, look, 0L);
        verify(copy, never()).setPose(any());

        when(player.getPose()).thenReturn(Pose.SNEAKING);
        PlayerFigures.keepInStep(copy, player, look, 50L);
        PlayerFigures.keepInStep(copy, player, look, 100L);

        verify(copy, times(1)).setPose(Pose.SNEAKING);
    }

    /**
     * What a player wears and holds is looked at once every {@link PlayerFigures#WORN_MILLIS} by the
     * clock, however often the stand-in is placed (a redraw places it up to ten times a second), and
     * put on only when what shows of it changed: a stack whose count changes is the same to look at.
     * An armour stand keeps the head it wears.
     */
    @Test
    void wornItemsAreLookedAtOnceASecondByTheClockAndPutOnOnlyWhenWhatShowsChanged()
    {
        PlayerFigures.mannequinWith(null);
        final Player player = player("Alex");
        final EntityEquipment theirs = mock(EntityEquipment.class);
        when(player.getEquipment()).thenReturn(theirs);
        final PlayerFigures.Look look = PlayerFigures.Look.of(player, 0L);
        final ArmorStand stand = mock(ArmorStand.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(stand.getEquipment()).thenReturn(worn);
        final ItemStack arrows = stack(Material.ARROW, 64);
        final ItemStack helmet = stack(Material.IRON_HELMET, 1);
        when(theirs.getItemInMainHand()).thenReturn(arrows);
        when(theirs.getHelmet()).thenReturn(helmet);

        for (long at = 0L; at < PlayerFigures.WORN_MILLIS; at += 50L)
        {
            PlayerFigures.keepInStep(stand, player, look, at);
        }
        verify(worn, never()).setItemInMainHand(any());

        PlayerFigures.keepInStep(stand, player, look, PlayerFigures.WORN_MILLIS);
        verify(worn, times(1)).setItemInMainHand(any());
        verify(worn, never()).setHelmet(any());
        assertEquals(new VisibleItems.Visible(Material.ARROW, false, null, null, null), look.worn[4]);

        final ItemStack fewer = stack(Material.ARROW, 12);
        when(theirs.getItemInMainHand()).thenReturn(fewer);
        PlayerFigures.keepInStep(stand, player, look, 2 * PlayerFigures.WORN_MILLIS);
        verify(worn, times(1)).setItemInMainHand(any());
    }

    /** A Mannequin, unlike an armour stand, puts on the helmet the player wears. */
    @Test
    void aMannequinPutsOnTheHelmetTheyWear()
    {
        PlayerFigures.mannequinWith(SpigotMannequin.class);
        final Player player = player("Alex");
        final EntityEquipment theirs = mock(EntityEquipment.class);
        when(player.getEquipment()).thenReturn(theirs);
        final PlayerFigures.Look look = PlayerFigures.Look.of(player, 0L);
        final SpigotMannequin copy = mock(SpigotMannequin.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(copy.getEquipment()).thenReturn(worn);
        final ItemStack helmet = stack(Material.DIAMOND_HELMET, 1);
        when(theirs.getHelmet()).thenReturn(helmet);

        PlayerFigures.keepInStep(copy, player, look, PlayerFigures.WORN_MILLIS);

        verify(worn).setHelmet(any());
        assertEquals(Material.DIAMOND_HELMET, made.get(made.size() - 1).type());
    }

    /**
     * On whichever API this is built against, the plugin finds every Mannequin method it calls, with
     * the parameter types it passes, or finds no Mannequin where the API has none: a misspelt name or a
     * wrong overload would show players as nameless default skins, and nothing else would notice.
     *
     * <p>Spigot's and Paper's Mannequins differ, so each is checked for its own: CI runs this against
     * both, from 1.20 to 26.x.
     */
    @Test
    void theApisOwnMannequinHasEveryMethodThePluginCallsWithItsTypes()
    {
        PlayerFigures.mannequinFromServer();
        final PlayerFigures.Mannequins found = PlayerFigures.mannequins();
        final Class<?> api = apiMannequin();
        if (api == null)
        {
            assertNull(found, "no Mannequin before 1.21.9");
            assertSame(ArmorStand.class, PlayerFigures.kind());
            return;
        }
        assertSame(api, PlayerFigures.kind());
        assertTypes(found.immovable, boolean.class);
        assertTypes(found.mainHand, MainHand.class);
        if (found.playerProfile != null)
        {
            assertTypes(found.playerProfile, PlayerProfile.class);
            assertTypes(found.hideDescription, boolean.class);
            assertTypes(found.pose, Pose.class);
            assertNotNull(found.modelPart, "Spigot's setModelPartShown");
            assertEquals(boolean.class, found.modelPart.getParameterTypes()[1]);
            final Class<?> part = found.modelPart.getParameterTypes()[0];
            assertTrue(Arrays.stream(Player.class.getMethods()).anyMatch(method -> "isModelPartShown".equals(method.getName())
                && Arrays.equals(method.getParameterTypes(), new Class<?>[] { part }) && (method.getReturnType() == boolean.class)),
                "Spigot's Player.isModelPartShown takes the Mannequin's part type");
        }
        else
        {
            assertNotNull(found.profile, "Paper's setProfile");
            assertNotNull(found.resolve, "Paper's ResolvableProfile.resolvableProfile");
            assertTrue(found.resolve.getParameterTypes()[0].isAssignableFrom(profileType()),
                "the factory takes what Player.getPlayerProfile gives");
            assertSame(found.profile.getParameterTypes()[0], found.resolve.getReturnType(), "and makes what setProfile takes");
            assertNotNull(found.description, "Paper's setDescription");
            assertFalse(found.description.getParameterTypes()[0].isPrimitive(), "a null hides the label");
            assertTypes(found.fixedPose, Pose.class, boolean.class);
        }
    }

    private static void assertTypes(final Method method, final Class<?>... types)
    {
        assertNotNull(method, "a method the plugin calls");
        assertArrayEquals(types, method.getParameterTypes(), method.getName());
    }

    private static Class<?> apiMannequin()
    {
        try
        {
            return Class.forName(PlayerFigures.MANNEQUIN);
        }
        catch (final ClassNotFoundException absent)
        {
            return null;
        }
    }

    private static Class<?> profileType()
    {
        try
        {
            return Player.class.getMethod("getPlayerProfile").getReturnType();
        }
        catch (final NoSuchMethodException absent)
        {
            throw new AssertionError(absent);
        }
    }

    private static ItemStack stack(final Material type, final int amount)
    {
        final ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    private static Player player(final String name)
    {
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        return player;
    }

    /**
     * A profile for the player, of whatever type this API's {@code getPlayerProfile} returns: Paper's
     * is its own subtype, which this test cannot name and still compile against Spigot.
     */
    private static Object profileOf(final Player player) throws ReflectiveOperationException
    {
        final Object profile = mock(Player.class.getMethod("getPlayerProfile").getReturnType());
        doReturn(profile).when(player).getPlayerProfile();
        return profile;
    }
}
