package org.clyze.jphantom.adapters;

import org.clyze.jphantom.util.Library;
import org.clyze.jphantom.hier.Importer;
import java.util.*;
import java.io.IOException;
import org.clyze.jphantom.hier.*;
import org.clyze.jphantom.*;

import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhantomAdder extends SignatureVisitor implements Opcodes
{
    private final ClassHierarchy hierarchy;
    private final ClassMembers members;
    private final Phantoms phantoms;

    private final static Logger logger = 
        LoggerFactory.getLogger(PhantomAdder.class);

    public PhantomAdder(ClassHierarchy hierarchy, ClassMembers members, Phantoms phantoms)
    {
        super(Options.ASM_VER);
        this.hierarchy = hierarchy;
        this.members = members;
        this.phantoms = phantoms;
    }

    @Override
    public void visitInnerClassType(String name) {
        visitClassType(name);
    }

    @Override
    public void visitClassType(String name) {
        visitClassType(Type.getObjectType(name));
    }

    public void visitClassType(Type objType)
    {
        assert objType.getSort() == Type.OBJECT;

        /* Check if class already exists in the repository */
        if (hierarchy.contains(objType))
            return;

        /* Check if class exists in the library (the JDK and other existing
           classes): the running JVM by default, or an explicit --classpath. */
        if (Library.v().contains(objType))
        {
            // At this point, a library type was found. We first have
            // to add it to the hierarchy of referenced types, along
            // with all of its supertypes.

            // Create a set of types referenced so far.

            Set<Type> prev = new HashSet<>();

            for (Type t : hierarchy)
                prev.add(t);

            // Import from the library. At this point, we may be at an
            // inconsistent state since a library type could have been added
            // recursively to the hierarchy, but its members may have not
            // been recorded.

            new Importer(hierarchy, Library.v().hierarchy()).execute(objType);

            assert hierarchy.contains(objType) && !prev.contains(objType);

            // Record members of each library type, added just now.
            try {
                for (Type t : hierarchy)
                    if (!prev.contains(t))
                        Library.v().reader(t).accept(members.new Feeder(), 0);
            } catch (IOException exc) {
                logger.warn("Could not locate library type: {}", objType);
                throw new RuntimeException(exc);
            }

            return;
        }

        /* Add to phantom classes */
        if (!phantoms.contains(objType))
        {
            // Lazy implementation will provide a default value
            phantoms.getTransformer(objType);

            logger.info("Phantom Class \"{}\" detected", objType.getClassName());
        }
    }
}
