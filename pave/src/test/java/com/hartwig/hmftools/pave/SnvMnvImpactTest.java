package com.hartwig.hmftools.pave;

import static com.hartwig.hmftools.common.codon.Nucleotides.reverseStrandBases;
import static com.hartwig.hmftools.common.fusion.FusionCommon.NEG_STRAND;
import static com.hartwig.hmftools.common.fusion.FusionCommon.POS_STRAND;
import static com.hartwig.hmftools.common.gene.CodingBaseData.PHASE_0;
import static com.hartwig.hmftools.common.gene.CodingBaseData.PHASE_1;
import static com.hartwig.hmftools.common.gene.CodingBaseData.PHASE_2;
import static com.hartwig.hmftools.common.gene.TranscriptCodingType.CODING;
import static com.hartwig.hmftools.common.gene.TranscriptRegionType.EXONIC;
import static com.hartwig.hmftools.common.test.GeneTestUtils.CHR_1;
import static com.hartwig.hmftools.common.test.GeneTestUtils.GENE_ID_1;
import static com.hartwig.hmftools.common.test.GeneTestUtils.GENE_ID_2;
import static com.hartwig.hmftools.common.test.GeneTestUtils.TRANS_ID_1;
import static com.hartwig.hmftools.common.test.GeneTestUtils.TRANS_ID_2;
import static com.hartwig.hmftools.common.test.GeneTestUtils.createTransExons;
import static com.hartwig.hmftools.common.test.MockRefGenome.generateRandomBases;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.variant.impact.VariantEffect.MISSENSE;
import static com.hartwig.hmftools.common.variant.impact.VariantEffect.SYNONYMOUS;
import static com.hartwig.hmftools.pave.ImpactTestUtils.createMockGenome;
import static com.hartwig.hmftools.pave.ImpactTestUtils.createNegTranscript;
import static com.hartwig.hmftools.pave.ImpactTestUtils.createPosTranscript;
import static com.hartwig.hmftools.pave.ImpactTestUtils.generateAlt;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.hartwig.hmftools.common.codon.AminoAcids;
import com.hartwig.hmftools.common.gene.TranscriptData;
import com.hartwig.hmftools.common.test.MockRefGenome;

import org.junit.Test;

public class SnvMnvImpactTest
{
    @Test
    public void testMnvBasic()
    {
        MockRefGenome refGenome = createMockGenome();
        String refBases = refGenome.RefGenomeMap.get(CHR_1);

        TranscriptData transDataPos = createPosTranscript();

        ImpactClassifier classifier = new ImpactClassifier(refGenome);

        // MNV spanning 2 codons at all positions - codons 30-32 and 33-35
        int pos = 30;
        String refCodonBases = refBases.substring(pos, pos + 6);

        String ref = refCodonBases.substring(0, 4);
        String alt = generateAlt(ref);
        VariantData var = new VariantData(CHR_1, pos, ref, alt);

        VariantTransImpact impact = classifier.classifyVariant(var, transDataPos);

        // first check general coding context fields
        assertEquals(7, impact.codingContext().CodingBase);
        assertEquals(30, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(33, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_1, impact.codingContext().UpstreamPhase);
        assertEquals(2, impact.codingContext().ExonRank);
        assertEquals(CODING, impact.codingContext().CodingType);
        assertEquals(EXONIC, impact.codingContext().RegionType);

        // then the protein context
        assertTrue(impact.proteinContext() != null);
        assertEquals(3, impact.proteinContext().CodonIndex);

        String altCodonBases = alt + refCodonBases.substring(4, 6);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        // shift up one base
        pos = 31;
        ref = refCodonBases.substring(1, 5);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, pos, ref, alt);
        impact = classifier.classifyVariant(var, transDataPos);

        assertEquals(8, impact.codingContext().CodingBase);
        assertEquals(31, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(34, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_2, impact.codingContext().UpstreamPhase);
        assertEquals(3, impact.proteinContext().CodonIndex);

        altCodonBases = refCodonBases.substring(0, 1) + alt + refCodonBases.substring(5, 6);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        pos = 32;
        ref = refCodonBases.substring(2, 6);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, pos, ref, alt);
        impact = classifier.classifyVariant(var, transDataPos);

        assertEquals(9, impact.codingContext().CodingBase);
        assertEquals(32, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(35, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_0, impact.codingContext().UpstreamPhase);
        assertEquals(3, impact.proteinContext().CodonIndex);

        altCodonBases = refCodonBases.substring(0, 2) + alt;
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        // again but on negative strand
        TranscriptData transDataNeg = createNegTranscript();

        // MNV spanning 2 codons at all positions - codons 80-78 and 77-75
        pos = 75;
        refCodonBases = refBases.substring(pos, pos + 6);

        ref = refCodonBases.substring(2, 6);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, 77, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);

        // first check general coding context fields
        assertEquals(7, impact.codingContext().CodingBase);
        assertEquals(77, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(80, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_1, impact.codingContext().UpstreamPhase);
        assertEquals(2, impact.codingContext().ExonRank);
        assertEquals(CODING, impact.codingContext().CodingType);
        assertEquals(EXONIC, impact.codingContext().RegionType);

        // then the protein context
        assertTrue(impact.proteinContext() != null);
        assertEquals(3, impact.proteinContext().CodonIndex);

        altCodonBases = refCodonBases.substring(0, 2) + alt;
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        // shift along 1 each time
        ref = refCodonBases.substring(1, 5);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, 76, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);

        // first check general coding context fields
        assertEquals(8, impact.codingContext().CodingBase);
        assertEquals(76, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(79, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_2, impact.codingContext().UpstreamPhase);
        assertEquals(3, impact.proteinContext().CodonIndex);

        altCodonBases = refCodonBases.substring(0, 1) + alt + refCodonBases.substring(5, 6);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        // and again
        ref = refCodonBases.substring(0, 4);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, 75, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);

        // first check general coding context fields
        assertEquals(9, impact.codingContext().CodingBase);
        assertEquals(75, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(78, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_0, impact.codingContext().UpstreamPhase);
        assertEquals(3, impact.proteinContext().CodonIndex);

        altCodonBases = alt + refCodonBases.substring(4, 6);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);
    }

    @Test
    public void testMnvAcrossSplice()
    {
        MockRefGenome refGenome = createMockGenome();

        ImpactClassifier classifier = new ImpactClassifier(refGenome);

        // MNV spanning exon boundary - shortens the effect
        String refBases = refGenome.RefGenomeMap.get(CHR_1);

        TranscriptData transDataPos = createPosTranscript();

        // firstly a 2-lots across the exon
        String refCodonBases = refBases.substring(15, 21);

        int pos = 20;
        String ref = refBases.substring(pos, pos + 2);
        String alt = generateAlt(ref);
        VariantData var = new VariantData(CHR_1, pos, ref, alt);

        VariantTransImpact impact = classifier.classifyVariant(var, transDataPos);

        // first check general coding context fields
        assertEquals(6, impact.codingContext().CodingBase);
        assertEquals(20, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(20, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_0, impact.codingContext().UpstreamPhase);
        assertEquals(1, impact.codingContext().ExonRank);
        assertEquals(CODING, impact.codingContext().CodingType);
        assertTrue(impact.codingContext().SpansSpliceJunction);
        assertEquals(EXONIC, impact.codingContext().RegionType);

        assertTrue(impact.proteinContext() != null);
        assertEquals(2, impact.proteinContext().CodonIndex);
        assertEquals(refCodonBases.substring(3, 6), impact.proteinContext().RefCodonBases);

        String altCodonBases = refCodonBases.substring(3, 5) + alt.substring(0, 1);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        // same again but spanning 2 exons with the codons: 36-38, 39-50, 51-53
        refCodonBases = refBases.substring(36, 41) + refBases.substring(50, 54);

        pos = 38;
        ref = refBases.substring(pos, pos + 4);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataPos);

        // first check general coding context fields
        assertEquals(15, impact.codingContext().CodingBase);
        assertEquals(38, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(40, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_0, impact.codingContext().UpstreamPhase);
        assertEquals(2, impact.codingContext().ExonRank);
        assertEquals(CODING, impact.codingContext().CodingType);
        assertEquals(EXONIC, impact.codingContext().RegionType);
        assertTrue(impact.proteinContext() != null);
        assertEquals(5, impact.proteinContext().CodonIndex);
        assertEquals(refBases.substring(36, 41) + refBases.substring(50, 51), impact.proteinContext().RefCodonBases);

        altCodonBases = refBases.substring(36, 38) + alt.substring(0, 3) + refBases.substring(50, 51);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        // and starting before the next exon
        pos = 48;
        ref = refBases.substring(pos, pos + 4);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataPos);

        // first check general coding context fields
        assertEquals(18, impact.codingContext().CodingBase);
        assertEquals(50, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(51, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_0, impact.codingContext().UpstreamPhase);
        assertEquals(3, impact.codingContext().ExonRank);
        assertEquals(CODING, impact.codingContext().CodingType);
        assertEquals(EXONIC, impact.codingContext().RegionType);

        assertTrue(impact.proteinContext() != null);
        assertEquals(6, impact.proteinContext().CodonIndex);
        assertEquals(refBases.substring(39, 41) + refBases.substring(50, 54), impact.proteinContext().RefCodonBases);

        altCodonBases = refBases.substring(39, 41) + alt.substring(2, 4) + refBases.substring(52, 54);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        // for negative strand, is the phase set correctly even though the variant starts in the upstream intron?

        TranscriptData transDataNeg = createNegTranscript();

        // and starting at the end of the last exon
        pos = 88;
        ref = refBases.substring(pos, pos + 3);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);

        // first check general coding context fields
        assertEquals(6, impact.codingContext().CodingBase);
        assertEquals(90, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(90, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_0, impact.codingContext().UpstreamPhase);
        assertEquals(1, impact.codingContext().ExonRank);
        assertTrue(impact.codingContext().SpansSpliceJunction);
        assertEquals(CODING, impact.codingContext().CodingType);
        assertEquals(EXONIC, impact.codingContext().RegionType);

        assertTrue(impact.proteinContext() != null);
        assertEquals(2, impact.proteinContext().CodonIndex);
        assertEquals(refBases.substring(90, 93), impact.proteinContext().RefCodonBases);

        altCodonBases = alt.substring(2, 3) + refBases.substring(91, 93);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        // spanning exons 2 and 3 codons: 74-72, 71-60, 59-57
        pos = 68;
        ref = refBases.substring(pos, pos + 4);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);

        // first check general coding context fields
        assertEquals(16, impact.codingContext().CodingBase);
        assertEquals(70, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(71, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_1, impact.codingContext().UpstreamPhase);
        assertEquals(2, impact.codingContext().ExonRank);
        assertTrue(impact.codingContext().SpansSpliceJunction);

        assertTrue(impact.proteinContext() != null);
        assertEquals(6, impact.proteinContext().CodonIndex);
        assertEquals(refBases.substring(60, 61) + refBases.substring(70, 72), impact.proteinContext().RefCodonBases);

        altCodonBases = refBases.substring(60, 61) + alt.substring(2, 4);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);

        pos = 59;
        ref = refBases.substring(pos, pos + 4);
        alt = generateAlt(ref);
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);

        // first check general coding context fields
        assertEquals(18, impact.codingContext().CodingBase);
        assertEquals(59, impact.codingContext().CodingPositionRange[SE_START]);
        assertEquals(60, impact.codingContext().CodingPositionRange[SE_END]);
        assertEquals(PHASE_0, impact.codingContext().UpstreamPhase);
        assertEquals(3, impact.codingContext().ExonRank);
        assertTrue(impact.codingContext().SpansSpliceJunction);

        assertTrue(impact.proteinContext() != null);
        assertEquals(6, impact.proteinContext().CodonIndex);
        assertEquals(refBases.substring(57, 61) + refBases.substring(70, 72), impact.proteinContext().RefCodonBases);

        altCodonBases = refBases.substring(57, 59) + alt.substring(0, 2) + refBases.substring(70, 72);
        assertEquals(altCodonBases, impact.proteinContext().AltCodonBases);
    }

    @Test
    public void testSynonymousMissenseImpacts()
    {
        final MockRefGenome refGenome = new MockRefGenome();

        int[] exonStarts = { 0, 100, 200 };

        // codons start on at 10, 13, 16 etc
        Integer codingStart = new Integer(10);
        Integer codingEnd = new Integer(250);

        TranscriptData transDataPos = createTransExons(
                GENE_ID_1, TRANS_ID_1, POS_STRAND, exonStarts, 80, codingStart, codingEnd, false, "");

        ImpactClassifier classifier = new ImpactClassifier(refGenome);

        String chr1Bases = generateRandomBases(300);

        // set the specific AAs for a region of this mock ref genome
        // S: TCA -> TCG - so last base of codon can change
        // I: ATC -> ATT
        // L: TTA -> CTA - first base changes

        //                     40      43      46
        String aminoAcidSeq = "TCA" + "ATC" + "TTA";
        chr1Bases = chr1Bases.substring(0, 40) + aminoAcidSeq + chr1Bases.substring(49);
        refGenome.RefGenomeMap.put(CHR_1, chr1Bases);

        // test SNVs at the 1st and 3rd codon bases

        // last base of a codon changes
        int codonPos = 40;
        String codon = chr1Bases.substring(codonPos, codonPos + 3);
        String aminoAcid = AminoAcids.findAminoAcidForCodon(codon);

        String alt = "G";
        String synCodon = chr1Bases.substring(codonPos, codonPos + 2) + alt;
        assertTrue(aminoAcid.equals(AminoAcids.findAminoAcidForCodon(synCodon)));

        int pos = 42;
        String ref = chr1Bases.substring(pos, pos + 1);
        VariantData var = new VariantData(CHR_1, pos, ref, alt);

        VariantTransImpact impact = classifier.classifyVariant(var, transDataPos);
        assertEquals(SYNONYMOUS, impact.topEffect());

        // now missense
        pos = 41;
        ref = chr1Bases.substring(pos, pos + 1);
        alt = "T";
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataPos);
        assertEquals(MISSENSE, impact.topEffect());

        // first base of a codon changes
        codonPos = 46;
        codon = chr1Bases.substring(codonPos, codonPos + 3);
        aminoAcid = AminoAcids.findAminoAcidForCodon(codon);

        alt = "C";
        synCodon = alt + chr1Bases.substring(codonPos + 1, codonPos + 3);
        assertTrue(aminoAcid.equals(AminoAcids.findAminoAcidForCodon(synCodon)));

        pos = 46;
        ref = chr1Bases.substring(pos, pos + 1);
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataPos);
        assertEquals(SYNONYMOUS, impact.topEffect());

        // test for an MNV spanning 3 codons - first in the last codon pos at 42 then all the next and 2 into the final one at 46
        pos = 42;
        ref = chr1Bases.substring(pos, pos + 5);
        alt = "G" + "ATT" + "C";
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataPos);
        assertEquals(SYNONYMOUS, impact.topEffect());

        // now missense by change middle codon
        alt = "G" + "AAT" + "C";
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataPos);
        assertEquals(MISSENSE, impact.topEffect());

        // test reverse strand
        TranscriptData transDataNeg = createTransExons(
                GENE_ID_2, TRANS_ID_2, NEG_STRAND, exonStarts, 80, codingStart, codingEnd, false, "");

        // coding starts at 250 so codons start at 250, 247, 244 etc
        // still change the AA sequence S, I then L - for the range 239-241, 242-244 and 245-247
        String aminoAcidSeqRev = reverseStrandBases(aminoAcidSeq);
        chr1Bases = chr1Bases.substring(0, 239) + aminoAcidSeqRev + chr1Bases.substring(248);
        refGenome.RefGenomeMap.put(CHR_1, chr1Bases);

        codonPos = 242;
        codon = chr1Bases.substring(codonPos, codonPos + 3);
        aminoAcid = AminoAcids.findAminoAcidForCodon(reverseStrandBases(codon));

        // change last base of a codon, which is the first base
        alt = "A";
        synCodon = alt + chr1Bases.substring(codonPos + 1, codonPos + 3);
        assertTrue(aminoAcid.equals(AminoAcids.findAminoAcidForCodon(reverseStrandBases(synCodon))));

        pos = 242;
        ref = chr1Bases.substring(pos, pos + 1);
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);
        assertEquals(SYNONYMOUS, impact.topEffect());

        // now missense
        pos = 243;
        ref = chr1Bases.substring(pos, pos + 1);
        alt = "T";
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);
        assertEquals(MISSENSE, impact.topEffect());

        // test a MNV spanning 3 codons as before
        pos = 241;
        ref = chr1Bases.substring(pos, pos + 5);
        alt = reverseStrandBases("G" + "ATT" + "C");
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);
        assertEquals(SYNONYMOUS, impact.topEffect());

        // and missense
        alt = reverseStrandBases("G" + "AAT" + "C");
        var = new VariantData(CHR_1, pos, ref, alt);

        impact = classifier.classifyVariant(var, transDataNeg);
        assertEquals(MISSENSE, impact.topEffect());
    }

}
