package com.hartwig.hmftools.patientdb.clinical.consents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;

import org.junit.Test;

public class ConsentConfigFactoryTest {

    private static final String INFORMED_CONSENTS_TSV = Resources.getResource("consents/informed_consents.tsv").getPath();

    @Test
    public void canReadConsentConfigFile() throws IOException {
        ConsentConfig consentConfig1 = ConsentConfigFactory.read(INFORMED_CONSENTS_TSV).get("1");

        assertEquals("1", consentConfig1.pifVersion());
        assertEquals("",consentConfig1.pif222());
        assertNull(consentConfig1.pif222Values());
        assertEquals("",consentConfig1.pif221());
        assertNull(consentConfig1.pif221Values());
        assertEquals("Ja",consentConfig1.pif26HMF());
        assertEquals(Lists.newArrayList("Ja", "Nee"), consentConfig1.pif26HMFValues());
        assertEquals("Ja",consentConfig1.pif26BUG());
        assertEquals(Lists.newArrayList("Ja", "Nee"), consentConfig1.pif26BUGValues());

        ConsentConfig consentConfig2 = ConsentConfigFactory.read(INFORMED_CONSENTS_TSV).get("2");
        assertEquals("2", consentConfig2.pifVersion());
        assertEquals("Yes",consentConfig2.pif222());
        assertEquals(Lists.newArrayList("Yes", "No"), consentConfig2.pif222Values());
        assertEquals("Yes",consentConfig2.pif221());
        assertEquals(Lists.newArrayList("Yes", "No"), consentConfig2.pif221Values());
        assertNull(consentConfig2.pif26HMF());
        assertNull(consentConfig2.pif26HMFValues());
        assertNull(consentConfig2.pif26BUG());
        assertNull(consentConfig2.pif26BUGValues());
    }
}