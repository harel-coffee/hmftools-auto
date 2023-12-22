package com.hartwig.hmftools.svassembly.models;

import java.util.ArrayList;
import java.util.List;

import com.hartwig.hmftools.svassembly.processor.SequenceDecomposer;

import org.jetbrains.annotations.Nullable;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;

public class Record implements MutableRecord
{
    private final SAMRecord mRecord;
    @Nullable
    private List<Alignment> mAlignment = null;
    @Nullable
    private List<SequenceDecomposer.Node> mDecomposition = null;

    public Record(final SAMRecord record)
    {
        mRecord = record;
    }

    @Override
    public String getName()
    {
        return mRecord.getReadName();
    }

    @Override
    public boolean isUnmapped()
    {
        return mRecord.getReadUnmappedFlag();
    }

    @Override
    public boolean isPairedRead()
    {
        return mRecord.getReadPairedFlag();
    }

    @Override
    public boolean isFirstOfPair()
    {
        return mRecord.getReadPairedFlag() && mRecord.getFirstOfPairFlag();
    }

    @Override
    public boolean isGermline()
    {
        return "germline".equals(mRecord.getHeader().getAttribute("userTag"));
    }

    @Override
    public String sampleName()
    {
        @Nullable
        final String readGroupName = mRecord.getStringAttribute("RG");
        if(readGroupName == null)
            return fallbackReadGroup("not finding RG tag");
        @Nullable
        final SAMReadGroupRecord readGroup = mRecord.getHeader().getReadGroup(readGroupName);
        if(readGroup == null)
            return fallbackReadGroup("not finding matching read-group for tag in file");
        return readGroup.getSample();
    }

    private String fallbackReadGroup(final String fallbackReason)
    {
        final List<SAMReadGroupRecord> readGroups = mRecord.getHeader().getReadGroups();
        if (readGroups.isEmpty())
            throw new IllegalStateException("Cannot determine Sample Name for " + getName() + ". No read groups in file");
        final String firstRGSample = readGroups.get(0).getSample();
        if (readGroups.stream().allMatch(rg -> rg.getSample().equals(firstRGSample)))
            return firstRGSample; // All read-groups in this file are from the same sample.

        throw new IllegalStateException("Cannot determine fallback Sample Name for file for " + getName() + " after " + fallbackReason);
    }

    @Override
    public String getBasesString()
    {
        return mRecord.getReadString();
    }

    @Override
    public byte[] getBases()
    {
        return mRecord.getReadBases();
    }

    @Override
    public byte[] getBaseQuality()
    {
        return mRecord.getBaseQualities();
    }

    @Override
    public int getLength()
    {
        return mRecord.getReadLength();
    }

    @Override
    public List<Alignment> getAlignmentBlocks()
    {
        if(mAlignment == null)
            return buildAlignmentBlocks();

        return mAlignment;
    }

    private synchronized List<Alignment> buildAlignmentBlocks()
    {
        if(mAlignment != null)
            return mAlignment;

        final List<Alignment> alignment = new ArrayList<>();
        if(mRecord.getReadUnmappedFlag())
        {
            alignment.add(Alignment.unmapped(mRecord.getReadLength()));
            return mAlignment = alignment;
        }

        final int mapQ = mRecord.getMappingQuality();
        int referencePosition = mRecord.getAlignmentStart();
        int readPosition = 1;
        for(final CigarElement element : mRecord.getCigar().getCigarElements())
        {
            switch(element.getOperator())
            {
                case M:
                    alignment.add(new Alignment(mRecord.getReferenceName(), referencePosition, readPosition, element.getLength(), false, mapQ));
                    break;
                case I:
                    alignment.add(new Alignment("*", 0, readPosition, element.getLength(), false, mapQ));
                    break;
                case S:
                    alignment.add(new Alignment("?", 0, readPosition, element.getLength(), false, mapQ));
                    break;
            }
            if(element.getOperator().consumesReadBases())
                readPosition += element.getLength();
            if(element.getOperator().consumesReferenceBases())
                referencePosition += element.getLength();
        }

        return mAlignment = alignment;
    }

    @Override
    public String getChromosome()
    {
        return mRecord.getReferenceName();
    }

    @Override
    public Cigar getCigar()
    {
        return mRecord.getCigar();
    }

    @Override
    public int getAlignmentStart()
    {
        return mRecord.getAlignmentStart();
    }

    @Override
    public int getAlignmentEnd()
    {
        return mRecord.getAlignmentEnd();
    }

    @Override
    public int getUnclippedStart()
    {
        return mRecord.getUnclippedStart();
    }

    @Override
    public int getUnclippedEnd()
    {
        return mRecord.getUnclippedEnd();
    }

    @Override
    public int getMappingQuality()
    {
        return mRecord.getMappingQuality();
    }

    @Nullable
    public String getMateChromosome()
    {
        return isMateMapped()
                ? mRecord.getMateReferenceName()
                : null;
    }

    /**
     * 0 for no alignment
     */
    @Override
    public int getMateAlignmentStart()
    {
        return mRecord.getMateAlignmentStart();
    }

    @Override
    public boolean isMateMapped()
    {
        return mRecord.getReadPairedFlag() && !mRecord.getMateUnmappedFlag();
    }

    @Override
    public boolean isMateUnmapped()
    {
        return mRecord.getReadPairedFlag() && mRecord.getMateUnmappedFlag();
    }

    @Override
    public boolean isMatePositiveStrand()
    {
        return !mRecord.getMateNegativeStrandFlag();
    }

    @Override
    public int impliedFragmentLength()
    {
        if(isMateMapped())
        {
            if(isMateOnTheLeft())
            {
                return getUnclippedEnd() - mRecord.getMateAlignmentStart();
            }
            else
            {
                final int mateEnd = mRecord.getMateAlignmentStart() + getLength();
                return mateEnd - getUnclippedStart();
            }
        }
        else
        {
            return getUnclippedEnd() - getUnclippedStart();
        }
    }

    @Override
    public boolean isDiscordant(final int discordantPairFragmentLength)
    {
        if(!isMateMapped())
            return false;

        final int impliedFragmentLength = impliedFragmentLength();
        return !getChromosome().equals(getMateChromosome())
                || isPositiveStrand() == isMatePositiveStrand()
                || impliedFragmentLength >= discordantPairFragmentLength
                || impliedFragmentLength <= 0;
    }

    @Override
    public boolean isPositiveStrand()
    {
        return !mRecord.getReadNegativeStrandFlag();
    }

    @Override
    public <T> T getAttribute(final String name)
    {
        //noinspection unchecked
        return (T) mRecord.getAttribute(name);
    }

    @Override
    public List<SequenceDecomposer.Node> decompose()
    {
        return mDecomposition == null ? (mDecomposition = SequenceDecomposer.decompose(this)) : mDecomposition;
    }

    @Override
    public int hashCode()
    {
        final int firstOfPair = !isSecondOfPair() ? 1 : 0;
        return mRecord.getReadName().hashCode() ^ firstOfPair;
    }

    @Override
    public boolean equals(final Object obj)
    {
        if(this == obj)
            return true;
        if(obj == null || obj.getClass() != Record.class)
            return false;

        final Record other = (Record) obj;
        if(!other.mRecord.getReadName().equals(mRecord.getReadName()))
            return false;

        return isFirstOfPair() == other.isFirstOfPair();
    }

    @Override
    public String toString()
    {
        return mRecord.toString();
    }

    @Override
    public void setBases(final byte[] bases, final byte[] quals)
    {
        mRecord.setReadBases(bases);
        mRecord.setBaseQualities(quals);
        mDecomposition = null;
    }

    @Override
    public void setUnmapped()
    {
        mRecord.setReadUnmappedFlag(true);
        mRecord.setReferenceName(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME);
        mRecord.setAlignmentStart(0);
        mAlignment = null;
    }

    @Override
    public void setChromosome(final String chromosome)
    {
        mRecord.setReadUnmappedFlag(false);
        mRecord.setReferenceName(chromosome);
        mAlignment = null;
    }

    @Override
    public void setAlignmentStart(final int position)
    {
        mRecord.setReadUnmappedFlag(false);
        mRecord.setAlignmentStart(position);
        mAlignment = null;
    }

    @Override
    public void setMappingQuality(final int mapQ)
    {
        mRecord.setMappingQuality(mapQ);
    }

    @Override
    public void setCigar(final Cigar cigar)
    {
        mRecord.setCigar(cigar);
        mAlignment = null;
    }

    @Override
    public void setCigar(final String cigar)
    {
        mRecord.setCigarString(cigar);
        mAlignment = null;
    }

    @Override
    public void setMateChromosome(final String chromosome)
    {
        mRecord.setMateUnmappedFlag(false);
        mRecord.setReadPairedFlag(true);
        mRecord.setMateReferenceName(chromosome);
    }

    @Override
    public void setMateAlignmentStart(final int position)
    {
        mRecord.setMateUnmappedFlag(false);
        mRecord.setReadPairedFlag(true);
        mRecord.setMateAlignmentStart(position);
    }

    @Override
    public void setMateUnmapped()
    {
        mRecord.setMateUnmappedFlag(true);
    }

    @Override
    public void setPositiveStrand(final boolean isPositiveStrand)
    {
        mRecord.setReadNegativeStrandFlag(!isPositiveStrand);
    }

    @Override
    public void setMatePositiveStrand(final boolean isPositiveStrand)
    {
        mRecord.setMateNegativeStrandFlag(!isPositiveStrand);
    }

    @Override
    public <T> void setAttribute(final String name, final T value)
    {
        mRecord.setAttribute(name, value);
    }

    @Override
    public void setIsGermline(final boolean isGermline)
    {
        throw new IllegalStateException("Unsupported");
    }

    @Override
    public void setReadPairedFlag(final boolean isPaired)
    {
        mRecord.setReadPairedFlag(isPaired);
    }

    @Override
    public void setIsFirstOfPair(final boolean isFirstOfPair)
    {
        mRecord.setFirstOfPairFlag(isFirstOfPair);
        mRecord.setSecondOfPairFlag(!isFirstOfPair);
    }

    @Override
    public Record copy()
    {
        return new Record(mRecord.deepCopy());
    }

    public Record trimLeft(final int count)
    {
        return (Record) MutableRecord.super.trimLeft(count);
    }

    public Record trimRight(final int count)
    {
        return (Record) MutableRecord.super.trimRight(count);
    }

    @Override
    public Record flipStrand()
    {
        return (Record) MutableRecord.super.flipStrand();
    }
}
