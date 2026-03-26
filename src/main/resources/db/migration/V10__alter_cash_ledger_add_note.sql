-- cash_ledger에 메모 컬럼 추가 (환전 시 적용 환율 등 부가 정보 기록)
ALTER TABLE cash_ledger ADD note VARCHAR2(200);
