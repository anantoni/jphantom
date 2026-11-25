package org.clyze.jphantom.util;

import java.io.IOException;
import org.clyze.jphantom.hier.ClassHierarchy;
import org.clyze.jphantom.hier.ClasspathClassHierarchy;
import org.clyze.jphantom.hier.SystemClassHierarchy;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

/**
 * The single definition of the "existing" classes outside the jars being
 * complemented (the library, including the JDK), used everywhere JPhantom must
 * decide whether a referenced class exists and how it looks.
 *
 * By default it is the running JVM (bootstrap class loader), exactly as in
 * JPhantom 1.3. With an explicit classpath (--classpath), it is that classpath
 * alone: the analyzed platform and libraries, independent of the JDK that runs
 * JPhantom.
 */
public abstract class Library
{
    private static Library instance = new Jvm();

    public static Library v() { return instance; }

    public static void useClasspath(ClassPath classpath) {
        instance = new Explicit(classpath);
    }

    /** Back to the running JVM (a new JPhantom run in the same JVM). */
    public static void useJvm() {
        instance = new Jvm();
    }

    /** Is the type an existing library class? */
    public abstract boolean contains(Type type);

    /** Hierarchy of library types, for importing supertypes. */
    public abstract ClassHierarchy hierarchy();

    /** Reader for an existing library class (members). */
    public abstract ClassReader reader(Type type) throws IOException;

    /** JPhantom 1.3 behaviour: whatever the running JVM's bootstrap loader has. */
    private static final class Jvm extends Library
    {
        @Override
        public boolean contains(Type type) {
            try {
                Class.forName(type.getClassName(), false, null);
                return true;
            } catch (ClassNotFoundException exc) {
                return false;
            }
        }

        @Override
        public ClassHierarchy hierarchy() {
            return SystemClassHierarchy.getInstance(BootstrapClassLoader.v());
        }

        @Override
        public ClassReader reader(Type type) throws IOException {
            return new ClassReader(type.getInternalName());
        }
    }

    /** An explicit classpath: the analyzed platform and libraries. */
    private static final class Explicit extends Library
    {
        private final ClassPath classpath;
        private final ClasspathClassHierarchy hierarchy;

        Explicit(ClassPath classpath) {
            this.classpath = classpath;
            this.hierarchy = new ClasspathClassHierarchy(classpath);
        }

        @Override
        public boolean contains(Type type) {
            return hierarchy.contains(type);
        }

        @Override
        public ClassHierarchy hierarchy() {
            return hierarchy;
        }

        @Override
        public ClassReader reader(Type type) throws IOException {
            return new ClassReader(classpath.open(type.getInternalName()));
        }
    }
}
