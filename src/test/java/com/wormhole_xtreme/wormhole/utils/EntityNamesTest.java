package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * An entity's name goes through Paper's component setter where the server has one, and Bukkit's
 * string setter otherwise; a server that refuses either costs the name, never the spawn of Freya
 * or a stand-in.
 *
 * <p>Spigot's API, which most legs compile against, has no {@code customName(Component)}, so these
 * tests install a stand-in type declaring Paper's two methods, found by reflection exactly as the
 * server's are.
 */
class EntityNamesTest
{
    private static final String NAME = "§bFreya";

    /** Paper's two methods, on an entity type that has them on every API build. */
    interface PaperNamed extends Entity
    {
        void customName(Component name);

        Component customName();
    }

    private PaperNamed entity;

    @BeforeEach
    void setUp()
    {
        entity = mock(PaperNamed.class);
    }

    @AfterEach
    void tearDown()
    {
        EntityNames.forget();
    }

    private static void asOnPaper() throws NoSuchMethodException
    {
        EntityNames.lookupWith(new EntityNames.Lookup(PaperNamed.class.getMethod("customName", Component.class),
            PaperNamed.class.getMethod("customName")));
    }

    private static Component component(final String legacyText)
    {
        return LegacyComponentSerializer.legacySection().deserialize(legacyText);
    }

    /** Paper deprecates the string setter, so a Paper server is given the name as a component, colours read as colours. */
    @Test
    void aPaperServerIsGivenTheNameAsAComponent() throws NoSuchMethodException
    {
        asOnPaper();

        EntityNames.set(entity, NAME);

        verify(entity).customName(component(NAME));
        verify(entity, never()).setCustomName(anyString());
    }

    @Test
    void aServerWithoutComponentNamesIsGivenTheString()
    {
        EntityNames.lookupWith(null);

        EntityNames.set(entity, NAME);

        verify(entity).setCustomName(NAME);
    }

    /** A Paper that refuses the component still names the entity, the older way. */
    @Test
    void aRefusedComponentNameFallsBackToTheString() throws NoSuchMethodException
    {
        asOnPaper();
        doThrow(new IllegalStateException("refused")).when(entity).customName(any(Component.class));

        EntityNames.set(entity, NAME);

        verify(entity).setCustomName(NAME);
    }

    /** A name is cosmetic: refused both ways, Freya still spawns, unnamed. */
    @Test
    void aNameRefusedBothWaysDoesNotStopTheSpawn() throws NoSuchMethodException
    {
        asOnPaper();
        doThrow(new IllegalStateException("refused")).when(entity).customName(any(Component.class));
        doThrow(new IllegalStateException("refused")).when(entity).setCustomName(anyString());

        assertDoesNotThrow(() -> EntityNames.set(entity, NAME));
    }

    /** A stand-in takes its creature's name as Paper holds it, without a trip through legacy text. */
    @Test
    void aPaperServerCopiesTheComponentItself() throws NoSuchMethodException
    {
        asOnPaper();
        final PaperNamed original = mock(PaperNamed.class);
        final Component name = component("§cBob");
        when(original.customName()).thenReturn(name);

        EntityNames.copy(original, entity);

        verify(entity).customName(name);
        verify(entity, never()).setCustomName(anyString());
    }

    @Test
    void aServerWithoutComponentNamesCopiesTheString()
    {
        EntityNames.lookupWith(null);
        final Entity original = mock(Entity.class);
        when(original.getCustomName()).thenReturn("§cBob");

        EntityNames.copy(original, entity);

        verify(entity).setCustomName("§cBob");
    }

    @Test
    void aRefusedComponentCopyFallsBackToTheString() throws NoSuchMethodException
    {
        asOnPaper();
        final PaperNamed original = mock(PaperNamed.class);
        when(original.customName()).thenThrow(new IllegalStateException("refused"));
        when(original.getCustomName()).thenReturn("§cBob");

        EntityNames.copy(original, entity);

        verify(entity).setCustomName("§cBob");
    }

    @Test
    void aCopyRefusedBothWaysDoesNotStopTheStandIn() throws NoSuchMethodException
    {
        asOnPaper();
        final PaperNamed original = mock(PaperNamed.class);
        when(original.customName()).thenThrow(new IllegalStateException("refused"));
        when(original.getCustomName()).thenThrow(new IllegalStateException("refused"));

        assertDoesNotThrow(() -> EntityNames.copy(original, entity));
    }

    /**
     * Spigot and CraftBukkit may have no Adventure at all. Hiding {@code net.kyori} from a fresh copy
     * of the class stands in for that: the lookup fails to link, and the entity is still named.
     */
    @Test
    void aServerWithoutAdventureIsGivenTheString() throws Throwable
    {
        final Method set = new IsolatedCopy(EntityNames.class, "net.kyori.").loadClass(EntityNames.class.getName())
            .getMethod("set", Entity.class, String.class);
        final Entity plain = mock(Entity.class);

        try
        {
            set.invoke(null, plain, NAME);
        }
        catch (final InvocationTargetException thrown)
        {
            throw thrown.getCause();
        }

        verify(plain).setCustomName(NAME);
    }
}
