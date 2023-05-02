package com.hartwig.hmftools.markdups.umi;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

import static com.hartwig.hmftools.markdups.umi.BaseBuilder.NO_BASE;
import static com.hartwig.hmftools.markdups.umi.ConsensusOutcome.INDEL_FAIL;
import static com.hartwig.hmftools.markdups.umi.ConsensusOutcome.INDEL_MATCH;
import static com.hartwig.hmftools.markdups.umi.ConsensusOutcome.INDEL_MISMATCH;
import static com.hartwig.hmftools.markdups.umi.ConsensusReads.selectConsensusRead;

import static htsjdk.samtools.CigarOperator.D;
import static htsjdk.samtools.CigarOperator.I;
import static htsjdk.samtools.CigarOperator.M;
import static htsjdk.samtools.CigarOperator.N;
import static htsjdk.samtools.CigarOperator.S;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;

public class IndelConsensusReads
{
    private final BaseBuilder mBaseBuilder;

    public IndelConsensusReads(final BaseBuilder baseBuilder)
    {
        mBaseBuilder = baseBuilder;
    }

    public void buildIndelComponents(final List<SAMRecord> reads, final ConsensusState consensusState)
    {
        Map<String,CigarFrequency> cigarFrequencies = CigarFrequency.buildFrequencies(reads);

        if(cigarFrequencies.size() == 1)
        {
            SAMRecord selectedConsensusRead = reads.get(0);
            int baseLength = selectedConsensusRead.getReadBases().length;
            consensusState.setBaseLength(baseLength);
            consensusState.setBoundaries(selectedConsensusRead);

            mBaseBuilder.buildReadBases(reads, consensusState);
            consensusState.setOutcome(INDEL_MATCH);
            consensusState.CigarElements.addAll(selectedConsensusRead.getCigar().getCigarElements());
            // setBoundaries(consensusState);
            return;
        }

        // find the most common read by CIGAR, and where there are equal counts choose the one with the least soft-clips
        SAMRecord selectedConsensusRead = selectConsensusRead(cigarFrequencies);

        int baseLength = selectedConsensusRead.getReadBases().length;
        consensusState.setBaseLength(baseLength);
        consensusState.setBoundaries(selectedConsensusRead);

        List<ReadParseState> readStates = reads.stream().map(x -> new ReadParseState(x, consensusState.IsForward)).collect(Collectors.toList());

        int baseIndex = consensusState.IsForward ? 0 : baseLength - 1;

        List<CigarElement> selectElements = selectedConsensusRead.getCigar().getCigarElements();
        int cigarCount = selectElements.size();
        int cigarIndex = consensusState.IsForward ? 0 : cigarCount - 1;

        while(cigarIndex >= 0 && cigarIndex < cigarCount)
        {
            CigarElement element = selectElements.get(cigarIndex);

            // simplest scenario is where all reads agree about this next element
            addElementBases(consensusState, readStates, element, baseIndex);

            if(consensusState.outcome() == INDEL_FAIL)
                return;

            if(!deleteOrSplit(element.getOperator()))
            {
                if(consensusState.IsForward)
                    baseIndex += element.getLength();
                else
                    baseIndex -= element.getLength();
            }

            if(consensusState.IsForward)
                ++cigarIndex;
            else
                --cigarIndex;
        }

        // setBoundaries(consensusState);

        consensusState.setOutcome(INDEL_MISMATCH);
    }

    private void setBoundaries(final ConsensusState consensusState)
    {
        int positionLength = consensusState.CigarElements.stream()
                .filter(x -> x.getOperator().consumesReferenceBases()).mapToInt(x -> x.getLength()).sum();

        if(consensusState.IsForward)
        {
            consensusState.MaxAlignedPosEnd = consensusState.MinAlignedPosStart + positionLength - 1;
            CigarElement lastElement = consensusState.CigarElements.get(consensusState.CigarElements.size() - 1);

            if(lastElement.getOperator() == S)
                consensusState.MaxUnclippedPosEnd = consensusState.MaxAlignedPosEnd + lastElement.getLength();
            else
                consensusState.MaxUnclippedPosEnd = consensusState.MaxAlignedPosEnd;
        }
        else
        {
            consensusState.MinAlignedPosStart = consensusState.MaxAlignedPosEnd - positionLength + 1;

            CigarElement firstElement = consensusState.CigarElements.get(0);

            if(firstElement.getOperator() == S)
                consensusState.MinUnclippedPosStart = consensusState.MinAlignedPosStart - firstElement.getLength();
            else
                consensusState.MinUnclippedPosStart = consensusState.MinAlignedPosStart;
        }
    }

    private void addElementBases(
            final ConsensusState consensusState, final List<ReadParseState> readStates, final CigarElement selectedElement, int baseIndex)
    {
        int readCount = readStates.size();

        consensusState.addCigarElement(selectedElement.getLength(), selectedElement.getOperator());

        if(deleteOrSplit(selectedElement.getOperator()))
        {
            // move past the delete element and any differing aligned bases
            for(int r = 0; r < readCount; ++r)
            {
                ReadParseState read = readStates.get(r);

                if(read.exhausted())
                    continue;

                for(int i = 0; i < selectedElement.getLength(); ++i)
                {
                    if(read.elementType() == I)
                        read.skipInsert();

                    if(deleteOrSplit(read.elementType()) || alignedOrSoftClip(read.elementType()))
                    {
                        read.moveNext();
                    }
                }
            }

            return;
        }

        byte[] locationBases = new byte[readCount];
        byte[] locationQuals = new byte[readCount];

        for(int i = 0; i < selectedElement.getLength(); ++i)
        {
            boolean hasMismatch = false;
            int maxQual = 0;
            byte firstBase = NO_BASE;

            for(int r = 0; r < readCount; ++r)
            {
                locationBases[r] = NO_BASE;
            }

            for(int r = 0; r < readCount; ++r)
            {
                ReadParseState read = readStates.get(r);

                if(read.exhausted())
                    continue;

                // check for element type differences:

                // first skip past any insert if the selected element is aligned
                if(selectedElement.getOperator() == M && read.elementType() == I)
                    read.skipInsert();

                boolean useBase = true;
                boolean moveNext = true;

                if(read.elementType() != selectedElement.getOperator())
                {
                    // when aligned (M) is selected:
                    // - insert - skip past the insert's bases
                    // - delete - move along in step but cannot use base
                    // - ignore read with soft-clipped bases since they are likely misaligned

                    // when insert (I) is selected:
                    // - aligned or delete - pause the index and don't use the base

                    if(selectedElement.getOperator() == M)
                    {
                        if(deleteOrSplit(read.elementType()) || read.elementType() == S)
                        {
                            useBase = false;
                        }
                        else if(read.elementType() == I)
                        {
                            // handled above, implies a bug or consecutive insert
                            // logMismatchFail(consensusState, r, read, selectedElement);
                            consensusState.setOutcome(INDEL_FAIL);
                            return;
                        }
                    }
                    else if(selectedElement.getOperator() == I)
                    {
                        if(read.elementType() == M || deleteOrSplit(read.elementType()))
                        {
                            moveNext = false;
                            useBase = false;
                        }
                    }
                }

                if(useBase)
                {
                    locationBases[r] = read.currentBase();
                    locationQuals[r] = read.currentBaseQual();

                    if(firstBase == NO_BASE)
                        firstBase = locationBases[r];
                    else
                        hasMismatch |= locationBases[r] != firstBase;

                    maxQual = max(locationQuals[r], maxQual);
                }

                if(moveNext)
                    read.moveNext();
            }

            if(!hasMismatch)
            {
                consensusState.Bases[baseIndex] = firstBase;
                consensusState.BaseQualities[baseIndex] = (byte) maxQual;
            }
            else
            {
                int basePosition = consensusState.MinUnclippedPosStart + baseIndex;

                byte[] consensusBaseAndQual = mBaseBuilder.determineBaseAndQual(
                        locationBases, locationQuals, consensusState.Chromosome, basePosition);

                consensusState.Bases[baseIndex] = consensusBaseAndQual[0];
                consensusState.BaseQualities[baseIndex] = consensusBaseAndQual[1];
            }

            if(consensusState.IsForward)
                ++baseIndex;
            else
                --baseIndex;
        }
    }

    private static boolean deleteOrSplit(final CigarOperator operator)
    {
        return operator == D || operator == N;
    }

    public static boolean alignedOrSoftClip(final CigarOperator operator)
    {
        return operator == M || operator == S;
    }
}
