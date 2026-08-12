-- Nepal master data seed (PostgreSQL)
-- Structure: Nepal -> 7 Provinces -> 77 Districts (Constitution of Nepal 2015, Schedule 4).
-- Data compiled 2026-08-12 from Wikipedia "List of districts of Nepal"
--   (https://en.wikipedia.org/wiki/List_of_districts_of_Nepal) — areas/populations are the 2021 CBS census figures as presented there.

CREATE TABLE IF NOT EXISTS master_country (
  id      SERIAL PRIMARY KEY,
  code    VARCHAR(2)  NOT NULL UNIQUE,
  iso3    VARCHAR(3)  NOT NULL,
  name_en VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS master_province (
  id              SERIAL PRIMARY KEY,
  code            VARCHAR(8)  NOT NULL UNIQUE,
  country_code    VARCHAR(2)  NOT NULL REFERENCES master_country(code),
  name_en         VARCHAR(100) NOT NULL,
  name_np         VARCHAR(100),
  capital_en      VARCHAR(100),
  capital_np      VARCHAR(100),
  area_km2        NUMERIC(12,2),
  population_2021 BIGINT
);

CREATE TABLE IF NOT EXISTS master_district (
  id              SERIAL PRIMARY KEY,
  code            VARCHAR(20) NOT NULL UNIQUE,
  province_id     INTEGER NOT NULL REFERENCES master_province(id),
  name_en         VARCHAR(100) NOT NULL,
  name_np         VARCHAR(100),
  headquarters    VARCHAR(100),
  area_km2        NUMERIC(12,2),
  population_2021 BIGINT
);

INSERT INTO master_country (code, iso3, name_en) VALUES ('NP', 'NPL', 'Nepal') ON CONFLICT (code) DO NOTHING;

INSERT INTO master_province (code, country_code, name_en, name_np, capital_en, capital_np, area_km2, population_2021) VALUES ('NP-P1', 'NP', 'Koshi Province', 'कोशी प्रदेश', 'Biratnagar', 'विराटनगर', 25905, 4961412) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_province (code, country_code, name_en, name_np, capital_en, capital_np, area_km2, population_2021) VALUES ('NP-P2', 'NP', 'Madhesh Province', 'मधेश प्रदेश', 'Janakpur', 'जनकपुर', 9661, 6114600) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_province (code, country_code, name_en, name_np, capital_en, capital_np, area_km2, population_2021) VALUES ('NP-P3', 'NP', 'Bagmati Province', 'बागमती प्रदेश', 'Hetauda', 'हेटौडा', 20300, 6116866) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_province (code, country_code, name_en, name_np, capital_en, capital_np, area_km2, population_2021) VALUES ('NP-P4', 'NP', 'Gandaki Province', 'गण्डकी प्रदेश', 'Pokhara', 'पोखरा', 21504, 2466427) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_province (code, country_code, name_en, name_np, capital_en, capital_np, area_km2, population_2021) VALUES ('NP-P5', 'NP', 'Lumbini Province', 'लुम्बिनी प्रदेश', 'Deukhuri', 'देउखुरी', 22288, 5122078) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_province (code, country_code, name_en, name_np, capital_en, capital_np, area_km2, population_2021) VALUES ('NP-P6', 'NP', 'Karnali Province', 'कर्णाली प्रदेश', 'Birendranagar', 'वीरेन्द्रनगर', 27984, 1688412) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_province (code, country_code, name_en, name_np, capital_en, capital_np, area_km2, population_2021) VALUES ('NP-P7', 'NP', 'Sudurpashchim Province', 'सुदूरपश्चिम प्रदेश', 'Godawari', 'गोदावरी', 19999.28, 2694783) ON CONFLICT (code) DO NOTHING;

INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('BHOJPUR', 1, 'Bhojpur', 'भोजपुर', 'Bhojpur', 1507, 157923) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DHANKUTA', 1, 'Dhankuta', 'धनकुटा', 'Dhankuta', 891, 150599) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('ILAM', 1, 'Ilam', 'इलाम', 'Ilam', 1703, 279534) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('JHAPA', 1, 'Jhapa', 'झापा', 'Chandragadhi (Bhadrapur)', 1606, 998054) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('KHOTANG', 1, 'Khotang', 'खोटाङ', 'Diktel', 1591, 175298) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('MORANG', 1, 'Morang', 'मोरङ', 'Biratnagar', 1855, 1148156) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('OKHALDHUNGA', 1, 'Okhaldhunga', 'ओखलढुङ्गा', 'Okhaldhunga (Siddhicharan)', 1074, 139552) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('PANCHTHAR', 1, 'Panchthar', 'पाँचथर', 'Phidim', 1241, 172400) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SANKHUWASABHA', 1, 'Sankhuwasabha', 'सङ्खुवासभा', 'Khandbari', 3480, 158041) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SOLUKHUMBU', 1, 'Solukhumbu', 'सोलुखुम्बु', 'Salleri (Solududhkunda)', 3312, 104851) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SUNSARI', 1, 'Sunsari', 'सुनसरी', 'Inaruwa', 1257, 926962) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('TAPLEJUNG', 1, 'Taplejung', 'ताप्लेजुङ', 'Phungling', 3646, 120590) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('TEHRATHUM', 1, 'Tehrathum', 'तेह्रथुम', 'Myanglung', 679, 88731) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('UDAYAPUR', 1, 'Udayapur', 'उदयपुर', 'Gaighat (Triyuga)', 2063, 340721) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('PARSA', 2, 'Parsa', 'पर्सा', 'Birgunj', 1353, 654471) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('BARA', 2, 'Bara', 'बारा', 'Kalaiya', 1190, 763137) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('RAUTAHAT', 2, 'Rautahat', 'रौतहट', 'Gaur', 1126, 813573) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SARLAHI', 2, 'Sarlahi', 'सर्लाही', 'Malangwa', 1259, 862470) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DHANUSHA', 2, 'Dhanusha', 'धनुषा', 'Janakpur', 1180, 867747) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SIRAHA', 2, 'Siraha', 'सिराहा', 'Siraha', 1188, 739953) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('MAHOTTARI', 2, 'Mahottari', 'महोत्तरी', 'Jaleshwar', 1002, 706994) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SAPTARI', 2, 'Saptari', 'सप्तरी', 'Rajbiraj', 1363, 706255) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SINDHULI', 3, 'Sindhuli', 'सिन्धुली', 'Sindhulimadhi (Kamalamai)', 2491, 300026) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('RAMECHHAP', 3, 'Ramechhap', 'रामेछाप', 'Manthali', 1546, 170302) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DOLAKHA', 3, 'Dolakha', 'दोलखा', 'Charikot (Bhimeshwar)', 2191, 172767) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('BHAKTAPUR', 3, 'Bhaktapur', 'भक्तपुर', 'Bhaktapur', 119, 432132) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DHADING', 3, 'Dhading', 'धादिङ', 'Dhading Besi (Nilkantha)', 1926, 325710) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('KATHMANDU', 3, 'Kathmandu', 'काठमाडौँ', 'Kathmandu', 395, 2041587) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('KAVREPALANCHOK', 3, 'Kavrepalanchok', 'काभ्रेपलाञ्चोक', 'Dhulikhel', 1396, 364039) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('LALITPUR', 3, 'Lalitpur', 'ललितपुर', 'Lalitpur', 385, 551667) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('NUWAKOT', 3, 'Nuwakot', 'नुवाकोट', 'Bidur', 1121, 263391) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('RASUWA', 3, 'Rasuwa', 'रसुवा', 'Dhunche', 1544, 46689) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SINDHUPALCHOK', 3, 'Sindhupalchok', 'सिन्धुपाल्चोक', 'Chautara', 2542, 262624) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('CHITWAN', 3, 'Chitwan', 'चितवन', 'Bharatpur', 2218, 719859) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('MAKWANPUR', 3, 'Makwanpur', 'मकवानपुर', 'Hetauda', 2426, 466073) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('BAGLUNG', 4, 'Baglung', 'बागलुङ', 'Baglung', 1784, 249211) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('GORKHA', 4, 'Gorkha', 'गोरखा', 'Gorkha', 3610, 251027) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('KASKI', 4, 'Kaski', 'कास्की', 'Pokhara', 2017, 600051) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('LAMJUNG', 4, 'Lamjung', 'लमजुङ', 'Besisahar', 1692, 155852) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('MANANG', 4, 'Manang', 'मनाङ', 'Chame', 2246, 5658) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('MUSTANG', 4, 'Mustang', 'मुस्ताङ', 'Jomsom', 3573, 14452) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('MYAGDI', 4, 'Myagdi', 'म्याग्दी', 'Beni', 2297, 107033) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('NAWALPUR', 4, 'Nawalpur', 'नवलपुर', 'Kawasoti', 1370, 378079) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('PARBAT', 4, 'Parbat', 'पर्वत', 'Kusma', 494, 130887) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SYANGJA', 4, 'Syangja', 'स्याङ्गजा', 'Putalibazar', 1164, 253024) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('TANAHUN', 4, 'Tanahun', 'तनहुँ', 'Damauli (Vyas)', 1546, 321153) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('KAPILVASTU', 5, 'Kapilvastu', 'कपिलवस्तु', 'Taulihawa', 1738, 682961) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('PARASI', 5, 'Parasi', 'परासी', 'Parasi (Ramgram)', 792, 386868) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('RUPANDEHI', 5, 'Rupandehi', 'रुपन्देही', 'Bhairahawa (Siddharthanagar)', 1360, 1121957) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('ARGHAKHANCHI', 5, 'Arghakhanchi', 'अर्घाखाँची', 'Sandhikharka', 1193, 177086) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('GULMI', 5, 'Gulmi', 'गुल्मी', 'Tamghas (Resunga)', 1149, 246494) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('PALPA', 5, 'Palpa', 'पाल्पा', 'Tansen', 1373, 245027) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DANG', 5, 'Dang', 'दाङ', 'Ghorahi', 2955, 674993) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('PYUTHAN', 5, 'Pyuthan', 'प्युठान', 'Pyuthan', 1309, 232019) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('ROLPA', 5, 'Rolpa', 'रोल्पा', 'Liwang (Rolpa)', 1879, 224506) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('RUKUM_EAST', 5, 'Eastern Rukum', 'पूर्वी रूकुम', 'Rukumkot', 1161, 56786) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('BANKE', 5, 'Banke', 'बाँके', 'Nepalgunj', 2337, 603194) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('BARDIYA', 5, 'Bardiya', 'बर्दिया', 'Gulariya', 2025, 459900) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('RUKUM_WEST', 6, 'Western Rukum', 'पश्चिमी रूकुम', 'Musikot', 1716, 166740) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SALYAN', 6, 'Salyan', 'सल्यान', 'Salyan (Sharada)', 1462, 238515) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DOLPA', 6, 'Dolpa', 'डोल्पा', 'Dunai (Thuli Bheri)', 7889, 42774) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('HUMLA', 6, 'Humla', 'हुम्ला', 'Simikot', 5655, 55394) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('JUMLA', 6, 'Jumla', 'जुम्ला', 'Jumla (Chandannath)', 2531, 118349) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('KALIKOT', 6, 'Kalikot', 'कालिकोट', 'Manma (Khandachakra)', 1741, 145292) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('MUGU', 6, 'Mugu', 'मुगु', 'Gamgadhi (Chhyanath Rara)', 3535, 64549) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('SURKHET', 6, 'Surkhet', 'सुर्खेत', 'Birendranagar', 2451, 415126) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DAILEKH', 6, 'Dailekh', 'दैलेख', 'Dailekh (Narayan)', 1502, 252313) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('JAJARKOT', 6, 'Jajarkot', 'जाजरकोट', 'Jajarkot Khalanga (Bheri)', 2230, 189360) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('KAILALI', 7, 'Kailali', 'कैलाली', 'Dhangadhi', 3235, 904666) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('ACHHAM', 7, 'Achham', 'अछाम', 'Mangalsen', 1680, 228852) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DOTI', 7, 'Doti', 'डोटी', 'Dipayal Silgadhi', 2025, 204831) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('BAJHANG', 7, 'Bajhang', 'बझाङ', 'Chainpur (Jayaprithvi)', 3422, 189085) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('BAJURA', 7, 'Bajura', 'बाजुरा', 'Martadi (Badimalika)', 2188, 138523) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('KANCHANPUR', 7, 'Kanchanpur', 'कञ्चनपुर', 'Mahendranagar (Bheemdatta)', 1610, 513757) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DADELDHURA', 7, 'Dadeldhura', 'डडेलधुरा', 'Dadeldhura (Amargadhi)', 1538, 139602) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('BAITADI', 7, 'Baitadi', 'बैतडी', 'Baitadi Khalanga (Dasharathchand)', 1519, 242157) ON CONFLICT (code) DO NOTHING;
INSERT INTO master_district (code, province_id, name_en, name_np, headquarters, area_km2, population_2021) VALUES ('DARCHULA', 7, 'Darchula', 'दार्चुला', 'Darchula Khalanga (Mahakali)', 2322, 133310) ON CONFLICT (code) DO NOTHING;
