package org.clyze.jphantom.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * An explicit set of existing classes: jars and class directories. Only the
 * class names are indexed up front; class files are read on demand and never
 * loaded into the running JVM. The first occurrence of a class name wins
 * (classpath order), as on a JVM classpath.
 */
public class ClassPath implements AutoCloseable
{
    /** Where a class file lives: a jar entry or a file. */
    private static final class Location {
        final JarFile jar;     // null for a directory entry
        final String entry;    // jar entry name
        final Path file;       // file in a class directory

        Location(JarFile jar, String entry) { this.jar = jar; this.entry = entry; this.file = null; }
        Location(Path file) { this.jar = null; this.entry = null; this.file = file; }
    }

    private final Map<String, Location> classes = new HashMap<>();
    private final Map<String, byte[]> cache = new HashMap<>();
    private final List<JarFile> jars = new ArrayList<>();

    public ClassPath(List<Path> entries) throws IOException
    {
        for (Path entry : entries) {
            if (Files.isDirectory(entry))
                addDirectory(entry);
            else
                addJar(entry);
        }
    }

    private void addJar(Path path) throws IOException
    {
        JarFile jar = new JarFile(path.toFile());
        jars.add(jar);

        for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
            JarEntry entry = e.nextElement();
            String name = entry.getName();

            if (entry.isDirectory() || !isClassFile(name))
                continue;

            classes.putIfAbsent(internalName(name), new Location(jar, name));
        }
    }

    private void addDirectory(Path dir) throws IOException
    {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String rel = dir.relativize(p).toString().replace(p.getFileSystem().getSeparator(), "/");

                if (!Files.isRegularFile(p) || !isClassFile(rel))
                    continue;

                classes.putIfAbsent(internalName(rel), new Location(p));
            }
        }
    }

    private static String internalName(String classFileName) {
        return classFileName.substring(0, classFileName.length() - ".class".length());
    }

    // Skip module/package descriptors and multi-release variants.
    private static boolean isClassFile(String name) {
        return name.endsWith(".class") && !JarEntries.isDescriptor(name);
    }

    private static byte[] readAll(InputStream in) throws IOException
    {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0)
            out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** @param internalName e.g. "java/lang/Object" */
    public boolean contains(String internalName) {
        return classes.containsKey(internalName);
    }

    /** The class file bytes (read once, then cached), or null if the class is not on this classpath. */
    public byte[] bytes(String internalName)
    {
        Location loc = classes.get(internalName);
        if (loc == null)
            return null;

        byte[] b = cache.get(internalName);
        if (b != null)
            return b;

        try {
            if (loc.jar != null)
                try (InputStream in = loc.jar.getInputStream(loc.jar.getJarEntry(loc.entry))) {
                    b = readAll(in);
                }
            else
                b = Files.readAllBytes(loc.file);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
        cache.put(internalName, b);
        return b;
    }

    public InputStream open(String internalName) throws IOException
    {
        byte[] b = bytes(internalName);
        if (b == null)
            throw new IOException("Class not on classpath: " + internalName);
        return new ByteArrayInputStream(b);
    }

    public Iterable<String> classNames() {
        return Collections.unmodifiableSet(classes.keySet());
    }

    @Override
    public void close() throws IOException {
        for (JarFile jar : jars)
            jar.close();
    }
}
