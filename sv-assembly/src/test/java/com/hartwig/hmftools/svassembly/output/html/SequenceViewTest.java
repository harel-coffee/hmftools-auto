package com.hartwig.hmftools.svassembly.output.html;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.hartwig.hmftools.common.test.MockRefGenome;
import com.hartwig.hmftools.svassembly.models.AlignedSequence;
import com.hartwig.hmftools.svassembly.models.Alignment;
import com.hartwig.hmftools.svassembly.models.BasicAlignedSequence;

import org.junit.Test;

public class SequenceViewTest
{
    @Test
    public void canRealignIdentical()
    {
        final List<Alignment> legend = List.of(
                new Alignment("2", 100, 1, 12, false, 60)
        );
        final AlignedSequence aligned = new BasicAlignedSequence("AAATTTCCCGGG".getBytes(), new byte[12], legend);

        final AlignedSequence realigned = new SequenceView(new MockRefGenome()).realignSequenceToLegend(aligned, legend);

        assertThat(realigned).isNotNull();
        assertThat(realigned.getAlignmentBlocks()).hasSize(1);
        assertThat(realigned.getAlignmentBlocks().get(0).Chromosome).isEqualTo("2");
        assertThat(realigned.getAlignmentBlocks().get(0).ReferenceStartPosition).isEqualTo(100);
        assertThat(realigned.getAlignmentBlocks().get(0).SequenceStartPosition).isEqualTo(1);
        assertThat(realigned.getAlignmentBlocks().get(0).Length).isEqualTo(12);
        assertThat(realigned.getAlignmentBlocks().get(0).Inverted).isEqualTo(false);
    }

    @Test
    public void canRealignSubset()
    {
        final List<Alignment> legend = List.of(
                new Alignment("2", 100, 0, 12, false, 60)
        );

        final List<Alignment> alignment = List.of(
                new Alignment("2", 103, 1, 6, false, 60)
        );
        final AlignedSequence aligned = new BasicAlignedSequence("Test", "AAATTT".getBytes(), new byte[6], alignment);

        final AlignedSequence realigned = new SequenceView(new MockRefGenome()).realignSequenceToLegend(aligned, legend);

        assertThat(realigned).isNotNull();
        assertThat(realigned.getAlignmentBlocks()).hasSize(1);
        assertThat(realigned.getAlignmentBlocks().get(0).Chromosome).isEqualTo("2");
        assertThat(realigned.getAlignmentBlocks().get(0).ReferenceStartPosition).isEqualTo(103);
        assertThat(realigned.getAlignmentBlocks().get(0).SequenceStartPosition).isEqualTo(1);
        assertThat(realigned.getAlignmentBlocks().get(0).Length).isEqualTo(6);
        assertThat(realigned.getAlignmentBlocks().get(0).Inverted).isEqualTo(false);
    }

    @Test
    public void canRealignForInsertInLegend()
    {
        final List<Alignment> legend = List.of(
                new Alignment("2", 100, 0, 6, false, 60),
                new Alignment("*", 0, 0, 3, false, 60),
                new Alignment("2", 106, 0, 6, false, 60)
        );

        final List<Alignment> alignment = List.of(
                new Alignment("2", 103, 1, 6, false, 60)
        );
        final AlignedSequence aligned = new BasicAlignedSequence("Test", "AAATTT".getBytes(), new byte[6], alignment);

        final AlignedSequence realigned = new SequenceView(new MockRefGenome()).realignSequenceToLegend(aligned, legend);

        assertThat(realigned).isNotNull();
        assertThat(realigned.getAlignmentBlocks()).hasSize(3);
        assertThat(realigned.getAlignmentBlocks().get(0).Chromosome).isEqualTo("2");
        assertThat(realigned.getAlignmentBlocks().get(0).ReferenceStartPosition).isEqualTo(103);
        assertThat(realigned.getAlignmentBlocks().get(0).SequenceStartPosition).isEqualTo(1);
        assertThat(realigned.getAlignmentBlocks().get(0).Length).isEqualTo(3);
        assertThat(realigned.getAlignmentBlocks().get(0).Inverted).isEqualTo(false);

        assertThat(realigned.getAlignmentBlocks().get(1).Chromosome).isEqualTo("*");
        assertThat(realigned.getAlignmentBlocks().get(1).ReferenceStartPosition).isEqualTo(0);
        assertThat(realigned.getAlignmentBlocks().get(1).SequenceStartPosition).isEqualTo(0);
        assertThat(realigned.getAlignmentBlocks().get(1).Length).isEqualTo(3);
        assertThat(realigned.getAlignmentBlocks().get(1).Inverted).isEqualTo(false);

        assertThat(realigned.getAlignmentBlocks().get(2).Chromosome).isEqualTo("2");
        assertThat(realigned.getAlignmentBlocks().get(2).ReferenceStartPosition).isEqualTo(107);
        assertThat(realigned.getAlignmentBlocks().get(2).SequenceStartPosition).isEqualTo(4);
        assertThat(realigned.getAlignmentBlocks().get(2).Length).isEqualTo(3);
        assertThat(realigned.getAlignmentBlocks().get(2).Inverted).isEqualTo(false);
    }

    @Test
    public void canRealignLeftSoftClipNoBoundary()
    {
        final List<Alignment> legend = List.of(
                new Alignment("2", 100, 0, 12, false, 60)
        );

        final List<Alignment> alignment = List.of(
                new Alignment("?", 0, 1, 3, false, 60),
                new Alignment("2", 106, 4, 3, false, 60)
        );
        final AlignedSequence aligned = new BasicAlignedSequence("Test", "AAATTT".getBytes(), new byte[6], alignment);

        final AlignedSequence realigned = new SequenceView(new MockRefGenome()).realignSequenceToLegend(aligned, legend);

        assertThat(realigned).isNotNull();
        assertThat(realigned.getAlignmentBlocks()).hasSize(1);
        assertThat(realigned.getAlignmentBlocks().get(0).Chromosome).isEqualTo("2");
        assertThat(realigned.getAlignmentBlocks().get(0).ReferenceStartPosition).isEqualTo(103);
        assertThat(realigned.getAlignmentBlocks().get(0).SequenceStartPosition).isEqualTo(1);
        assertThat(realigned.getAlignmentBlocks().get(0).Length).isEqualTo(6);
        assertThat(realigned.getAlignmentBlocks().get(0).Inverted).isEqualTo(false);
    }

    @Test
    public void canRealignRightSoftClipNoBoundary()
    {
        final List<Alignment> legend = List.of(
                new Alignment("2", 100, 0, 12, false, 60)
        );

        final List<Alignment> alignment = List.of(
                new Alignment("2", 103, 1, 3, false, 60),
                new Alignment("?", 0, 4, 3, false, 60)
        );
        final AlignedSequence aligned = new BasicAlignedSequence("Test", "AAATTT".getBytes(), new byte[6], alignment);

        final AlignedSequence realigned = new SequenceView(new MockRefGenome()).realignSequenceToLegend(aligned, legend);

        assertThat(realigned).isNotNull();
        assertThat(realigned.getAlignmentBlocks()).hasSize(1);
        assertThat(realigned.getAlignmentBlocks().get(0).Chromosome).isEqualTo("2");
        assertThat(realigned.getAlignmentBlocks().get(0).ReferenceStartPosition).isEqualTo(103);
        assertThat(realigned.getAlignmentBlocks().get(0).SequenceStartPosition).isEqualTo(1);
        assertThat(realigned.getAlignmentBlocks().get(0).Length).isEqualTo(6);
        assertThat(realigned.getAlignmentBlocks().get(0).Inverted).isEqualTo(false);
    }
}