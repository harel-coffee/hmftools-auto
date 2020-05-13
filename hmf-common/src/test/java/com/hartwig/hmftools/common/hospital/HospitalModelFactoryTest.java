package com.hartwig.hmftools.common.hospital;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.google.common.io.Resources;

import org.junit.Test;

public class HospitalModelFactoryTest {

    private static final String LIMS_DIRECTORY = Resources.getResource("lims").getPath();

    @Test
    public void canReadFromHospitalDirectory() throws IOException {
        HospitalModel hospitalModel = HospitalModelFactory.fromHospitalDirectory(LIMS_DIRECTORY);
        assertNotNull(hospitalModel);
    }

    @Test
    public void canReadHospitalAdress() throws IOException {
        Map<String, HospitalAddress> hospitalAddress =
                HospitalModelFactory.readFromHospitalAddress(LIMS_DIRECTORY + File.separator + "hospital_address.tsv");

        assertEquals(2, hospitalAddress.size());

        HospitalAddress address1 = hospitalAddress.get("01");
        assertEquals("01", address1.hospitalId());
        assertEquals("Ext-HMF", address1.hospitalName());
        assertEquals("1000 AB", address1.hospitalZip());
        assertEquals("AMSTERDAM", address1.hospitalCity());

        HospitalAddress address2 = hospitalAddress.get("02");
        assertEquals("02", address2.hospitalId());
        assertEquals("Ext-HMF", address2.hospitalName());
        assertEquals("1000 AB", address2.hospitalZip());
        assertEquals("AMSTERDAM", address2.hospitalCity());
    }

    @Test
    public void canReadHospitalCPCT() throws IOException {
        Map<String, HospitalData> hospitalDataCPCT =
                HospitalModelFactory.readFromHospitalDataCPCT(LIMS_DIRECTORY + File.separator + "hospital_cpct.tsv");

        assertEquals(2, hospitalDataCPCT.size());

        HospitalData cpct1 = hospitalDataCPCT.get("01");
        assertEquals("01", cpct1.hospitalId());
        assertEquals("Someone", cpct1.hospitalPI());
        assertNull(cpct1.requestName());
        assertNull(cpct1.requestEmail());

        HospitalData cpct2 = hospitalDataCPCT.get("02");
        assertEquals("02", cpct2.hospitalId());
        assertEquals("Someone", cpct2.hospitalPI());
        assertNull(cpct2.requestName());
        assertNull(cpct2.requestEmail());

    }

    @Test
    public void canReadHospitalDRUP() throws IOException {
        Map<String, HospitalData> hospitalDataDRUP =
                HospitalModelFactory.readFromHospitalDataDRUP(LIMS_DIRECTORY + File.separator + "hospital_drup.tsv");
        assertEquals(2, hospitalDataDRUP.size());

        HospitalData drup1 = hospitalDataDRUP.get("01");
        assertEquals("01", drup1.hospitalId());
        assertEquals("Someone", drup1.hospitalPI());
        assertNull(drup1.requestName());
        assertNull(drup1.requestEmail());

        HospitalData drup2 = hospitalDataDRUP.get("02");
        assertEquals("02", drup2.hospitalId());
        assertEquals("Someone", drup2.hospitalPI());
        assertNull(drup2.requestName());
        assertNull(drup2.requestEmail());

    }

    @Test
    public void canReadHospitalWIDE() throws IOException {
        Map<String, HospitalData> hospitalDataWIDE =
                HospitalModelFactory.readFromHospitalDataWIDE(LIMS_DIRECTORY + File.separator + "hospital_wide.tsv");
        assertEquals(2, hospitalDataWIDE.size());

        HospitalData wide1 = hospitalDataWIDE.get("01");
        assertEquals("01", wide1.hospitalId());
        assertEquals("Someone", wide1.hospitalPI());
        assertEquals("Someone1", wide1.requestName());
        assertEquals("my@email.com", wide1.requestEmail());

        HospitalData wide2 = hospitalDataWIDE.get("02");
        assertEquals("02", wide2.hospitalId());
        assertEquals("Someone", wide2.hospitalPI());
        assertEquals("Someone1", wide2.requestName());
        assertEquals("my@email.com", wide2.requestEmail());
    }

    @Test
    public void canReadSampleHospitalMapping() throws IOException {
        Map<String, HospitalSampleMapping> sampleHospitalMapping =
                HospitalModelFactory.readFromSampleHospitalMapping(LIMS_DIRECTORY + File.separator + "sample_hospital_mapping.tsv");

        assertEquals(1, sampleHospitalMapping.size());

        HospitalSampleMapping sampleMapping = sampleHospitalMapping.get("CORE18001224T");
        assertEquals("HOSP1", sampleMapping.internalHospitalName());
    }
}
