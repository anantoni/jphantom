package org.clyze.jphantom.constraints.extractors;

import org.clyze.jphantom.*;
import org.clyze.jphantom.exc.*;
import org.clyze.jphantom.hier.*;
import org.clyze.jphantom.hier.closure.*;
import org.clyze.jphantom.conversions.*;
import org.clyze.jphantom.constraints.solvers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.objectweb.asm.*;

public abstract class AbstractExtractor implements ConversionVisitor, Types
{
    protected final TypeConstraintSolver solver;
    protected final ClassHierarchy hierarchy;
    private final ClassHierarchy.Snapshot closure;

    private final static Logger logger = 
        LoggerFactory.getLogger(AbstractExtractor.class);

    public AbstractExtractor(TypeConstraintSolver solver)
    {
        this.solver = solver;
        this.hierarchy = solver.getHierarchy();
        this.closure = new CopyingSnapshot(hierarchy);
    }
    
    /////////////////// Conversion Visitor ///////////////////

    @Override
    public void visit(IdentityConversion conv) {}

    @Override
    public void visit(WideningPrimitiveConversion conv) {}

    @Override
    public void visit(NarrowingPrimitiveConversion conv) {}

    @Override
    public void visit(IllegalConversion conv) {
        insolvable(conv.asConstraint());
    }

    /**
     * A conversion the existing (non-phantom) class hierarchy forbids: the
     * bytecode was compiled against other versions of these classes. It says
     * nothing about the phantom classes. Strict (default): fail. --lenient:
     * skip it and report it.
     */
    private static void insolvable(org.clyze.jphantom.constraints.Constraint constraint) {
        if (!org.clyze.jphantom.Options.V().isLenient())
            throw new InsolvableConstraintException(constraint);
        org.clyze.jphantom.Options.V().recordConflict();
        logger.warn("Skipping conversion that contradicts the existing hierarchy: {}", constraint);
    }

    @Override
    public void visit(NullConversion conv) {}

    @Override
    public void visit(WideningReferenceConversion conv)
    {
        // Object is a supertype for every reference type
        if (conv.to.equals(OBJECT))
            return;

        // Array types => add contraint for elements
        if (conv.from.getSort() == Type.ARRAY && 
            conv.to.getSort() == Type.ARRAY)
        {
            Type from = ArrayTypes.elementOf(conv.from);
            Type to = ArrayTypes.elementOf(conv.to);

            // Conversion for element types
            Conversion subconv = Conversions.getAssignmentConversion(from, to);

            subconv.accept(this);
        }

        // Skip array reference types
        if (conv.from.getSort() == Type.ARRAY || 
            conv.to.getSort() == Type.ARRAY)
            return;
                
        // Non-array reference types

        if (hierarchy.contains(conv.from) && hierarchy.contains(conv.to)) {
            try {
                // Check if conversion is legal w.r.t. our class hierarchy
                if (!closure.isSubtypeOf(conv.from, conv.to))
                    insolvable(conv.asConstraint());
                return;
            } catch (IncompleteSupertypesException exc) {} // Add constraint below
        }

        // Add constraint to underlying solver
        conv.asConstraint().accept(solver);
        logger.debug("Adding constaint: {}", conv.asConstraint());
    }
}
