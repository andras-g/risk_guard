-- Story 8.3: Add vtszMappings array to existing version-1 EPR config
-- Each entry maps a VTSZ prefix to a KF code for invoice-driven EPR auto-fill.
-- Prefix matching: longest prefix wins. All VTSZ codes present in DemoInvoiceFixtures are covered.
UPDATE epr_configs
SET config_data = config_data || '{
  "vtszMappings": [
    {"vtszPrefix": "4819", "kfCode": "11010101", "materialName_hu": "Karton csomagolás", "materialName_en": "Cardboard packaging"},
    {"vtszPrefix": "4820", "kfCode": "11010101", "materialName_hu": "Papír csomagolás", "materialName_en": "Paper packaging"},
    {"vtszPrefix": "4818", "kfCode": "11010101", "materialName_hu": "Papír háztartási csomagolás", "materialName_en": "Household paper packaging"},
    {"vtszPrefix": "3923", "kfCode": "11020101", "materialName_hu": "PET csomagolás", "materialName_en": "PET packaging"},
    {"vtszPrefix": "3917", "kfCode": "11020101", "materialName_hu": "Műanyag cső csomagolás", "materialName_en": "Plastic pipe packaging"},
    {"vtszPrefix": "3920", "kfCode": "11020101", "materialName_hu": "Műanyag lap csomagolás", "materialName_en": "Plastic sheet packaging"},
    {"vtszPrefix": "3926", "kfCode": "11020101", "materialName_hu": "Egyéb műanyag csomagolás", "materialName_en": "Other plastic packaging"},
    {"vtszPrefix": "7214", "kfCode": "91010101", "materialName_hu": "Acél rúd/profil", "materialName_en": "Steel bar/profile"},
    {"vtszPrefix": "7210", "kfCode": "91010101", "materialName_hu": "Acél lap", "materialName_en": "Steel sheet"},
    {"vtszPrefix": "7604", "kfCode": "11040101", "materialName_hu": "Alumínium profil csomagolás", "materialName_en": "Aluminium profile packaging"},
    {"vtszPrefix": "7610", "kfCode": "11040101", "materialName_hu": "Alumínium építési elem", "materialName_en": "Aluminium construction element"},
    {"vtszPrefix": "2523", "kfCode": "91010101", "materialName_hu": "Cement", "materialName_en": "Cement"},
    {"vtszPrefix": "2521", "kfCode": "91010101", "materialName_hu": "Mész", "materialName_en": "Lime"},
    {"vtszPrefix": "1905", "kfCode": "61010101", "materialName_hu": "Pékárú csomagolás", "materialName_en": "Bakery product packaging"},
    {"vtszPrefix": "1901", "kfCode": "61010101", "materialName_hu": "Élelmiszerkészítmény csomagolás", "materialName_en": "Food preparation packaging"},
    {"vtszPrefix": "1101", "kfCode": "61010101", "materialName_hu": "Liszt csomagolás", "materialName_en": "Flour packaging"},
    {"vtszPrefix": "1102", "kfCode": "61010101", "materialName_hu": "Gabona liszt csomagolás", "materialName_en": "Grain flour packaging"},
    {"vtszPrefix": "8523", "kfCode": "23030101", "materialName_hu": "Szoftver/adathordozó csomagolás", "materialName_en": "Software/media packaging"},
    {"vtszPrefix": "6202", "kfCode": "61010101", "materialName_hu": "Textilárú csomagolás", "materialName_en": "Textile product packaging"},
    {"vtszPrefix": "6301", "kfCode": "61010101", "materialName_hu": "Takaró textil csomagolás", "materialName_en": "Blanket textile packaging"},
    {"vtszPrefix": "6311", "kfCode": "61010101", "materialName_hu": "Használt textil", "materialName_en": "Used textile"},
    {"vtszPrefix": "7318", "kfCode": "91010101", "materialName_hu": "Acél csavar/kötőelem", "materialName_en": "Steel screw/fastener"},
    {"vtszPrefix": "4821", "kfCode": "11010101", "materialName_hu": "Papír címke", "materialName_en": "Paper label"}
  ]
}'::jsonb
WHERE version = 1;
