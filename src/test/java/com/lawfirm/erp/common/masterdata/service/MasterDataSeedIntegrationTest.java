package com.lawfirm.erp.common.masterdata.service;

import com.lawfirm.erp.common.masterdata.entity.Country;
import com.lawfirm.erp.common.masterdata.entity.District;
import com.lawfirm.erp.common.masterdata.entity.Province;
import com.lawfirm.erp.common.masterdata.repository.CountryRepository;
import com.lawfirm.erp.common.masterdata.repository.DistrictRepository;
import com.lawfirm.erp.common.masterdata.repository.ProvinceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the master data was dumped into the database: the CommandLineRunner seeder
 * runs when the context boots, so these counts must hold against the configured DB.
 */
@SpringBootTest
class MasterDataSeedIntegrationTest {

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private DistrictRepository districtRepository;

    @Test
    void masterDataIsDumpedInDatabase() {
        assertThat(countryRepository.count()).isEqualTo(1);
        assertThat(provinceRepository.count()).isEqualTo(7);
        assertThat(districtRepository.count()).isEqualTo(77);
    }

    @Test
    void seededRowsCarryProperTitles() {
        Optional<Country> nepal = countryRepository.findByCode("NP");
        assertThat(nepal).isPresent();
        assertThat(nepal.get().getNameEn()).isEqualTo("Nepal");
        assertThat(nepal.get().getNameNp()).isEqualTo("नेपाल");
        assertThat(nepal.get().getIso3()).isEqualTo("NPL");

        Optional<Province> bagmati = provinceRepository.findByCode("NP-P3");
        assertThat(bagmati).isPresent();
        assertThat(bagmati.get().getNameEn()).isEqualTo("Bagmati Province");
        assertThat(bagmati.get().getNameNp()).isEqualTo("बागमती प्रदेश");
        assertThat(bagmati.get().getCapitalEn()).isEqualTo("Hetauda");

        Optional<District> kathmandu = districtRepository.findByCode("KATHMANDU");
        assertThat(kathmandu).isPresent();
        assertThat(kathmandu.get().getNameEn()).isEqualTo("Kathmandu");
        assertThat(kathmandu.get().getNameNp()).isEqualTo("काठमाडौँ");
        assertThat(kathmandu.get().getProvinceCode()).isEqualTo("NP-P3");
    }
}
