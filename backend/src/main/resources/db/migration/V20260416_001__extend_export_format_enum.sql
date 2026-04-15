-- Story 9.4 follow-up: the `export_format_type` ENUM was created in V20260323_004 with
-- only ('CSV','XLSX'), but EprRepository.insertExport() writes the literal 'OKIRKAPU_XML'
-- for OKIRkapu XML exports (EprService:326). Without this value, any real OKIRkapu export
-- fails with `invalid input value for enum export_format_type: "OKIRKAPU_XML"`.
--
-- IF NOT EXISTS makes this safely idempotent across reruns / partial environments.
ALTER TYPE export_format_type ADD VALUE IF NOT EXISTS 'OKIRKAPU_XML';
