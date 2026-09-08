package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import com.wormhole_xtreme.wormhole.model.ring.RingPermissions;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;

/**
 * Who may let somebody into a ring pair, and who may hand one over.
 *
 * <p>`allow`, `deny` and `owner` were uncovered between them, and they are the whole of how
 * access to a pair changes hands. Two rules in there are decisions rather than plumbing, and
 * both are the kind a reader would have to work out from the code.
 *
 * <p>The quota is checked against the **recipient** of a transfer, not the giver -- otherwise
 * anybody at their limit could carry on building by having a friend build and hand over. And a
 * pair's previous owner is not kept on its allow list afterwards: staff who build rings for
 * players would otherwise accumulate standing access to every one of them.
 */
class RingOwnershipTransferTest
{
    private static final String WORLD = "world";
    private static final String PAIR_ID = "aaaa0001";
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String RECIPIENT = "11111111-2222-3333-4444-555555555555";

    private Player owner;
    private World world;
    private RingPair pair;
    private OfflinePlayer grace;
    private MockedStatic<RingYamlManager> yaml;
    private MockedStatic<ConfigManager> config;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp()
    {
        RingManager.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);

        owner = mock(Player.class);
        when(owner.getName()).thenReturn("Justin");
        when(owner.getUniqueId()).thenReturn(UUID.fromString(OWNER));
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(new Location(world, 500, 64, 500));
        when(owner.isOp()).thenReturn(Boolean.FALSE);
        when(owner.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        // Owning a pair is not on its own enough to travel by one -- see
        // owningAPairIsNotOnItsOwnEnoughToTravelByIt below. Granted here so the tests about
        // the allow list are measuring the allow list.
        when(owner.hasPermission(RingPermissions.USE)).thenReturn(Boolean.TRUE);

        pair = registeredPair();

        grace = mock(OfflinePlayer.class);
        when(grace.getUniqueId()).thenReturn(UUID.fromString(RECIPIENT));
        when(grace.getName()).thenReturn("Grace");
        when(grace.hasPlayedBefore()).thenReturn(Boolean.TRUE);

        yaml = mockStatic(RingYamlManager.class);
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingReach).thenReturn(Integer.valueOf(4));
        // No limit unless a test sets one: zero is how the config turns the quota off.
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(0));

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayerExact("Grace")).thenReturn(null);
        bukkit.when(() -> Bukkit.getOfflinePlayer("Grace")).thenReturn(grace);
    }

    @AfterEach
    void tearDown()
    {
        bukkit.close();
        config.close();
        yaml.close();
        RingManager.clear();
    }

    private RingPair registeredPair()
    {
        final Ring a = new Ring(0, 64, 0, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final RingPair made = new RingPair(PAIR_ID, WORLD, a, b);
        made.setOwner(OWNER);
        made.setOwnerName("Justin");
        made.setAccess(RingAccess.PRIVATE);
        RingManager.addPair(made, 4);
        return made;
    }

    private boolean run(final Player who, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = "ring";
        System.arraycopy(rest, 0, args, 1, rest.length);
        return new RingCommand().execute(who, args);
    }

    /** Another pair already to Grace's name, so the quota has something to count. */
    private void graceAlreadyOwns(final String id, final int at)
    {
        final RingPair theirs = new RingPair(id, WORLD,
            new Ring(at, 64, at, RingPattern.ODD, RingOrientation.FLOOR,
                Material.STONE_SLAB, Material.GLOWSTONE),
            new Ring(at + 200, 64, at + 200, RingPattern.ODD, RingOrientation.FLOOR,
                Material.STONE_SLAB, Material.GLOWSTONE));
        theirs.setOwner(RECIPIENT);
        RingManager.addPair(theirs, 4);
    }

    /** Somebody who has never been on the server, so `findPlayer` gives nothing back. */
    private void nobodyCalled(final String name)
    {
        final OfflinePlayer invented = mock(OfflinePlayer.class);
        when(invented.hasPlayedBefore()).thenReturn(Boolean.FALSE);
        bukkit.when(() -> Bukkit.getPlayerExact(name)).thenReturn(null);
        bukkit.when(() -> Bukkit.getOfflinePlayer(name)).thenReturn(invented);
    }

    /**
     * A pair is handed over, and the new owner's name goes with the id.
     *
     * <p>Both are recorded: the uuid is what the plugin checks and the name is what everybody
     * else reads, and a pair showing the wrong name in a list is a pair nobody can find.
     */
    @Test
    void handingAPairOverRecordsTheNewOwnerByBothIdAndName()
    {
        assertTrue(run(owner, "owner", "Grace", PAIR_ID));

        assertEquals(RECIPIENT, pair.getOwner(), "the uuid is what the plugin checks");
        assertEquals("Grace", pair.getOwnerName(), "and the name is what people read");
        yaml.verify(() -> RingYamlManager.saveWorld(WORLD));
    }

    /**
     * The giver keeps no access to a private pair they handed over.
     *
     * <p>Written for staff building rings on request. Kept on the list, they would end up with
     * standing access to every pair they had ever built for anybody. A player who wants to keep
     * using one they gave away can be added back by its new owner, which is that owner's call.
     */
    @Test
    void theGiverKeepsNoAccessToAPrivatePairTheyHandedOver()
    {
        assertTrue(RingPermissions.mayUse(owner, pair), "theirs to begin with");

        run(owner, "owner", "Grace", PAIR_ID);

        assertFalse(RingPermissions.mayUse(owner, pair),
            "handing over a private pair hands over the use of it");
        verify(owner).sendMessage(contains("no longer have access"));
    }

    /**
     * The quota is checked against whoever is receiving the pair.
     *
     * <p>Checked against the giver it would do nothing at all: anybody at their limit could
     * carry on building by having somebody else lay the rings and hand them over.
     */
    @Test
    void theQuotaIsCheckedAgainstTheRecipientRatherThanTheGiver()
    {
        // Two, not one, and the recipient holds both. A limit of one would be met by the giver
        // as well -- they own the pair they are giving away -- so either reading of the rule
        // would refuse and the test would prove nothing about which one is in force.
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(2));
        graceAlreadyOwns("bbbb0002", 400);
        graceAlreadyOwns("cccc0003", 800);

        assertTrue(run(owner, "owner", "Grace", PAIR_ID));

        assertEquals(OWNER, pair.getOwner(), "the pair stays where it was");
        verify(owner).sendMessage(contains("already has 2 ring pairs"));
    }

    /**
     * The giver being under the limit does not stop the recipient's being enforced.
     *
     * <p>The other half of the same rule, and the half that makes the first half mean
     * something: with one pair to the giver's name they are well under any limit worth setting.
     */
    @Test
    void theGiverBeingUnderTheLimitDoesNotLetThePairThrough()
    {
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(2));
        graceAlreadyOwns("bbbb0002", 400);
        graceAlreadyOwns("cccc0003", 800);

        run(owner, "owner", "Grace", PAIR_ID);

        assertEquals(1, RingManager.countPairsOwnedBy(OWNER),
            "the giver holds one pair, which is inside a limit of two");
        assertEquals(OWNER, pair.getOwner(), "and the transfer is refused all the same");
    }

    /** A name nobody has ever used is not added to the access list either. */
    @Test
    void allowingANameNobodyHasEverUsedIsRefused()
    {
        nobodyCalled("Ghost");

        assertTrue(run(owner, "allow", "Ghost", PAIR_ID));

        verify(owner).sendMessage(contains("has been on this server"));
        yaml.verify(() -> RingYamlManager.saveWorld(anyString()), never());
    }

    /**
     * A pair cannot be handed to whoever already owns it.
     *
     * <p>Reached as an operator: the giver has to be allowed to manage the pair before the
     * question of who owns it comes up at all, and by then it is somebody else's.
     */
    @Test
    void aPairCannotBeHandedToItsOwnOwner()
    {
        pair.setOwner(RECIPIENT);
        when(owner.isOp()).thenReturn(Boolean.TRUE);

        assertTrue(run(owner, "owner", "Grace", PAIR_ID));

        verify(owner).sendMessage(contains("already owns that pair"));
        yaml.verify(() -> RingYamlManager.saveWorld(anyString()), never());
    }

    /** Nor to a name nobody on this server has ever used. */
    @Test
    void aPairCannotBeHandedToANameNobodyHasEverUsed()
    {
        nobodyCalled("Ghost");

        assertTrue(run(owner, "owner", "Ghost", PAIR_ID));

        assertEquals(OWNER, pair.getOwner());
        verify(owner).sendMessage(contains("has been on this server"));
    }

    /** And not by somebody it does not belong to. */
    @Test
    void aPairIsNotSomebodyElsesToGiveAway()
    {
        pair.setOwner(UUID.randomUUID().toString());

        assertTrue(run(owner, "owner", "Grace", PAIR_ID));

        verify(owner).sendMessage(contains("not your ring pair to give away"));
    }

    /** Letting somebody in adds them, and says so by name and id. */
    @Test
    void allowingSomebodyLetsThemUseThePair()
    {
        assertTrue(run(owner, "allow", "Grace", PAIR_ID));

        verify(owner).sendMessage(contains("Grace may now use " + PAIR_ID));
        yaml.verify(() -> RingYamlManager.saveWorld(WORLD));
    }

    /**
     * Allowing somebody already on the list says so rather than pretending.
     *
     * <p>The answer to "did that do anything" is worth having. Told it worked either way, an
     * owner has no way to tell a name they have already added from one they mistyped.
     */
    @Test
    void allowingSomebodyAlreadyOnTheListSaysSo()
    {
        run(owner, "allow", "Grace", PAIR_ID);

        assertTrue(run(owner, "allow", "Grace", PAIR_ID));

        verify(owner).sendMessage(contains("could already use it"));
    }

    /** Denying takes them off again. */
    @Test
    void denyingSomebodyTakesThemOffTheList()
    {
        run(owner, "allow", "Grace", PAIR_ID);

        assertTrue(run(owner, "deny", "Grace", PAIR_ID));

        verify(owner).sendMessage(contains("Grace may no longer use " + PAIR_ID));
    }

    /** Denying somebody who was never on it says that rather than nothing. */
    @Test
    void denyingSomebodyWhoWasNeverOnTheListSaysSo()
    {
        assertTrue(run(owner, "deny", "Grace", PAIR_ID));

        verify(owner).sendMessage(contains("was not on the list"));
    }

    /** The access list is not somebody else's to change either. */
    @Test
    void theAccessListIsNotSomebodyElsesToChange()
    {
        pair.setOwner(UUID.randomUUID().toString());

        assertTrue(run(owner, "allow", "Grace", PAIR_ID));

        verify(owner, never()).sendMessage(contains("may now use"));
    }

    /**
     * Owning a pair is not on its own enough to travel by one.
     *
     * <p>The two are separate questions: `mayManage` asks whose pair it is, `mayUse` asks
     * whether they may travel by rings at all and then whether this pair admits them. An owner
     * whose `wormhole.ring.use` has been taken away keeps the pair and cannot ride it, which
     * reads as a bug until the two are seen apart.
     */
    @Test
    void owningAPairIsNotOnItsOwnEnoughToTravelByIt()
    {
        when(owner.hasPermission(RingPermissions.USE)).thenReturn(Boolean.FALSE);

        assertTrue(RingPermissions.mayManage(owner, pair), "still theirs to manage");
        assertFalse(RingPermissions.mayUse(owner, pair), "but not theirs to ride");
    }

    /** Naming a pair that does not exist says so rather than acting on the one underfoot. */
    @Test
    void namingAPairThatDoesNotExistIsRefused()
    {
        assertTrue(run(owner, "allow", "Grace", "nosuchpair"));

        verify(owner).sendMessage(contains("There is no ring pair called nosuchpair"));
    }
}
