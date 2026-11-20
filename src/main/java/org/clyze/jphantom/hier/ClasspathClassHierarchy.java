package org.clyze.jphantom.hier;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.clyze.jphantom.util.ClassPath;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * The (read-only) hierarchy of the classes on an explicit {@link ClassPath},
 * read from their class files. It plays the role {@link SystemClassHierarchy}
 * plays for the running JVM: the source of "library" types and their supertypes.
 * Interfaces report java.lang.Object as superclass, as in SystemClassHierarchy.
 */
public class ClasspathClassHierarchy extends AbstractClassHierarchy implements Opcodes
{
    private static final Type OBJECT = Type.getObjectType("java/lang/Object");

    private final ClassPath classpath;

    public ClasspathClassHierarchy(ClassPath classpath) {
        this.classpath = classpath;
    }

    @Override
    public void addClass(Type clazz, Type superclass, Type[] interfaces) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addInterface(Type iface, Type[] superInterfaces) {
        throw new UnsupportedOperationException();
    }

    private ClassReader reader(Type obj)
    {
        byte[] bytes = classpath.bytes(obj.getInternalName());
        if (bytes == null)
            throw new IllegalArgumentException(obj.getClassName());
        return new ClassReader(bytes);
    }

    @Override
    public boolean isInterface(Type obj) {
        return (reader(obj).getAccess() & ACC_INTERFACE) != 0;
    }

    @Override
    public boolean contains(Type obj) {
        return obj.getSort() == Type.OBJECT && classpath.contains(obj.getInternalName());
    }

    @Override
    public Set<Type> getInterfaces(Type obj)
    {
        Set<Type> ifaces = new HashSet<>();
        for (String name : reader(obj).getInterfaces())
            ifaces.add(Type.getObjectType(name));
        return ifaces;
    }

    @Override
    public Type getSuperclass(Type obj)
    {
        ClassReader r = reader(obj);
        if ((r.getAccess() & ACC_INTERFACE) != 0)
            return OBJECT;
        String superName = r.getSuperName();
        return superName == null ? null : Type.getObjectType(superName);
    }

    @Override
    public Iterator<Type> iterator() {
        throw new UnsupportedOperationException();
    }
}
