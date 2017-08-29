package com.hartwig.hmftools.healthchecker.runners;

import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

public class CheckTypeTest {

    @Test
    public void getByTypeSuccess() {
        final Optional<CheckType> checkType = CheckType.getByCategory("coverage");
        assert checkType.isPresent();
        Assert.assertTrue(checkType.get() == CheckType.COVERAGE);
    }

    @Test
    public void getByTypeFailures() {
        final Optional<CheckType> checkType = CheckType.getByCategory("does not exist");
        Assert.assertFalse(checkType.isPresent());
    }
}