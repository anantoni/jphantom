package org.clyze.jphantom.hier;

import java.util.*;
import org.clyze.jphantom.Types;
import org.objectweb.asm.Type;

/** @author George Balatsouras */
public class IncrementalClassHierarchy extends AbstractClassHierarchy 
    implements Types
{    
    ///////////////// Fields /////////////////

    private final Map<Type,Type> directSuperclass = new HashMap<>();
    private final Map<Type,Set<Type>> directImplInterfaces = new HashMap<>();
    private final Map<Type,Boolean> isIface = new HashMap<>();


    ///////////////// Constructors /////////////////

    public IncrementalClassHierarchy()
    {
        super();

        // Add java.lang.Object

        directSuperclass.put(OBJECT, null);
        directImplInterfaces.put(OBJECT, Collections.<Type>emptySet());
        isIface.put(OBJECT, false);
    }

    public IncrementalClassHierarchy(ClassHierarchy other)
    {
        this();

        for (Type t : other) {
            isIface.put(t, other.isInterface(t));

            Set<Type> ifaces = Collections.unmodifiableSet(
                other.getInterfaces(t));

            directSuperclass.put(t, other.getSuperclass(t));
            directImplInterfaces.put(t, ifaces);
        }
    }

    ///////////////////////// Methods /////////////////////////

    @Override
    public Iterator<Type> iterator() {
        return isIface.keySet().iterator();
    }

    private final void addSupertypes(Type clazz, Type superclass, Type[] interfaces)
    {
        // Create an unmodifiable set of interfaces
        Set<Type> ifaces = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(interfaces)));

        assert !ifaces.contains(null);
        assert superclass != null || clazz.equals(OBJECT) : clazz;

        directImplInterfaces.put(clazz, ifaces);
        directSuperclass.put(clazz, superclass);
    }

    @Override
    public void addClass(Type clazz, Type superclass, Type[] interfaces)
    {
        if (contains(clazz))
            throw new IllegalArgumentException(clazz + " has already been added");

        // Argument Checking (--lenient: checked once all jars are loaded,
        // see dropInconsistentSupertypes, so that the result does not depend
        // on the order in which classes are added)

        if (!org.clyze.jphantom.Options.V().isLenient()) {
            for (Type i : interfaces)
                checkedInterface(i);

            checkedClass(superclass);
        }

        // No graph cycle sanity check is done at this point

        isIface.put(clazz, false);
        addSupertypes(clazz, superclass, interfaces);
    }

    @Override
    public void addInterface(Type iface, Type[] superInterfaces) 
    {
        if (contains(iface))
            throw new IllegalArgumentException(iface + " has already been added");

        // Argument Checking (--lenient: see dropInconsistentSupertypes)

        if (!org.clyze.jphantom.Options.V().isLenient())
            for (Type i : superInterfaces)
                checkedInterface(i);
     
        // No graph cycle sanity check is done at this point

        isIface.put(iface, true);
        addSupertypes(iface, OBJECT, superInterfaces);
    }

    /**
     * --lenient: real classpaths mix libraries compiled against different
     * versions of the same library, so a present class may extend a type that
     * another present jar declares as an interface, or implement one declared
     * as a class. No phantom class can satisfy such a use (the JVM would
     * reject the class), so the contradicting supertype is dropped from this
     * hierarchy, and reported: a superclass that is an interface becomes
     * Object, an implemented class is removed. Only this model changes; input
     * classes are never rewritten. Classes are visited in name order.
     */
    public void dropInconsistentSupertypes()
    {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(IncrementalClassHierarchy.class);
        List<Type> types = new ArrayList<>(isIface.keySet());
        types.sort(Comparator.comparing(Type::getInternalName));

        for (Type t : types) {
            Type sc = directSuperclass.get(t);

            if (!isIface.get(t) && sc != null && contains(sc) && isIface.get(sc)) {
                logger.warn("Inconsistent classpath: {} extends interface {}; using Object", t, sc);
                org.clyze.jphantom.Options.V().recordConflict();
                directSuperclass.put(t, OBJECT);
            }

            Set<Type> ifaces = directImplInterfaces.get(t);
            Set<Type> kept = new HashSet<>();

            for (Type i : ifaces)
                if (contains(i) && !isIface.get(i)) {
                    logger.warn("Inconsistent classpath: {} implements class {}; dropped", t, i);
                    org.clyze.jphantom.Options.V().recordConflict();
                } else {
                    kept.add(i);
                }

            if (kept.size() != ifaces.size())
                directImplInterfaces.put(t, Collections.unmodifiableSet(kept));
        }
    }

    @Override
    public final boolean isInterface(Type obj) {
        return isIface.get(checkedContainedObject(obj));
    }

    @Override
    public final boolean contains(Type obj) {
        return isIface.containsKey(checkedObject(obj));
    }

    @Override
    public Set<Type> getInterfaces(Type obj)
    {
        Set<Type> ifaces = directImplInterfaces.get(checkedContainedObject(obj));

        // Sanity checks
        assert ifaces != null : obj;

        return ifaces;
    }

    @Override
    public Type getSuperclass(Type obj)
    {
        Type superclass = directSuperclass.get(checkedContainedObject(obj));

        // Sanity checks
        assert superclass != null || obj.equals(OBJECT) : obj;
        assert !isInterface(obj) || superclass.equals(OBJECT);

        return superclass;
    }
}

