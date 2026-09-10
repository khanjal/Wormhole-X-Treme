package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.events.StargatePlayerTravelEvent;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;

/**
 * What a gate refuses you, in what order, and when it takes your money.
 *
 * <p>Walking into a gate runs a sequence of refusals -- permission, cooldown, having just come
 * back through it, the fare, a shut iris at the far end, and whether the server allows crossing
 * worlds -- and then asks other plugins whether they object. None of that order was covered by
 * anything.
 *
 * <p>The order is the design, not an accident of how it was written. A player who cannot afford
 * the trip is told that rather than being bounced off an iris they were never going to reach,
 * because the reason they are given is the one they can act on. And the money moves last of
 * all: the fare is worked out early so the refusal comes in the right order, and taken only
 * once the trip has actually happened. A player charged for a journey a listener then cancelled
 * has no way to argue about it.
 */
class GateTollAndRefusalOrderTest
{
    private static final String WORLD = "world";
    private static final String FAR_WORLD = "nether";
    private static final int BX = 10, BY = 64, BZ = 20;
    private static final double FARE = 25.0;

    private World world;
    private World farWorld;
    private Block portal;
    private Player walker;
    private Stargate src;
    private Stargate dst;
    private MockedStatic<ConfigManager> config;
    private MockedStatic<EconomySupport> economy;
    private MockedStatic<StargateRestrictions> restrictions;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        set("thisPlugin", mock(WormholeXTreme.class));
        set("scheduler", mock(org.bukkit.scheduler.BukkitScheduler.class));

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        farWorld = mock(World.class);
        when(farWorld.getName()).thenReturn(FAR_WORLD);

        portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getX()).thenReturn(Integer.valueOf(BX));
        when(portal.getY()).thenReturn(Integer.valueOf(BY));
        when(portal.getZ()).thenReturn(Integer.valueOf(BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(portal);

        src = new Stargate();
        src.setGateName("src");
        src.setGateActive(true);
        dst = new Stargate();
        dst.setGateName("dst");
        dst.setGateFacing(BlockFace.EAST);
        dst.setGatePlayerTeleportLocation(new Location(world, 100.5, 70.0, 200.5));
        StargateTestSupport.target(src, dst);

        StargateManager.addBlockIndex(portal, src);
        src.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));

        walker = mock(Player.class);
        when(walker.getName()).thenReturn("walker");
        when(walker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(walker.getWorld()).thenReturn(world);
        when(walker.getMaximumAir()).thenReturn(Integer.valueOf(300));
        when(walker.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        when(walker.isOp()).thenReturn(Boolean.TRUE);

        config = mockStatic(ConfigManager.class);
        // Off by default: each test turns on the one rule it is about, so a refusal can only
        // have come from that rule.
        config.when(ConfigManager::getWormholeUseIsTeleport).thenReturn(Boolean.FALSE);
        config.when(ConfigManager::isUseCooldownEnabled).thenReturn(Boolean.FALSE);
        config.when(ConfigManager::isEconomyEnabled).thenReturn(Boolean.FALSE);
        config.when(ConfigManager::isSameWorldOnly).thenReturn(Boolean.FALSE);
        config.when(ConfigManager::getTimeoutShutdown).thenReturn(Integer.valueOf(30));

        economy = mockStatic(EconomySupport.class);
        restrictions = mockStatic(StargateRestrictions.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        restrictions.close();
        economy.close();
        config.close();
        StargateManager.removeBlockIndex(portal);
        GateSpatialIndex.clear();
        GateEvents.setDispatcherForTest(null);
        set("thisPlugin", null);
        set("scheduler", null);
    }

    private static void set(final String name, final Object value) throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    /**
     * Gives the gate to somebody else, so its permission nodes are actually consulted.
     *
     * <p>A gate with no owner is treated as public -- gates built before ownership was
     * recorded have none, and locking everyone out of them would strand them -- so an unowned
     * gate admits anybody however the nodes are set.
     */
    private void ownedBySomebodyElse()
    {
        src.setGateOwner(UUID.randomUUID().toString());
        src.setGateOwnerName("Somebody");
    }

    /** Turns the fare on at {@link #FARE}, with the player able to pay it. */
    private void aFareOf(final double amount, final boolean canAfford)
    {
        config.when(ConfigManager::isEconomyEnabled).thenReturn(Boolean.TRUE);
        config.when(ConfigManager::getEconomyUseCost).thenReturn(Double.valueOf(amount));
        economy.when(EconomySupport::isAvailable).thenReturn(Boolean.TRUE);
        economy.when(() -> EconomySupport.canAfford(walker, amount)).thenReturn(Boolean.valueOf(canAfford));
        economy.when(() -> EconomySupport.currencyName(anyDouble())).thenReturn("credits");
    }

    /** One step that ends inside the gate's portal block. */
    private void walkIn()
    {
        new WormholeXTremePlayerListener().onPlayerMove(new PlayerMoveEvent(walker,
            new Location(world, BX + 0.5, BY, BZ - 0.5),
            new Location(world, BX + 0.5, BY, BZ + 0.5)));
    }

    private void nobodyIsCharged()
    {
        economy.verify(() -> EconomySupport.charge(any(Player.class), anyDouble()), never());
    }

    /**
     * A player who may not use the gate is told so and goes nowhere.
     *
     * <p>Which node is asked for depends on how the gate is dialled, and only when the server
     * treats walking through as using it at all.
     */
    @Test
    void aPlayerWhoMayNotUseTheGateIsRefused()
    {
        config.when(ConfigManager::getWormholeUseIsTeleport).thenReturn(Boolean.TRUE);
        ownedBySomebodyElse();
        when(walker.isOp()).thenReturn(Boolean.FALSE);
        when(walker.hasPermission(anyString())).thenReturn(Boolean.FALSE);

        walkIn();

        verify(walker, atLeastOnce()).sendMessage(contains("permission"));
        verify(walker, never()).teleport(any(Location.class));
    }

    /**
     * A gate nobody owns admits anybody, whatever their nodes say.
     *
     * <p>Gates built before ownership was recorded have no owner, and treating those as
     * private would strand every one of them behind a permission their builder never had to
     * hold. Worth pinning because it reads like a hole and is a decision.
     */
    @Test
    void aGateNobodyOwnsIsPublic()
    {
        config.when(ConfigManager::getWormholeUseIsTeleport).thenReturn(Boolean.TRUE);
        when(walker.isOp()).thenReturn(Boolean.FALSE);
        when(walker.hasPermission(anyString())).thenReturn(Boolean.FALSE);

        walkIn();

        verify(walker, never()).sendMessage(contains("permission"));
    }

    /**
     * Permission is asked about before the cooldown.
     *
     * <p>A player who has neither is told they may not use the gate, which is the thing they
     * would have to do something about. Telling them to wait thirty seconds for a gate they
     * will still not be allowed to use is a wasted thirty seconds.
     */
    @Test
    void permissionIsTheReasonGivenWhenBothWouldRefuse()
    {
        config.when(ConfigManager::getWormholeUseIsTeleport).thenReturn(Boolean.TRUE);
        config.when(ConfigManager::isUseCooldownEnabled).thenReturn(Boolean.TRUE);
        ownedBySomebodyElse();
        when(walker.isOp()).thenReturn(Boolean.FALSE);
        when(walker.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        restrictions.when(() -> StargateRestrictions.isPlayerUseCooldown(walker)).thenReturn(Boolean.TRUE);

        walkIn();

        verify(walker, atLeastOnce()).sendMessage(contains("permission"));
        verify(walker, never()).sendMessage(contains("cooldown"));
    }

    /** A player still on cooldown is told how long is left. */
    @Test
    void aPlayerStillOnCooldownIsToldHowLongIsLeft()
    {
        config.when(ConfigManager::isUseCooldownEnabled).thenReturn(Boolean.TRUE);
        restrictions.when(() -> StargateRestrictions.isPlayerUseCooldown(walker)).thenReturn(Boolean.TRUE);
        restrictions.when(() -> StargateRestrictions.checkPlayerUseCooldownRemaining(walker))
            .thenReturn(Long.valueOf(28L));

        walkIn();

        verify(walker, atLeastOnce()).sendMessage(contains("28"));
        verify(walker, never()).teleport(any(Location.class));
    }

    /**
     * An iris raised after the wormhole opened still turns a traveller back.
     *
     * <p>Dialling checks the far iris once, before connecting, and refuses. That is not the
     * only moment it can matter: the gates stay connected afterwards, and whoever is at the far
     * end can raise the iris while somebody is walking towards the portal. The check that
     * counts is therefore the one on the way through, not the one at dial time.
     *
     * <p>They are put back where they started rather than being stopped at the threshold, and
     * given five ticks of damage immunity so the return trip cannot hurt them.
     */
    @Test
    void anIrisRaisedAfterTheGatesConnectedTurnsATravellerBack()
    {
        // Where a bounced traveller is put back. No other test reaches that path, so the
        // fixture had never needed the source gate to have one.
        final Location home = new Location(world, BX + 0.5, BY, BZ - 0.5);
        src.setGatePlayerTeleportLocation(home);
        dst.setGateIrisActive(true);

        walkIn();

        verify(walker, atLeastOnce()).sendMessage(contains("Remote Iris is locked"));
        verify(walker).teleport(home);
        verify(walker).setNoDamageTicks(5);
    }

    /** And is not charged for the journey they did not make. */
    @Test
    void aTravellerTurnedBackByAnIrisPaysNothing()
    {
        src.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ - 0.5));
        aFareOf(FARE, true);
        dst.setGateIrisActive(true);

        walkIn();

        nobodyIsCharged();
    }

    /**
     * A player who cannot afford the trip is told that, and not bounced off the iris.
     *
     * <p>The order the reasons come in is the point. The far end being shut is not something
     * the player can do anything about; being short of the fare is.
     */
    @Test
    void beingUnableToAffordTheFareIsSaidBeforeAShutIrisIs()
    {
        aFareOf(FARE, false);
        dst.setGateIrisActive(true);

        walkIn();

        verify(walker, atLeastOnce()).sendMessage(contains(
            ConfigManager.MessageStrings.ECONOMY_INSUFFICIENT_FUNDS.toString()));
        nobodyIsCharged();
    }

    /**
     * A player refused by another plugin is not charged.
     *
     * <p>The reason the fare is worked out early and taken late. Charged at the check, a
     * traveller pays for a journey a listener then cancels, and there is nothing they can say
     * about it afterwards.
     */
    @Test
    void aPlayerAListenerTurnsBackIsNotCharged()
    {
        aFareOf(FARE, true);
        GateEvents.setDispatcherForTest(e -> ((StargatePlayerTravelEvent) e).setCancelled(true));

        walkIn();

        nobodyIsCharged();
    }

    /** Nor is one refused because the server will not carry them between worlds. */
    @Test
    void aPlayerRefusedForCrossingWorldsIsNotCharged()
    {
        aFareOf(FARE, true);
        config.when(ConfigManager::isSameWorldOnly).thenReturn(Boolean.TRUE);
        dst.setGatePlayerTeleportLocation(new Location(farWorld, 100.5, 70.0, 200.5));

        walkIn();

        verify(walker, atLeastOnce()).sendMessage(contains("Cross-world travel is disabled"));
        nobodyIsCharged();
    }

    /**
     * The setting restricts crossing worlds, not travelling at all.
     *
     * <p>Both ends in one world is the ordinary case and by far the common one. A rule that
     * refused every trip whenever SAME_WORLD_ONLY was on would take every gate on the server
     * with it, and the server that turned it on is the one least likely to notice quickly.
     */
    @Test
    void sameWorldTravelIsStillAllowedWhenOnlySameWorldIsRequired()
    {
        aFareOf(FARE, true);
        config.when(ConfigManager::isSameWorldOnly).thenReturn(Boolean.TRUE);

        walkIn();

        verify(walker, never()).sendMessage(contains("Cross-world travel is disabled"));
        economy.verify(() -> EconomySupport.charge(walker, FARE));
    }

    /**
     * A server that does not mind carries them, and takes the fare once it has.
     *
     * <p>The other half of the rule: {@code SAME_WORLD_ONLY} off means the far end's world is
     * not consulted at all.
     */
    @Test
    void aServerThatAllowsItCarriesThemAcrossAndChargesThem()
    {
        aFareOf(FARE, true);
        config.when(ConfigManager::isSameWorldOnly).thenReturn(Boolean.FALSE);
        dst.setGatePlayerTeleportLocation(new Location(farWorld, 100.5, 70.0, 200.5));

        walkIn();

        verify(walker, never()).sendMessage(contains("Cross-world travel is disabled"));
        economy.verify(() -> EconomySupport.charge(walker, FARE));
    }

    /** The fare taken is the one the server set, and the player is told what it cost. */
    @Test
    void theFareTakenIsTheOneTheServerSetAndTheTravellerIsToldOfIt()
    {
        aFareOf(FARE, true);

        walkIn();

        economy.verify(() -> EconomySupport.charge(walker, FARE));
        verify(walker, atLeastOnce()).sendMessage(contains("credits"));
    }

    /**
     * A gate that costs nothing charges nothing, rather than charging zero.
     *
     * <p>Not the same thing: a charge of zero is still a transaction, and on some economy
     * plugins still a line in somebody's log.
     */
    @Test
    void aGateThatCostsNothingDoesNotChargeAtAll()
    {
        aFareOf(0.0, true);

        walkIn();

        nobodyIsCharged();
    }

    /** With no economy on the server at all, nobody is asked whether they can pay. */
    @Test
    void withNoEconomyNobodyIsAskedToPay()
    {
        config.when(ConfigManager::isEconomyEnabled).thenReturn(Boolean.TRUE);
        config.when(ConfigManager::getEconomyUseCost).thenReturn(Double.valueOf(FARE));
        economy.when(EconomySupport::isAvailable).thenReturn(Boolean.FALSE);

        walkIn();

        economy.verify(() -> EconomySupport.canAfford(any(Player.class), anyDouble()), never());
        nobodyIsCharged();
    }

    /**
     * Somebody who has just arrived through this gate cannot walk straight back.
     *
     * <p>Its own refusal rather than a silent no-op: being turned away at the door is a
     * cancelled move, not a permitted one that went nowhere.
     */
    @Test
    void walkingStraightBackIntoTheGateYouArrivedFromIsRefused()
    {
        restrictions.when(() -> StargateRestrictions.isPlayerRecentArrivalFrom(walker, src))
            .thenReturn(Boolean.TRUE);
        aFareOf(FARE, true);

        walkIn();

        nobodyIsCharged();
        verify(walker, never()).teleport(any(Location.class));
    }

    /** The traveller is given a few ticks of invulnerability, so the arrival does not hurt. */
    @Test
    void theTravellerArrivesBrieflyInvulnerable()
    {
        walkIn();

        verify(walker, atLeastOnce()).setNoDamageTicks(anyInt());
    }
}
