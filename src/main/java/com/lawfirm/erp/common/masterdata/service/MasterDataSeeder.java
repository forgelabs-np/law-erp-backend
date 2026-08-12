package com.lawfirm.erp.common.masterdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.erp.common.masterdata.entity.Country;
import com.lawfirm.erp.common.masterdata.entity.District;
import com.lawfirm.erp.common.masterdata.entity.Province;
import com.lawfirm.erp.common.masterdata.repository.CountryRepository;
import com.lawfirm.erp.common.masterdata.repository.DistrictRepository;
import com.lawfirm.erp.common.masterdata.repository.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds Nepal master data (7 provinces, 77 districts) from classpath JSON on startup.
 * Idempotent: rows that already exist (by code) are left untouched — safe to re-run
 * manually via the cache-refresh endpoint after editing the JSON resources.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterDataSeeder implements CommandLineRunner {

    private final CountryRepository countryRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        seed();
    }

    public void seed() {
        try {
            ClassPathResource provincesResource = new ClassPathResource("master-data/nepal/provinces.json");
            ClassPathResource districtsResource = new ClassPathResource("master-data/nepal/districts.json");

            List<ProvinceSeed> provinceSeeds =
                    objectMapper.readValue(provincesResource.getInputStream(), new TypeReference<>() {});
            List<DistrictSeed> districtSeeds =
                    objectMapper.readValue(districtsResource.getInputStream(), new TypeReference<>() {});

            // Country row (the top of the hierarchy) — single row today: Nepal.
            if (countryRepository.findByCode("NP").isEmpty()) {
                Country country = new Country();
                country.setCode("NP");
                country.setIso3("NPL");
                country.setNameEn("Nepal");
                country.setNameNp("नेपाल");
                country.setActive(true);
                countryRepository.save(country);
                log.info("  + Country: Nepal (NP)");
            }

            int provincesCreated = 0;
            Map<Integer, Province> bySeedId = new HashMap<>();

            for (ProvinceSeed seed : provinceSeeds) {
                Province province = provinceRepository.findByCode(seed.code()).orElse(null);
                if (province == null) {
                    province = new Province();
                    province.setCode(seed.code());
                    province.setNameEn(seed.nameEn());
                    province.setNameNp(seed.nameNp());
                    province.setCapitalEn(seed.capitalEn());
                    province.setCapitalNp(seed.capitalNp());
                    province.setAreaKm2(seed.areaKm2());
                    province.setPopulation2021(seed.population2021());
                    province.setDisplayOrder(seed.id());
                    province.setActive(true);
                    province = provinceRepository.save(province);
                    provincesCreated++;
                    log.info("  + Province: {}", seed.code());
                }
                bySeedId.put(seed.id(), province);
            }

            int districtsCreated = 0;
            for (DistrictSeed seed : districtSeeds) {
                Province province = bySeedId.get(seed.provinceId());
                if (province == null) {
                    log.warn("Skipping district {} — unknown provinceId {}", seed.nameEn(), seed.provinceId());
                    continue;
                }
                if (districtRepository.findByCode(seed.code()).isPresent()) {
                    continue;
                }
                District district = new District();
                district.setCode(seed.code());
                district.setProvinceId(province.getId());
                district.setProvinceCode(seed.provinceCode());
                district.setNameEn(seed.nameEn());
                district.setNameNp(seed.nameNp());
                district.setHeadquarters(seed.headquarters());
                district.setAreaKm2(seed.areaKm2());
                district.setPopulation2021(seed.population2021());
                district.setActive(true);
                districtRepository.save(district);
                districtsCreated++;
            }

            log.info("MasterDataSeeder: {} province(s), {} district(s) in resources — "
                            + "created {} province(s), {} district(s) (country: Nepal)",
                    provinceSeeds.size(), districtSeeds.size(), provincesCreated, districtsCreated);
        } catch (Exception e) {
            log.error("MasterDataSeeder failed — master data may be missing or malformed", e);
        }
    }

    private record ProvinceSeed(Integer id, String code, String nameEn, String nameNp,
                                String capitalEn, String capitalNp, java.math.BigDecimal areaKm2,
                                Long population2021, Integer districtCount) {
    }

    private record DistrictSeed(Integer id, String code, String nameEn, String nameNp,
                                String headquarters, Integer provinceId, String provinceCode,
                                java.math.BigDecimal areaKm2, Long population2021) {
    }
}
