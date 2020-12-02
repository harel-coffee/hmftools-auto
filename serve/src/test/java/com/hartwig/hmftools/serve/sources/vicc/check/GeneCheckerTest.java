package com.hartwig.hmftools.serve.sources.vicc.check;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Sets;

import org.junit.Test;

public class GeneCheckerTest {

    @Test
    public void canCorrectlyAssessGenes() {
        GeneChecker geneChecker = new GeneChecker(Sets.newHashSet("BRAF"));

        assertTrue(geneChecker.isValidGene("BRAF"));
        assertTrue(geneChecker.isValidGene("IGH"));

        assertFalse(geneChecker.isValidGene("I am not a gene"));
        assertFalse(geneChecker.isValidGene(null));
    }
}