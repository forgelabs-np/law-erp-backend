# Master Data — Nepal

Nepal's administrative geography: **Nepal → 7 Provinces → 77 Districts** (federal structure per
Schedule 4 of the Constitution of Nepal, 2015).

## Files

| File | Contents |
|------|----------|
| `provinces.json` | The 7 provinces (ISO codes, English/Nepali names, capitals, area, 2021 population) |
| `districts.json` | All 77 districts, each mapped to its province (Nepali names, headquarters, area, 2021 population) |
| `nepal-hierarchy.json` | The full nested tree: Nepal → provinces → districts (use this to seed a `country → province → district` table structure) |
| `provinces.csv` | Same data as CSV (easy spreadsheet/import) |
| `districts.csv` | Same data as CSV |
| `seed.sql` | PostgreSQL insert script (idempotent, `ON CONFLICT DO NOTHING`) |

## Fields

- `code` (provinces) — official **ISO 3166-2** codes (`NP-P1` … `NP-P7`).
- `code` (districts) — **generated slugs** derived from the English name (uppercase, no spaces).
  These are NOT official ISO codes — Nepal has no official district ISO codes. Do not treat them as canonical; use `nameEn` for identity.
- `areaKm2` / `population2021` — from the 2021 Nepal census (CBS), as presented by the source below.
  Note: summing the 77 district-level populations gives 29,154,291 vs the province-level total of 29,164,578 —
  the official figures differ slightly between aggregation levels (census rounding); both are kept as published.
- District split note: Nawalparasi was split into **Parasi** (Lumbini, West) and **Nawalpur** (Gandaki, East);
  Rukum was split into **Western Rukum** (Karnali) and **Eastern Rukum** (Lumbini) — making 77 districts.

## Sources (where this data was fetched from)

Fetched **12 August 2026** directly from:

1. **Wikipedia — "List of districts of Nepal"**
   https://en.wikipedia.org/wiki/List_of_districts_of_Nepal
   — province→district mapping, district Nepali names, headquarters, area (km²), 2021 census population.
2. **Wikipedia — "Provinces of Nepal"**
   https://en.wikipedia.org/wiki/Provinces_of_Nepal
   — province names, ISO codes (NP-P1…NP-P7), capitals, areas, populations.

Official legal basis: **Constitution of Nepal 2015, Schedule 4** (7 provinces; 77 districts as of
20 September 2015). Population/area figures are from the **National Statistics Office (CBS), 2021 census**.

## Canonical source (important)

This folder is the **source of truth** for the data. For the backend to seed it into the database
(`master_province` / `master_district` tables, served via `GET /api/v1/master-data/*`), the JSON is
**copied** to `src/main/resources/master-data/nepal/` (it must be on the classpath). If you edit the
JSON here, re-copy it to `src/main/resources/master-data/nepal/` and call
`POST /api/v1/master-data/cache/refresh` to re-seed + clear the cache.

## Totals (verified)

| Province | ISO | Districts | Area km² | Population 2021 |
|----------|-----|-----------|----------|-----------------|
| Koshi | NP-P1 | 14 | 25,905 | 4,961,412 |
| Madhesh | NP-P2 | 8 | 9,661 | 6,114,600 |
| Bagmati | NP-P3 | 13 | 20,300 | 6,116,866 |
| Gandaki | NP-P4 | 11 | 21,504 | 2,466,427 |
| Lumbini | NP-P5 | 12 | 22,288 | 5,122,078 |
| Karnali | NP-P6 | 10 | 27,984 | 1,688,412 |
| Sudurpashchim | NP-P7 | 9 | 19,999.28 | 2,694,783 |
| **Nepal** | NP | **77** | 147,641.28 | 29,164,578 |
