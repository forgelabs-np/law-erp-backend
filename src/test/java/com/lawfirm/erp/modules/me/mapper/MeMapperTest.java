package com.lawfirm.erp.modules.me.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.me.dto.MeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MeMapperTest {

    private final MeMapper meMapper = new MeMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Firm firm() {
        Firm firm = new Firm();
        firm.setId(UUID.randomUUID());
        firm.setLawFirmCode("YLAW");
        firm.setName("YLaw & Partners");
        firm.setStatus(FirmStatus.ACTIVE);
        firm.setIsTrial(false);
        return firm;
    }

    @Test
    void toFirmInfo_mapsLogoAndBrandFields() {
        Firm firm = firm();
        firm.setLogoUrl("http://host/logo.png");
        firm.setLogoAllowed(true);
        firm.setBrandPrimaryHex("#1a237e");
        firm.setBrandSecondaryHex("#e3f2fd");

        MeResponse.FirmInfo info = meMapper.toFirmInfo(firm);

        assertEquals("http://host/logo.png", info.getLogoUrl());
        assertTrue(info.isLogoAllowed());
        assertEquals("#1a237e", info.getBrandPrimaryHex());
        assertEquals("#e3f2fd", info.getBrandSecondaryHex());
        assertEquals(Boolean.TRUE, info.getIsPersonalColor());
    }

    @Test
    @DisplayName("One color set is enough to call the firm personalized")
    void toFirmInfo_oneColorIsEnough() {
        Firm firm = firm();
        firm.setBrandSecondaryHex("#e3f2fd");

        assertTrue(meMapper.toFirmInfo(firm).getIsPersonalColor());
    }

    @Test
    @DisplayName("No colors set leaves the flag null so the portal uses the app default")
    void toFirmInfo_noColorsLeavesFlagNull() {
        MeResponse.FirmInfo info = meMapper.toFirmInfo(firm());

        assertNull(info.getIsPersonalColor());
        assertNull(info.getBrandPrimaryHex());
        assertNull(info.getBrandSecondaryHex());
    }

    @Test
    void toFirmInfo_nullFirmIsNull() {
        assertNull(meMapper.toFirmInfo(null));
    }

    @Test
    @DisplayName("JSON: the flag is sent (and spelled isPersonalColor) only when a color is set")
    void serialization_omitsFlagWhenNotPersonal() throws Exception {
        Firm personalized = firm();
        personalized.setBrandPrimaryHex("#1a237e");
        JsonNode withColors = objectMapper.readTree(
                objectMapper.writeValueAsString(meMapper.toFirmInfo(personalized)));

        assertTrue(withColors.has("isPersonalColor"), "flag should be present when colors are set");
        assertTrue(withColors.get("isPersonalColor").asBoolean());
        assertEquals("#1a237e", withColors.get("brandPrimaryHex").asText());

        JsonNode withoutColors = objectMapper.readTree(
                objectMapper.writeValueAsString(meMapper.toFirmInfo(firm())));
        assertFalse(withoutColors.has("isPersonalColor"), "flag should be omitted when no colors are set");
        assertTrue(withoutColors.has("logoAllowed"), "logoAllowed is always present");
        assertFalse(withoutColors.get("logoAllowed").asBoolean());
    }
}
