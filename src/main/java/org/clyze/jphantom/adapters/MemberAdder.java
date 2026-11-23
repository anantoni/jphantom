package org.clyze.jphantom.adapters;

import java.util.LinkedHashMap;
import java.util.Map;
import org.clyze.jphantom.Options;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Adds the missing members of one phantom class. There is one per class,
 * directly above the method cache and the writer, so it sees the class access
 * that is finally written. Uses of the same member are merged: a method is
 * keyed by name and descriptor, a field by name, and the latest access wins.
 * Earlier versions chained one MethodAdder or FieldAdder per use site, and
 * visiting a chain of many thousand adapters overflowed the stack (alfresco).
 */
public class MemberAdder extends ClassVisitor implements Opcodes
{
    private final Map<String, int[]> methodAccess = new LinkedHashMap<>();
    private final Map<String, String[]> methodNameDesc = new LinkedHashMap<>();
    private final Map<String, Object[]> fields = new LinkedHashMap<>();

    public MemberAdder(ClassVisitor cv) {
        super(Options.ASM_VER, cv);
    }

    public void addMethod(int access, String name, String desc) {
        String key = name + desc;
        methodAccess.put(key, new int[] { access });
        methodNameDesc.putIfAbsent(key, new String[] { name, desc });
    }

    public void addField(int access, String name, String desc) {
        fields.put(name, new Object[] { access, desc });
    }

    @Override
    public void visit(int version, int access, String name,
                      String signature, String superName, String[] interfaces)
    {
        // A non-abstract interface method (default or static) needs a Java 8
        // class file; other phantom classes keep their version.
        if ((access & ACC_INTERFACE) != 0 && version < V1_8)
            for (int[] a : methodAccess.values())
                if ((a[0] & ACC_ABSTRACT) == 0) {
                    version = V1_8;
                    break;
                }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitEnd() {
        // Members are added in the order of their first use.
        for (Map.Entry<String, Object[]> f : fields.entrySet()) {
            Object[] v = f.getValue();
            FieldAdder.emit(cv, (Integer) v[0], f.getKey(), (String) v[1]);
        }
        for (Map.Entry<String, int[]> m : methodAccess.entrySet()) {
            String[] nd = methodNameDesc.get(m.getKey());
            MethodAdder.emit(cv, m.getValue()[0], nd[0], nd[1], null);
        }
        super.visitEnd();
    }
}
