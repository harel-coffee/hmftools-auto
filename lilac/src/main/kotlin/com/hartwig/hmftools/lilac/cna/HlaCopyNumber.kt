package com.hartwig.hmftools.lilac.cna

import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumberFile
import com.hartwig.hmftools.lilac.coverage.HlaAlleleCoverage
import com.hartwig.hmftools.lilac.coverage.HlaComplexCoverage

data class HlaCopyNumber(val allele1: Double, val allele2: Double, val allele3: Double, val allele4: Double, val allele5: Double, val allele6: Double) {

    companion object {
        private val genes = listOf("HLA-A", "HLA-B", "HLA-C")

        fun alleleCopyNumber(geneCopyNumberFile: String, tumorCoverage: HlaComplexCoverage): HlaCopyNumber {
            if (geneCopyNumberFile.isEmpty()) {
                return HlaCopyNumber(0.0,0.0, 0.0,0.0,.0,0.0)
            }

            require(tumorCoverage.alleleCoverage.size == 6)
            val geneCopyNumbers = GeneCopyNumberFile.read(geneCopyNumberFile).filter { it.gene() in genes}.sortedBy { it.gene() }
            val aCopyNumber = alleleCopyNumber(geneCopyNumbers[0], tumorCoverage.alleleCoverage.filter { it.allele.gene == "A" })
            val bCopyNumber = alleleCopyNumber(geneCopyNumbers[1], tumorCoverage.alleleCoverage.filter { it.allele.gene == "B" })
            val cCopyNumber = alleleCopyNumber(geneCopyNumbers[2], tumorCoverage.alleleCoverage.filter { it.allele.gene == "C" })

            return HlaCopyNumber(aCopyNumber.first, aCopyNumber.second, bCopyNumber.first, bCopyNumber.second, cCopyNumber.first, cCopyNumber.second)
        }

        private fun alleleCopyNumber(geneCopyNumber: GeneCopyNumber, alleleCoverage: List<HlaAlleleCoverage>): Pair<Double, Double> {
            require(alleleCoverage.size == 2)

            val minor = geneCopyNumber.minMinorAlleleCopyNumber()
            val major = geneCopyNumber.minCopyNumber() - minor

            return if (alleleCoverage[0].totalCoverage >= alleleCoverage[1].totalCoverage) {
                Pair(major, minor)
            } else {
                Pair(minor, major)
            }
        }

    }

}
