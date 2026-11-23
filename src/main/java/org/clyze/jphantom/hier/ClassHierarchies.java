package org.clyze.jphantom.hier;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.clyze.jphantom.Types;

public class ClassHierarchies implements Opcodes, Types
{
    private ClassHierarchies() {
        throw new AssertionError();
    }

    public static void loadSystemType(ClassHierarchy hierarchy, Class<?> clazz)
    {
        new Importer(hierarchy, clazz.getClassLoader()).execute(Type.getType(clazz));
    }

    public static Set<Type> unknownTypes(ClassHierarchy hierarchy)
    {
        Set<Type> unknown = new HashSet<>();

        for (Type c : hierarchy)
        {
            Type sc = hierarchy.getSuperclass(c);
            Set<Type> ifaces = hierarchy.getInterfaces(c);

            if (sc != null) {
                if (!hierarchy.contains(sc))
                    unknown.add(sc);
                else
                    assert !hierarchy.isInterface(sc) : sc;
            }
            
            for (Type iface : ifaces)
                if (!hierarchy.contains(iface))
                    unknown.add(iface);
                else
                    assert hierarchy.isInterface(iface) : iface;
        }

        return unknown;
    }

    /////////////// Factory Method ///////////////

    public static ClassHierarchy fromJar(String jarname) throws IOException
    {
        return fromJar(new JarFile(jarname));
    }

    /** Hierarchy of the classes of several jars; a class name's first occurrence wins. */
    public static ClassHierarchy fromJars(List<String> jarnames) throws IOException
    {
        ClassHierarchy hierarchy = new IncrementalClassHierarchy();
        for (String jarname : jarnames)
            addJar(hierarchy, new JarFile(jarname));
        if (org.clyze.jphantom.Options.V().isLenient())
            ((IncrementalClassHierarchy) hierarchy).dropInconsistentSupertypes();
        return hierarchy;
    }

    public static ClassHierarchy fromJar(JarFile file) throws IOException
    {
        ClassHierarchy hierarchy = new IncrementalClassHierarchy();
        addJar(hierarchy, file);
        if (org.clyze.jphantom.Options.V().isLenient())
            ((IncrementalClassHierarchy) hierarchy).dropInconsistentSupertypes();
        return hierarchy;
    }

    private static void addJar(ClassHierarchy hierarchy, JarFile file) throws IOException
    {
        try {
            for (Enumeration<JarEntry> e = file.entries(); e.hasMoreElements();)
            {
                JarEntry entry = e.nextElement();

                /* Skip directories */
                if (entry.isDirectory())
                    continue;
            
                /* Skip non-class files */
                if(!entry.getName().endsWith(".class"))
                    continue;

                /* Skip module/package descriptors and multi-release variants */
                if (org.clyze.jphantom.util.JarEntries.isDescriptor(entry.getName()))
                    continue;

                try (InputStream stream = file.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(stream);
                    String ifaceNames[] = reader.getInterfaces();

                    // Compute Types

                    Type clazz = Type.getObjectType(reader.getClassName());

                    // Duplicate across jars: the first occurrence wins
                    if (hierarchy.contains(clazz))
                        continue;

                    Type superclass = Type.getObjectType(reader.getSuperName());
                    Type ifaces[] = new Type[ifaceNames.length];

                    for (int i = 0; i < ifaces.length; i++)
                        ifaces[i] = Type.getObjectType(ifaceNames[i]);

                    // Add type to hierarchy
                    boolean isInterface = (reader.getAccess() & ACC_INTERFACE) != 0;

                    if (isInterface) {
                        hierarchy.addInterface(clazz, ifaces);
                        assert superclass.equals(OBJECT);
                    } else {
                        hierarchy.addClass(clazz, superclass, ifaces);
                    }
                }
            }
        } finally {
            file.close();
        }
    }
}
