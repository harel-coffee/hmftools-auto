package com.hartwig.hmftools.cdr3

import com.hartwig.hmftools.cdr3.layout.LayoutTree
import com.hartwig.hmftools.cdr3.layout.LayoutTreeBuilder
import com.hartwig.hmftools.cdr3.layout.ReadLayout
import com.hartwig.hmftools.cdr3.layout.ReadLayoutBuilder
import htsjdk.samtools.util.SequenceUtil
import org.apache.logging.log4j.LogManager

// helper class to convert from the outer VJ classes to the overlay classes
object VJReadLayoutAdaptor
{
    private val sLogger = LogManager.getLogger(javaClass)

    @JvmStatic
    fun readCandidateToLayoutRead(readCandidate: VJReadCandidate) : ReadLayout.Read
    {
        if (readCandidate.vjGeneType.isV)
        {
            // for V, we layout from the anchor start, left to right
            // we are only interested in what comes after anchor start
            val vAnchorStart: Int = readCandidate.anchorOffsetStart
            return ReadLayout.Read(readCandidate, ReadKey(readCandidate.read.readName, readCandidate.read.firstOfPairFlag),
                readCandidate.readSequence.substring(vAnchorStart),
                readCandidate.baseQualities.drop(vAnchorStart).toByteArray(),
                0)
        }
        else
        {
            // for J, we layout from the anchor last, right to left
            // we are only interested in what comes before anchor end
            val jAnchorEnd: Int = readCandidate.anchorOffsetEnd
            return ReadLayout.Read(readCandidate, ReadKey(readCandidate.read.readName, readCandidate.read.firstOfPairFlag),
                readCandidate.readSequence.substring(0, jAnchorEnd),
                readCandidate.baseQualities.take(jAnchorEnd).toByteArray(),
                jAnchorEnd - 1)
        }
    }

    @JvmStatic
    fun toReadCandidate(read: ReadLayout.Read) : VJReadCandidate
    {
        return read.source as VJReadCandidate
    }

    @JvmStatic
    fun getReadCandidates(layout: ReadLayout) : List<VJReadCandidate>
    {
        return layout.reads.map({ read: ReadLayout.Read -> toReadCandidate(read)})
    }

    @JvmStatic
    fun getImgtGenes(layout: ReadLayout) : List<VJGene>
    {
        val vGenes: List<VJGene> = layout.reads
            .flatMap({ read: ReadLayout.Read -> toReadCandidate(read).vjGenes})
            .distinctBy({ imgtGene -> imgtGene.id })
        return vGenes
    }

    @JvmStatic
    fun getAnchorRange(geneType: VJGeneType, overlay: ReadLayout) : IntRange?
    {
        val overlayReads = overlay.reads.map { o: ReadLayout.Read -> toReadCandidate(o) }
            .toList()

        // we more or less get the top one
        val anchorLength = overlayReads.maxOfOrNull { o: VJReadCandidate -> o.anchorOffsetEnd - o.anchorOffsetStart } ?: 0

        // for V read we align to anchor start, for J read we align to last base of the anchor
        if (geneType.isV)
            return overlay.alignedPosition until overlay.alignedPosition + anchorLength
        else if (geneType.isJ)
            return overlay.alignedPosition - anchorLength + 1 .. overlay.alignedPosition
        return null
    }

    // we also make sure they are aligned to codons
    @JvmStatic
    fun getCdr3Range(geneType: VJGeneType, overlay: ReadLayout) : IntRange?
    {
        // for V it is the sequence after the aligned position
        // for J it is the sequence before
        if (geneType.isV)
            return overlay.alignedPosition until overlay.consensusSequence().length
        else if (geneType.isJ)
            return 0 until overlay.alignedPosition
        return null
    }

    @JvmStatic
    fun getAnchorSequence(geneType: VJGeneType, overlay: ReadLayout) : String
    {
        return overlay.consensusSequence().substring(getAnchorRange(geneType, overlay)!!)
    }

    @JvmStatic
    fun getAnchorSupport(geneType: VJGeneType, overlay: ReadLayout) : String
    {
        return overlay.highQualSupportString().substring(getAnchorRange(geneType, overlay)!!)
    }

    @JvmStatic
    fun getCdr3Sequence(geneType: VJGeneType, overlay: ReadLayout) : String
    {
        return overlay.consensusSequence().substring(getCdr3Range(geneType, overlay)!!)
    }

    @JvmStatic
    fun getCdr3Support(geneType: VJGeneType, overlay: ReadLayout) : String
    {
        return overlay.highQualSupportString().substring(getCdr3Range(geneType, overlay)!!)
    }

    @JvmStatic
    fun buildOverlays(geneType: VJGeneType, readCandidates: List<VJReadCandidate>,
                      minBaseQuality: Int, minMatchedBases: Int, minMatchRatio: Double,
                      numBasesToTrim: Int = 0)
    : List<ReadLayout>
    {
        sLogger.info("building {} overlays from {} reads", geneType, readCandidates.size)

        val overlayInputs = readCandidates.map({ o -> readCandidateToLayoutRead(o) }).toList()
        // for V type we build from left, J we build from right
        val overlayBuilder = ReadLayoutBuilder(
            inputReads = overlayInputs,
            minBaseQuality = minBaseQuality,
            minMatchedBases = minMatchedBases,
            minMatchRatio = minMatchRatio,
            alignLeft = geneType.isV)
        val readOverlays = overlayBuilder.build()
        return readOverlays

        /*

        val useReverseComplement = geneType.isJ
        val overlayInputs = readCandidates.map({ o -> readCandidateToLayoutRead(o) })
            .toList()

        // convert to LayoutTree.Read
        val layoutTreeReads = if (useReverseComplement)
                overlayInputs.map({ r -> LayoutTree.Read(r.source, r.readKey, SequenceUtil.reverseComplement(r.sequence),
                    r.baseQualities.reversed().toByteArray(), 0)}).toList()
            else
                overlayInputs.map({ r -> LayoutTree.Read(r.source, r.readKey, r.sequence,
                    r.baseQualities, 0)}).toList()

        // for V type we build from left, J we build from right
        val layoutTreeBuilder = LayoutTreeBuilder(
            inputReads = layoutTreeReads,
            minBaseQuality = minBaseQuality,
            minOverlapBases = minMatchedBases,
            minBaseHighQualCount = 1)
        val layoutTree = layoutTreeBuilder.build()

        return layoutTree.buildReadLayouts(useReverseComplement)

         */
    }

    /*
        sLogger.info("Overlay type: {}， read count: {}, anchor length: {}", geneType, overlay.reads.size, anchorLength)

        // get the sequence, remember aligned position is the anchor start

        // get the sequence, remember aligned position is the anchor start
        val sequence = overlay.sequence
        val support = overlay.supportString()

        if (geneType.isV)
        {
            val anchorStart = overlay.alignedPosition - anchorLength
            val anchorEnd = overlay.alignedPosition
            val anchor = sequence.substring(anchorStart, anchorEnd)
            val cdr3 = sequence.substring(anchorEnd)
            val anchorSupport = support.substring(anchorStart, anchorEnd)
            val cdr3Support = support.substring(anchorEnd)
            sLogger.info("V sequence: {}-{}", anchor, cdr3)
            sLogger.info("V support:  {}-{}", anchorSupport, cdr3Support)
            sLogger.info("V AA seq:  {}-{}", Codons.aminoAcidFromBases(anchor), Codons.aminoAcidFromBases(cdr3))
        } else if (geneType.isJ)
        {
            val anchorStart = overlay.alignedPosition
            val anchorEnd = overlay.alignedPosition + anchorLength
            val anchor = sequence.substring(anchorStart, anchorEnd)
            val cdr3 = sequence.substring(0, anchorStart)
            val anchorSupport = support.substring(anchorStart, anchorEnd)
            val cdr3Support = support.substring(0, anchorStart)
            sLogger.info("J sequence: {}-{}", cdr3, anchor)
            sLogger.info("J support:  {}-{}", cdr3Support, anchorSupport)
            sLogger.info("J AA seq:  {}-{}", Codons.aminoAcidFromBases(cdr3), Codons.aminoAcidFromBases(anchor))
        }
    }
     */
}