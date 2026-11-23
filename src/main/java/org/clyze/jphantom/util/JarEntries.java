package org.clyze.jphantom.util;

/** Jar entries that end in .class but are not classes to complement. */
public final class JarEntries
{
    private JarEntries() {}

    /**
     * module-info descriptors and multi-release copies under META-INF/.
     * package-info classes are ordinary (synthetic interface) class files and
     * are complemented like any other: front ends load them and resolve the
     * types of their package annotations.
     */
    public static boolean isDescriptor(String entryName) {
        return entryName.endsWith("module-info.class")
            || entryName.startsWith("META-INF/");
    }
}
