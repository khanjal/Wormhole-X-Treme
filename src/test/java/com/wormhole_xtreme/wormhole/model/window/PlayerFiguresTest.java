package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Function;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.profile.PlayerProfile;
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
 * finds the server's. A name misspelt here and in the plugin alike would pass, which is what
 * {@code PaperApiTest} and a real server are for.
 */
class PlayerFiguresTest
{
    /** Spigot's Mannequin, as far as the plugin reaches it. */
    interface SpigotMannequin extends LivingEntity
    {
        void setPlayerProfile(PlayerProfile profile);

        void setPose(Pose pose);

        void setImmovable(boolean immovable);

        void setHideDescription(boolean hide);

        void setMainHand(MainHand hand);
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

    private Function<Player, ItemStack> heads;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        heads = PlayerFigures.heads;
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PlayerFigures.heads = heads;
        PlayerFigures.mannequinFromServer();
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
     * Spigot's Mannequin wears the player's skin and name, its "Mannequin" label hidden, can not be
     * pushed, and stands as the player does, with their main hand.
     */
    @Test
    void onSpigotTheMannequinWearsTheirSkinNameHandAndPoseWithItsLabelHidden() throws ReflectiveOperationException
    {
        PlayerFigures.mannequinWith(SpigotMannequin.class);
        final Player player = player("Alex");
        final Object profile = profileOf(player);
        when(player.getPose()).thenReturn(Pose.SNEAKING);
        when(player.getMainHand()).thenReturn(MainHand.LEFT);
        final SpigotMannequin copy = mock(SpigotMannequin.class);

        PlayerFigures.dress(copy, player);

        verify(copy).setCustomName("Alex");
        verify(copy).setCustomNameVisible(true);
        verify(copy).setPlayerProfile((PlayerProfile) profile);
        verify(copy).setHideDescription(true);
        verify(copy).setImmovable(true);
        verify(copy).setMainHand(MainHand.LEFT);
        verify(copy).setPose(Pose.SNEAKING);
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

        PlayerFigures.dress(copy, player);

        verify(copy).setProfile(new Resolvable(profile));
        verify(copy).setDescription(null);
        verify(copy).setImmovable(true);
        verify(copy).setMainHand(MainHand.LEFT);
        verify(copy).setPose(Pose.SWIMMING, true);
        verify(copy).setCustomName("Alex");
    }

    /**
     * Before 1.21.9 the stand-in is an armour stand dressed as a person: seen, arms out, no base plate,
     * full size, not a marker, wearing the player's head and named.
     */
    @Test
    void beforeAMannequinTheArmourStandIsSeenArmedFullSizeAndWearsTheirHead()
    {
        PlayerFigures.mannequinWith(null);
        final Player player = player("Alex");
        final ItemStack head = mock(ItemStack.class);
        PlayerFigures.heads = who -> (who == player) ? head : null;
        final ArmorStand stand = mock(ArmorStand.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(stand.getEquipment()).thenReturn(worn);

        PlayerFigures.dress(stand, player);

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
        PlayerFigures.heads = who ->
        {
            throw new IllegalStateException("no item factory");
        };
        final ArmorStand stand = mock(ArmorStand.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(stand.getEquipment()).thenReturn(worn);

        PlayerFigures.dress(stand, player("Alex"));

        verify(worn, never()).setHelmet(any());
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
        final PlayerFigures.Look look = PlayerFigures.Look.of(player);
        final SpigotMannequin copy = mock(SpigotMannequin.class);

        PlayerFigures.keepInStep(copy, player, look);
        verify(copy, never()).setPose(any());

        when(player.getPose()).thenReturn(Pose.SNEAKING);
        PlayerFigures.keepInStep(copy, player, look);
        PlayerFigures.keepInStep(copy, player, look);

        verify(copy, times(1)).setPose(Pose.SNEAKING);
    }

    /**
     * What a player wears and holds is looked at once every {@link PlayerFigures#WORN_EVERY} follows,
     * and put on the stand-in only when it changed; an armour stand keeps the head it wears.
     */
    @Test
    void wornItemsAreLookedAtOnceASecondAndPutOnOnlyWhenChanged()
    {
        PlayerFigures.mannequinWith(null);
        final Player player = player("Alex");
        final EntityEquipment theirs = mock(EntityEquipment.class);
        when(player.getEquipment()).thenReturn(theirs);
        final ItemStack sword = mock(ItemStack.class);
        final ItemStack helmet = mock(ItemStack.class);
        final PlayerFigures.Look look = PlayerFigures.Look.of(player);
        final ArmorStand stand = mock(ArmorStand.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(stand.getEquipment()).thenReturn(worn);

        when(theirs.getItemInMainHand()).thenReturn(sword);
        when(theirs.getHelmet()).thenReturn(helmet);
        for (int follow = 1; follow < PlayerFigures.WORN_EVERY; follow++)
        {
            PlayerFigures.keepInStep(stand, player, look);
        }
        verify(worn, never()).setItemInMainHand(any());

        PlayerFigures.keepInStep(stand, player, look);
        verify(worn).setItemInMainHand(sword);
        verify(worn, never()).setHelmet(any());

        for (int follow = 0; follow < PlayerFigures.WORN_EVERY; follow++)
        {
            PlayerFigures.keepInStep(stand, player, look);
        }
        verify(worn, times(1)).setItemInMainHand(sword);
    }

    /** A Mannequin, unlike an armour stand, wears the helmet the player wears. */
    @Test
    void aMannequinPutsOnTheHelmetTheyWear()
    {
        PlayerFigures.mannequinWith(SpigotMannequin.class);
        final Player player = player("Alex");
        final EntityEquipment theirs = mock(EntityEquipment.class);
        when(player.getEquipment()).thenReturn(theirs);
        final PlayerFigures.Look look = PlayerFigures.Look.of(player);
        final SpigotMannequin copy = mock(SpigotMannequin.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(copy.getEquipment()).thenReturn(worn);
        final ItemStack helmet = mock(ItemStack.class);
        when(theirs.getHelmet()).thenReturn(helmet);

        for (int follow = 0; follow < PlayerFigures.WORN_EVERY; follow++)
        {
            PlayerFigures.keepInStep(copy, player, look);
        }

        verify(worn).setHelmet(helmet);
    }

    /**
     * On whichever API this is built against, the plugin finds every Mannequin method it calls by
     * name, or finds no Mannequin where the API has none: a misspelt name would show players as
     * nameless default skins, and nothing else would notice.
     *
     * <p>Spigot's and Paper's Mannequins differ, so each is checked for its own: CI runs this against
     * both, from 1.20 to 26.x.
     */
    @Test
    void theApisOwnMannequinHasEveryMethodThePluginCalls()
    {
        PlayerFigures.mannequinFromServer();
        final PlayerFigures.Mannequins found = PlayerFigures.mannequins();
        Class<?> api;
        try
        {
            api = Class.forName(PlayerFigures.MANNEQUIN);
        }
        catch (final ClassNotFoundException absent)
        {
            api = null;
        }
        if (api == null)
        {
            assertNull(found, "no Mannequin before 1.21.9");
            assertSame(ArmorStand.class, PlayerFigures.kind());
            return;
        }
        assertSame(api, PlayerFigures.kind());
        assertNotNull(found.immovable, "setImmovable");
        assertNotNull(found.mainHand, "setMainHand");
        if (found.playerProfile != null)
        {
            assertNotNull(found.hideDescription, "Spigot's setHideDescription");
            assertNotNull(found.pose, "Spigot's setPose(Pose)");
            assertNotNull(found.modelPart, "Spigot's setModelPartShown");
            assertNotNull(found.partShown, "Spigot's Player.isModelPartShown");
        }
        else
        {
            assertNotNull(found.profile, "Paper's setProfile");
            assertNotNull(found.resolve, "Paper's ResolvableProfile.resolvableProfile");
            assertNotNull(found.description, "Paper's setDescription");
            assertNotNull(found.fixedPose, "Paper's setPose(Pose, boolean)");
        }
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
