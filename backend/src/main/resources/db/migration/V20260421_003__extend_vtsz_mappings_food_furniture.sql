-- Demo-data support: extend vtszMappings to cover the expanded Zöld Élelmiszer (food)
-- and Prémium Bútor (furniture) catalogs used by the Demo Accountant flow.
-- Only the *fallback* classifier consults this table — products with registered
-- packaging components bypass it (see InvoiceDrivenFilingAggregator).
UPDATE epr_configs
SET config_data = jsonb_set(
    config_data,
    '{vtszMappings}',
    (config_data->'vtszMappings') || '[
        {"vtszPrefix": "1006", "kfCode": "11020101", "materialName_hu": "Rizs csomagolás", "materialName_en": "Rice packaging"},
        {"vtszPrefix": "1902", "kfCode": "11020101", "materialName_hu": "Tészta csomagolás", "materialName_en": "Pasta packaging"},
        {"vtszPrefix": "0409", "kfCode": "12050201", "materialName_hu": "Méz üvegcsomagolás", "materialName_en": "Honey glass packaging"},
        {"vtszPrefix": "2007", "kfCode": "12050201", "materialName_hu": "Lekvár üvegcsomagolás", "materialName_en": "Jam glass packaging"},
        {"vtszPrefix": "1512", "kfCode": "12020101", "materialName_hu": "Növényi olaj PET csomagolás", "materialName_en": "Vegetable oil PET packaging"},
        {"vtszPrefix": "1509", "kfCode": "12050201", "materialName_hu": "Olívaolaj üvegcsomagolás", "materialName_en": "Olive oil glass packaging"},
        {"vtszPrefix": "2009", "kfCode": "12020101", "materialName_hu": "Gyümölcslé PET csomagolás", "materialName_en": "Fruit juice PET packaging"},
        {"vtszPrefix": "2201", "kfCode": "12020101", "materialName_hu": "Ásványvíz PET csomagolás", "materialName_en": "Mineral water PET packaging"},
        {"vtszPrefix": "1701", "kfCode": "11010101", "materialName_hu": "Cukor papírcsomagolás", "materialName_en": "Sugar paper packaging"},
        {"vtszPrefix": "0403", "kfCode": "11020101", "materialName_hu": "Tejtermék műanyag csomagolás", "materialName_en": "Dairy plastic packaging"},
        {"vtszPrefix": "0901", "kfCode": "11020101", "materialName_hu": "Kávé csomagolás (multilayer)", "materialName_en": "Coffee packaging (multilayer)"},
        {"vtszPrefix": "0902", "kfCode": "11010101", "materialName_hu": "Tea papírcsomagolás", "materialName_en": "Tea paper packaging"},
        {"vtszPrefix": "9401", "kfCode": "11010301", "materialName_hu": "Bútor (ülőbútor) csomagolás", "materialName_en": "Furniture (seating) packaging"},
        {"vtszPrefix": "9403", "kfCode": "11010301", "materialName_hu": "Bútor (tárolóbútor) csomagolás", "materialName_en": "Furniture (storage) packaging"},
        {"vtszPrefix": "9404", "kfCode": "11010301", "materialName_hu": "Matracok csomagolás", "materialName_en": "Mattress packaging"}
    ]'::jsonb
)
WHERE version = 1;
