package org.clyze.jphantom.util;

/** Jar entries that end in .class but are not classes to complement. */
public final class JarEntries
{
    private JarEntries() {}

    /** module-info / package-info descriptors, and multi-release copies under META-INF/. */
    public static boolean isDescriptor(String entryName) {
        return entryName.endsWith("module-info.class")
            || entryName.endsWith("package-info.class")
            || entryName.startsWith("META-INF/");
    }
}
