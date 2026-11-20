package org.clyze.jphantom.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * An explicit set of existing classes: jars and class directories, read with
 * ASM-friendly byte access and never loaded into the running JVM. The first
 * occurrence of a class name wins (classpath order), as on a JVM classpath.
 */
public class ClassPath
{
    private final Map<String, byte[]> classes = new HashMap<>();

    public ClassPath(List<Path> entries) throws IOException
    {
        for (Path entry : entries) {
            if (Files.isDirectory(entry))
                addDirectory(entry);
            else
                addJar(entry);
        }
    }

    private void addJar(Path jar) throws IOException
    {
        try (JarFile file = new JarFile(jar.toFile())) {
            for (Enumeration<JarEntry> e = file.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                String name = entry.getName();

                if (entry.isDirectory() || !isClassFile(name))
                    continue;

                String internalName = name.substring(0, name.length() - ".class".length());

                if (!classes.containsKey(internalName))
                    try (InputStream in = file.getInputStream(entry)) {
                        classes.put(internalName, readAll(in));
                    }
            }
        }
    }

    private void addDirectory(Path dir) throws IOException
    {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String rel = dir.relativize(p).toString().replace(p.getFileSystem().getSeparator(), "/");

                if (!Files.isRegularFile(p) || !isClassFile(rel))
                    continue;

                String internalName = rel.substring(0, rel.length() - ".class".length());

                if (!classes.containsKey(internalName))
                    classes.put(internalName, Files.readAllBytes(p));
            }
        }
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

    /** The class file bytes, or null if the class is not on this classpath. */
    public byte[] bytes(String internalName) {
        return classes.get(internalName);
    }

    public InputStream open(String internalName) throws IOException
    {
        byte[] b = classes.get(internalName);
        if (b == null)
            throw new IOException("Class not on classpath: " + internalName);
        return new ByteArrayInputStream(b);
    }

    public Iterable<String> classNames() {
        return Collections.unmodifiableSet(classes.keySet());
    }
}
