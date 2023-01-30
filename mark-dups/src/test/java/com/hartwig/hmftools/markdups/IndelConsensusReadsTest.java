package com.hartwig.hmftools.markdups;

import static com.hartwig.hmftools.common.test.GeneTestUtils.CHR_1;
import static com.hartwig.hmftools.markdups.ConsensusReadsTest.UMI_ID_1;
import static com.hartwig.hmftools.markdups.ConsensusReadsTest.nextUmiReadId;
import static com.hartwig.hmftools.markdups.TestUtils.REF_BASES;
import static com.hartwig.hmftools.markdups.TestUtils.REF_BASES_A;
import static com.hartwig.hmftools.markdups.umi.ConsensusOutcome.INDEL_MATCH;
import static com.hartwig.hmftools.markdups.umi.ConsensusOutcome.INDEL_MISMATCH;
import static com.hartwig.hmftools.markdups.umi.IndelConsensusReads.haveConsistentCigars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.test.MockRefGenome;
import com.hartwig.hmftools.common.test.ReadIdGenerator;
import com.hartwig.hmftools.markdups.umi.ConsensusReadInfo;
import com.hartwig.hmftools.markdups.umi.ConsensusReads;
import com.hartwig.hmftools.markdups.umi.UmiConfig;

import org.junit.Test;

import htsjdk.samtools.SAMRecord;

public class IndelConsensusReadsTest
{
    private final MockRefGenome mRefGenome;
    private final UmiConfig mConfig;
    private final ConsensusReads mConsensusReads;
    private final ReadIdGenerator mReadIdGen;

    public IndelConsensusReadsTest()
    {
        mConfig = new UmiConfig(true);
        mRefGenome = new MockRefGenome();
        mRefGenome.RefGenomeMap.put(CHR_1, REF_BASES);
        mConsensusReads = new ConsensusReads(mConfig, mRefGenome);
        mReadIdGen = new ReadIdGenerator();
    }

    @Test
    public void testIndelCompatibility()
    {
        int posStart = 13;

        String consensusBases = REF_BASES_A.substring(0, 5) + "C" + REF_BASES_A.substring(5, 10);
        String firstCigar = "2S3M1I3M2S";
        SAMRecord read1 = createSamRecord(nextReadId(), posStart, consensusBases, firstCigar, false);

        SAMRecord read2 = createSamRecord(nextReadId(), posStart, consensusBases, firstCigar, false);
        assertTrue(haveConsistentCigars(Lists.newArrayList(read1, read2)));

        // differing soft-clipping
        read2 = createSamRecord(nextReadId(), posStart, consensusBases, "3S3M1I3M3S", false);
        assertTrue(haveConsistentCigars(Lists.newArrayList(read1, read2)));

        // differing initial and end alignment
        read2 = createSamRecord(nextReadId(), posStart, consensusBases, "10M1I5M", false);
        assertTrue(haveConsistentCigars(Lists.newArrayList(read1, read2)));

        // differing initial and end alignment and soft-clipping
        read2 = createSamRecord(nextReadId(), posStart, consensusBases, "3S4M1I4M3S", false);
        assertTrue(haveConsistentCigars(Lists.newArrayList(read1, read2)));

        // now test differences

        // different indel length
        read2 = createSamRecord(nextReadId(), posStart, consensusBases, "2S3M2I3M2S", false);
        assertFalse(haveConsistentCigars(Lists.newArrayList(read1, read2)));

        firstCigar = "5M2D5M1I5M";
        read1 = createSamRecord(nextReadId(), posStart, consensusBases, firstCigar, false);
        read2 = createSamRecord(nextReadId(), posStart, consensusBases, firstCigar, false);
        assertTrue(haveConsistentCigars(Lists.newArrayList(read1, read2)));

        read2 = createSamRecord(nextReadId(), posStart, consensusBases, "5M2D4M1I5M", false);
        assertFalse(haveConsistentCigars(Lists.newArrayList(read1, read2)));

        read1 = createSamRecord(nextReadId(), posStart, consensusBases, "10M", false);
        read2 = createSamRecord(nextReadId(), posStart, consensusBases, "4M1I6M", false);
        assertFalse(haveConsistentCigars(Lists.newArrayList(read1, read2)));
    }

    @Test
    public void testInsertMismatches()
    {
        final List<SAMRecord> reads = Lists.newArrayList();

        // first 2 reads without an insert vs 1 with one ignored
        SAMRecord read1 = createSamRecord(nextReadId(), 13, REF_BASES_A.substring(0, 10), "2S8M", false);
        reads.add(read1);

        // matching but without the soft-clip
        SAMRecord read2 = createSamRecord(nextReadId(), 11, REF_BASES_A.substring(0, 10), "7M3S", false);
        reads.add(read2);

        String indelBases = REF_BASES_A.substring(0, 5) + "C" + REF_BASES_A.substring(5, 10);
        SAMRecord read3 = createSamRecord(nextReadId(), 12, indelBases, "1S4M1I4M1S", false);
        reads.add(read3);

        String consensusBases = REF_BASES_A.substring(0, 10);

        ConsensusReadInfo readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(INDEL_MISMATCH, readInfo.Outcome);
        assertEquals(consensusBases, readInfo.ConsensusRead.getReadString());
        assertEquals("10M", readInfo.ConsensusRead.getCigarString());
        assertEquals(11, readInfo.ConsensusRead.getAlignmentStart());
    }

    @Test
    public void testDeleteMismatches()
    {
        final List<SAMRecord> reads = Lists.newArrayList();

        // first 2 reads without a delete vs 1 with one ignored
        SAMRecord read1 = createSamRecord(nextReadId(), 13, REF_BASES_A.substring(0, 10), "2S8M", false);
        reads.add(read1);

        // matching but without the soft-clip
        SAMRecord read2 = createSamRecord(nextReadId(), 11, REF_BASES_A.substring(0, 10), "7M3S", false);
        reads.add(read2);

        String indelBases = REF_BASES_A.substring(0, 5) + REF_BASES_A.substring(6, 10);
        SAMRecord read3 = createSamRecord(nextReadId(), 12, indelBases, "1S4M1D3M1S", false);
        reads.add(read3);

        indelBases = REF_BASES_A.substring(0, 3) + REF_BASES_A.substring(7, 10);
        SAMRecord read4 = createSamRecord(nextReadId(), 11, indelBases, "3M4D3M", false);
        reads.add(read4);

        indelBases = REF_BASES_A.substring(0, 3) + REF_BASES_A.substring(4, 6) + REF_BASES_A.substring(7, 10);
        SAMRecord read5 = createSamRecord(nextReadId(), 11, indelBases, "3M1D2M1D3M", false);
        reads.add(read5);

        String consensusBases = REF_BASES_A.substring(0, 10);

        ConsensusReadInfo readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(INDEL_MISMATCH, readInfo.Outcome);
        assertEquals(consensusBases, readInfo.ConsensusRead.getReadString());
        assertEquals("10M", readInfo.ConsensusRead.getCigarString());
        assertEquals(11, readInfo.ConsensusRead.getAlignmentStart());
    }

    private String nextReadId() { return nextUmiReadId(UMI_ID_1, mReadIdGen); }

    private static SAMRecord createSamRecord(
            final String readId, int readStart, final String readBases, final String cigar, boolean isReversed)
    {
        return TestUtils.createSamRecord(
                readId, CHR_1, readStart, readBases, cigar, CHR_1, 5000, isReversed, false, null);
    }

}
