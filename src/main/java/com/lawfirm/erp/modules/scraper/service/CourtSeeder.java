package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.entity.Court;
import com.lawfirm.erp.modules.scraper.repository.CourtRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds and maintains the scraper court registry (scraper_courts).
 *
 * Source of truth: the court menu on supremecourt.gov.np (district list verified from the
 * homepage HTML on 2026-09-06). courtId is the numeric path segment of each
 * /weekly_dainik/pesi/daily/<id> link; the Nepali name is that link's exact text.
 *
 * District courts only — High Courts are served by a separate appeal/syspublic.php system
 * with a different URL scheme the scrape client cannot talk to, so they are deliberately
 * not seeded rather than guessed.
 *
 * Runs an upsert by courtId at startup: missing courts are inserted, known courts get their
 * names refreshed. It never touches courtType or the isActive kill-switch, so operator
 * choices in the registry survive every deploy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourtSeeder {

    private final CourtRepository courtRepository;

    /** One registry row: site id, name as shown on the site, English display name. */
    private record CourtRoute(Integer courtId, String nameNepali, String nameEnglish) {}

    private static final List<CourtRoute> COURT_ROUTES = List.of(
            new CourtRoute(18, "झापा जिल्ला अदालत", "Jhapa District Court"),
            new CourtRoute(19, "इलाम जिल्ला अदालत", "Ilam District Court"),
            new CourtRoute(20, "ताप्लेजुङ जिल्ला अदालत", "Taplejung District Court"),
            new CourtRoute(21, "पाँचथर जिल्ला अदालत", "Panchthar District Court"),
            new CourtRoute(22, "तेह्रथुम जिल्ला अदालत", "Terhathum District Court"),
            new CourtRoute(23, "संखुवासभा जिल्ला अदालत", "Sankhuwasabha District Court"),
            new CourtRoute(24, "भोजपुर जिल्ला अदालत", "Bhojpur District Court"),
            new CourtRoute(25, "धनकुटा जिल्ला अदालत", "Dhankuta District Court"),
            new CourtRoute(26, "सुनसरी जिल्ला अदालत", "Sunsari District Court"),
            new CourtRoute(27, "मोरङ जिल्ला अदालत", "Morang District Court"),
            new CourtRoute(28, "सोलुखुम्बु जिल्ला अदालत", "Solukhumbu District Court"),
            new CourtRoute(29, "ओखलढुंगा जिल्ला अदालत", "Okhaldhunga District Court"),
            new CourtRoute(30, "खोटाङ जिल्ला अदालत", "Khotang District Court"),
            new CourtRoute(31, "उदयपुर जिल्ला अदालत", "Udayapur District Court"),
            new CourtRoute(32, "सिराहा जिल्ला अदालत", "Siraha District Court"),
            new CourtRoute(33, "सप्तरी जिल्ला अदालत", "Saptari District Court"),
            new CourtRoute(34, "रामेछाप जिल्ला अदालत", "Ramechhap District Court"),
            new CourtRoute(35, "सिन्धुली जिल्ला अदालत", "Sindhuli District Court"),
            new CourtRoute(36, "धनुषा जिल्ला अदालत", "Dhanusha District Court"),
            new CourtRoute(37, "महोत्तरी जिल्ला अदालत", "Mahottari District Court"),
            new CourtRoute(38, "सर्लाही जिल्ला अदालत", "Sarlahi District Court"),
            new CourtRoute(39, "काठमाडौं जिल्ला अदालत", "Kathmandu District Court"),
            new CourtRoute(40, "ललितपुर जिल्ला अदालत", "Lalitpur District Court"),
            new CourtRoute(41, "भक्तपुर जिल्ला अदालत", "Bhaktapur District Court"),
            new CourtRoute(42, "दोलखा जिल्ला अदालत", "Dolakha District Court"),
            new CourtRoute(43, "सिन्धुपाल्चोक जिल्ला अदालत", "Sindhupalchok District Court"),
            new CourtRoute(44, "काभ्रेपलान्चोक जिल्ला अदालत", "Kavrepalanchok District Court"),
            new CourtRoute(45, "रसुवा जिल्ला अदालत", "Rasuwa District Court"),
            new CourtRoute(46, "धादिंङ जिल्ला अदालत", "Dhading District Court"),
            new CourtRoute(47, "नुवाकोट जिल्ला अदालत", "Nuwakot District Court"),
            new CourtRoute(48, "मकवानपुर जिल्ला अदालत", "Makwanpur District Court"),
            new CourtRoute(49, "चितवन जिल्ला अदालत", "Chitwan District Court"),
            new CourtRoute(50, "बारा जिल्ला अदालत", "Bara District Court"),
            new CourtRoute(51, "पर्सा जिल्ला अदालत", "Parsa District Court"),
            new CourtRoute(52, "रौतहट जिल्ला अदालत", "Rautahat District Court"),
            new CourtRoute(53, "मनांङ जिल्ला अदालत", "Manang District Court"),
            new CourtRoute(54, "गोरखा जिल्ला अदालत", "Gorkha District Court"),
            new CourtRoute(55, "लमजुङ जिल्ला अदालत", "Lamjung District Court"),
            new CourtRoute(56, "तनहुँ जिल्ला अदालत", "Tanahu District Court"),
            new CourtRoute(57, "कास्की जिल्ला अदालत", "Kaski District Court"),
            new CourtRoute(58, "स्याङजा जिल्ला अदालत", "Syangja District Court"),
            new CourtRoute(59, "मुस्तांङ जिल्ला अदालत", "Mustang District Court"),
            new CourtRoute(60, "म्याग्दी जिल्ला अदालत", "Myagdi District Court"),
            new CourtRoute(61, "पर्वत जिल्ला अदालत", "Parbat District Court"),
            new CourtRoute(62, "बागलुङ जिल्ला अदालत", "Baglung District Court"),
            new CourtRoute(63, "गुल्मी जिल्ला अदालत", "Gulmi District Court"),
            new CourtRoute(64, "अर्घाखाँची जिल्ला अदालत", "Arghakhanchi District Court"),
            new CourtRoute(65, "पाल्पा जिल्ला अदालत", "Palpa District Court"),
            new CourtRoute(66, "नवलपरासी जिल्ला अदालत", "Nawalparasi District Court"),
            new CourtRoute(67, "रूपन्देही जिल्ला अदालत", "Rupandehi District Court"),
            new CourtRoute(68, "कपिलवस्तु जिल्ला अदालत", "Kapilvastu District Court"),
            new CourtRoute(69, "रुकुम जिल्ला अदालत", "Rukum District Court"),
            new CourtRoute(70, "रोल्पा जिल्ला अदालत", "Rolpa District Court"),
            new CourtRoute(71, "सल्यान जिल्ला अदालत", "Salyan District Court"),
            new CourtRoute(72, "प्युठान जिल्ला अदालत", "Pyuthan District Court"),
            new CourtRoute(73, "दाङ जिल्ला अदालत", "Dang District Court"),
            new CourtRoute(74, "बाँके जिल्ला अदालत", "Banke District Court"),
            new CourtRoute(75, "बर्दिया जिल्ला अदालत", "Bardiya District Court"),
            new CourtRoute(76, "जाजरकोट जिल्ला अदालत", "Jajarkot District Court"),
            new CourtRoute(77, "दैलेख जिल्ला अदालत", "Dailekh District Court"),
            new CourtRoute(78, "सुर्खेत जिल्ला अदालत", "Surkhet District Court"),
            new CourtRoute(79, "जुम्ला जिल्ला अदालत", "Jumla District Court"),
            new CourtRoute(80, "हुम्ला जिल्ला अदालत", "Humla District Court"),
            new CourtRoute(81, "डोल्पा जिल्ला अदालत", "Dolpa District Court"),
            new CourtRoute(82, "मुगु जिल्ला अदालत", "Mugu District Court"),
            new CourtRoute(83, "कालिकोट जिल्ला अदालत", "Kalikot District Court"),
            new CourtRoute(84, "डोटी जिल्ला अदालत", "Doti District Court"),
            new CourtRoute(85, "कैलाली जिल्ला अदालत", "Kailali District Court"),
            new CourtRoute(86, "अछाम जिल्ला अदालत", "Achham District Court"),
            new CourtRoute(87, "बाजुरा जिल्ला अदालत", "Bajura District Court"),
            new CourtRoute(88, "बझाँङ जिल्ला अदालत", "Bajhang District Court"),
            new CourtRoute(89, "दार्चुला जिल्ला अदालत", "Darchula District Court"),
            new CourtRoute(90, "बैतडी जिल्ला अदालत", "Baitadi District Court"),
            new CourtRoute(91, "डडेलधुरा जिल्ला अदालत", "Dadeldhura District Court"),
            new CourtRoute(92, "कन्चनपुर जिल्ला अदालत", "Kanchanpur District Court"),
            new CourtRoute(95, "नवलपुर जिल्ला अदालत", "Nawalpur District Court"),
            new CourtRoute(96, "रुकुमकोट जिल्ला अदालत", "Rukumkot District Court")
    );

    @PostConstruct
    @Transactional
    public void seed() {
        int inserted = 0;
        int refreshed = 0;
        for (CourtRoute route : COURT_ROUTES) {
            Court court = courtRepository.findByCourtId(route.courtId()).orElse(null);
            if (court == null) {
                court = Court.builder()
                        .courtId(route.courtId())
                        .courtType("DISTRICT")
                        .isActive(true)
                        .build();
                inserted++;
            } else {
                refreshed++;
            }
            court.setCourtNameNepali(route.nameNepali());
            court.setCourtNameEnglish(route.nameEnglish());
            courtRepository.save(court);
        }
        log.info("Court registry synced: {} inserted, {} refreshed ({} known routes)",
                inserted, refreshed, COURT_ROUTES.size());
    }
}
