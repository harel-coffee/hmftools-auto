package com.hartwig.hmftools.esvee.common;

import static java.lang.Math.abs;
import static java.lang.String.format;

import static com.hartwig.hmftools.common.sv.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.DEL;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.INS;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.INV;

import com.hartwig.hmftools.common.sv.StructuralVariantType;

public class AssemblyLink
{
    private final JunctionAssembly mFirst;
    private final JunctionAssembly mSecond;
    private final LinkType mType;

    private final int mFirstJunctionIndexInSecond; // the index in the second assembly of the first assembly's junction position/index
    private final String mInsertedBases;

    public AssemblyLink(
            final JunctionAssembly first, final JunctionAssembly second, final LinkType type,
            int firstJunctionIndexInSecond, final String insertedBases)
    {
        mFirst = first;
        mSecond = second;
        mType = type;
        mFirstJunctionIndexInSecond = firstJunctionIndexInSecond;
        mInsertedBases = insertedBases;
    }

    public JunctionAssembly first() { return mFirst; }
    public JunctionAssembly second() { return mSecond; }
    public int firstJunctionIndexInSecond() { return mFirstJunctionIndexInSecond; }

    public StructuralVariantType svType()
    {
        if(!mFirst.junction().Chromosome.equals(mSecond.junction().Chromosome))
            return BND;

        if(mFirst.junction().Orientation != mSecond.junction().Orientation)
        {
            if(abs(mFirst.junction().Position - mSecond.junction().Position) == 1 && !mInsertedBases.isEmpty())
                return INS;

            boolean firstIsLower = mFirst.junction().Position <= mSecond.junction().Position;

            return (firstIsLower == mFirst.junction().isForward()) ? DEL : DUP;
        }
        else
        {
            return INV;
        }
    }

    public int length()
    {
        return mFirst.junction().Chromosome.equals(mSecond.junction().Chromosome) ?
                abs(mFirst.junction().Position - mSecond.junction().Position) : 0;
    }

    public String toString()
    {
        return format("%s: %s:%d:%d - %s:%d:%d len(%d) %s",
                mType, mFirst.junction().Chromosome, mFirst.junction().Position, mFirst.junction().Orientation,
                mSecond.junction().Chromosome, mFirst.junction().Position, mSecond.junction().Orientation, length(),
                !mInsertedBases.isEmpty() ? format("insert(%d: %s)", mInsertedBases.length(), mInsertedBases) : "");
    }

    public String insertedBases() { return mInsertedBases; }
}
