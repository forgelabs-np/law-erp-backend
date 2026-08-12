package com.lawfirm.erp.common.masterdata.service;

import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.masterdata.dto.DistrictResponse;
import com.lawfirm.erp.common.masterdata.dto.ProvinceResponse;
import com.lawfirm.erp.common.masterdata.entity.District;
import com.lawfirm.erp.common.masterdata.entity.Province;
import com.lawfirm.erp.common.masterdata.repository.DistrictRepository;
import com.lawfirm.erp.common.masterdata.repository.ProvinceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDataServiceTest {

    @Mock private ProvinceRepository provinceRepository;
    @Mock private DistrictRepository districtRepository;
    @Mock private CacheManager cacheManager;

    private MasterDataService service;

    private UUID p1Id;
    private UUID p2Id;
    private Province p1;
    private District d1;
    private District d2;

    @BeforeEach
    void setUp() {
        service = new MasterDataService(provinceRepository, districtRepository, cacheManager);

        p1Id = UUID.randomUUID();
        p2Id = UUID.randomUUID();
        p1 = new Province();
        p1.setId(p1Id);
        p1.setCode("NP-P1");
        p1.setNameEn("Koshi Province");
        p1.setNameNp("कोशी प्रदेश");
        p1.setCapitalEn("Biratnagar");
        p1.setDisplayOrder(1);
        p1.setActive(true);

        d1 = new District();
        d1.setId(UUID.randomUUID());
        d1.setCode("JHAPA");
        d1.setNameEn("Jhapa");
        d1.setProvinceId(p1Id);
        d1.setProvinceCode("NP-P1");
        d1.setHeadquarters("Chandragadhi");
        d1.setActive(true);

        d2 = new District();
        d2.setId(UUID.randomUUID());
        d2.setCode("KATHMANDU");
        d2.setNameEn("Kathmandu");
        d2.setProvinceId(p2Id);
        d2.setProvinceCode("NP-P3");
        d2.setActive(true);
    }

    @Test
    void getAllProvinces_mapsEntitiesAndCountsDistricts() {
        Province p2 = new Province();
        p2.setId(p2Id);
        p2.setCode("NP-P3");
        p2.setNameEn("Bagmati Province");
        p2.setDisplayOrder(3);

        when(provinceRepository.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(p1, p2));
        when(districtRepository.findAll()).thenReturn(List.of(d1, d2));

        List<ProvinceResponse> result = service.getAllProvinces();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("NP-P1");
        assertThat(result.get(0).getDistrictCount()).isEqualTo(1);
        assertThat(result.get(0).getCapitalEn()).isEqualTo("Biratnagar");
        assertThat(result.get(1).getDistrictCount()).isEqualTo(1);
    }

    @Test
    void getDistrictsByProvince_mapsWithProvinceName() {
        when(provinceRepository.findById(p1Id)).thenReturn(Optional.of(p1));
        when(districtRepository.findByProvinceIdOrderByNameEnAsc(p1Id)).thenReturn(List.of(d1));

        List<DistrictResponse> result = service.getDistrictsByProvince(p1Id);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNameEn()).isEqualTo("Jhapa");
        assertThat(result.get(0).getProvinceNameEn()).isEqualTo("Koshi Province");
        assertThat(result.get(0).getProvinceCode()).isEqualTo("NP-P1");
    }

    @Test
    void getDistrictsByProvince_unknownProvince_throws() {
        when(provinceRepository.findById(p2Id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDistrictsByProvince(p2Id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllDistricts_mapsAllWithProvinceName() {
        Province p2 = new Province();
        p2.setId(p2Id);
        p2.setNameEn("Bagmati Province");

        when(provinceRepository.findAll()).thenReturn(List.of(p1, p2));
        when(districtRepository.findAllByOrderByNameEnAsc()).thenReturn(List.of(d1, d2));

        List<DistrictResponse> result = service.getAllDistricts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProvinceNameEn()).isEqualTo("Koshi Province");
        assertThat(result.get(1).getProvinceNameEn()).isEqualTo("Bagmati Province");
    }
}
