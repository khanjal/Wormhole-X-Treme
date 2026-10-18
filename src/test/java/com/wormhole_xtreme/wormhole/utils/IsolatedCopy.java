package com.wormhole_xtreme.wormhole.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads its own copy of one class and its nested classes, on a classpath without the packages
 * named: a server missing an API, for a class that must survive linking without it.
 */
final class IsolatedCopy extends ClassLoader
{
    private final String copied;

    private final List<String> hidden;

    /**
     * @param copied
     *            the class to copy
     * @param hidden
     *            package prefixes, each ending in a dot
     */
    IsolatedCopy(final Class<?> copied, final String... hidden)
    {
        super(IsolatedCopy.class.getClassLoader());
        this.copied = copied.getName();
        this.hidden = List.of(hidden);
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException
    {
        if (hidden.stream().anyMatch(name::startsWith))
        {
            throw new ClassNotFoundException(name);
        }
        if (!name.equals(copied) && !name.startsWith(copied + "$"))
        {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name))
        {
            final Class<?> found = findLoadedClass(name);
            final Class<?> loaded = (found != null) ? found : define(name);
            if (resolve)
            {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> define(final String name) throws ClassNotFoundException
    {
        try (final InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class"))
        {
            if (in == null)
            {
                throw new ClassNotFoundException(name);
            }
            final byte[] bytes = in.readAllBytes();
            return defineClass(name, bytes, 0, bytes.length);
        }
        catch (final IOException unreadable)
        {
            throw new ClassNotFoundException(name, unreadable);
        }
    }
}
