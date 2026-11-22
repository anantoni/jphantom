package org.clyze.jphantom.adapters;

import org.clyze.jphantom.Options;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassVisitor;

public class AccessAdapter extends ClassVisitor implements Opcodes
{
    private final int access;

    public AccessAdapter(ClassVisitor cv, int access) {
        super(Options.ASM_VER, cv);
        this.access = access;
    }

    public int getAccess() {
        return access;
    }

    /**
     * Chain an access adapter on top of {@code top}, unless {@code top} already
     * is one with the same access: in a chain of access adapters the one closest
     * to the writer decides the final access, so an adjacent duplicate changes
     * nothing. Without this, every use of a missing annotation class added one
     * more adapter, and a class used as an annotation thousands of times
     * overflowed the stack when the chain was visited.
     */
    public static ClassVisitor chain(ClassVisitor top, int access) {
        if (top instanceof AccessAdapter && ((AccessAdapter) top).access == access)
            return top;
        return new AccessAdapter(top, access);
    }

    @Override
    public void visit(int version, int oldAccess, 
        String name, String signature, 
        String superName, String[] interfaces)
    {
        super.visit(
            version, 
            this.access, 
            name, 
            signature, 
            superName, 
            interfaces);
    }
}
