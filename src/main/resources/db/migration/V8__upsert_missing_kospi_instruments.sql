MERGE INTO instruments t
USING (
    SELECT
        'KOSPI' AS market_type,
        'STOCK' AS instrument_type,
        'KRX' AS exchange_code,
        '000080' AS stock_code,
        'KR7000080002' AS standard_code,
        '하이트진로' AS stock_name,
        CAST(NULL AS VARCHAR2(200)) AS stock_name_en,
        'KRW' AS currency_code,
        'N' AS etf_yn,
        'N' AS nxt_yn,
        'Y' AS active_yn
    FROM dual
) s
ON (t.market_type = s.market_type AND t.stock_code = s.stock_code)
WHEN MATCHED THEN UPDATE SET
    t.instrument_type = s.instrument_type,
    t.exchange_code = s.exchange_code,
    t.standard_code = s.standard_code,
    t.stock_name = s.stock_name,
    t.stock_name_en = s.stock_name_en,
    t.currency_code = s.currency_code,
    t.etf_yn = s.etf_yn,
    t.nxt_yn = s.nxt_yn,
    t.active_yn = s.active_yn,
    t.updated_at = SYSTIMESTAMP
WHEN NOT MATCHED THEN INSERT (
    market_type,
    instrument_type,
    exchange_code,
    stock_code,
    standard_code,
    stock_name,
    stock_name_en,
    currency_code,
    etf_yn,
    nxt_yn,
    active_yn,
    created_at,
    updated_at
) VALUES (
    s.market_type,
    s.instrument_type,
    s.exchange_code,
    s.stock_code,
    s.standard_code,
    s.stock_name,
    s.stock_name_en,
    s.currency_code,
    s.etf_yn,
    s.nxt_yn,
    s.active_yn,
    SYSTIMESTAMP,
    SYSTIMESTAMP
);

MERGE INTO instruments t
USING (
    SELECT
        'KOSPI' AS market_type,
        'STOCK' AS instrument_type,
        'KRX' AS exchange_code,
        '0126Z0' AS stock_code,
        CAST(NULL AS VARCHAR2(20)) AS standard_code,
        '삼성에피스홀딩스' AS stock_name,
        CAST(NULL AS VARCHAR2(200)) AS stock_name_en,
        'KRW' AS currency_code,
        'N' AS etf_yn,
        'N' AS nxt_yn,
        'Y' AS active_yn
    FROM dual
) s
ON (t.market_type = s.market_type AND t.stock_code = s.stock_code)
WHEN MATCHED THEN UPDATE SET
    t.instrument_type = s.instrument_type,
    t.exchange_code = s.exchange_code,
    t.standard_code = s.standard_code,
    t.stock_name = s.stock_name,
    t.stock_name_en = s.stock_name_en,
    t.currency_code = s.currency_code,
    t.etf_yn = s.etf_yn,
    t.nxt_yn = s.nxt_yn,
    t.active_yn = s.active_yn,
    t.updated_at = SYSTIMESTAMP
WHEN NOT MATCHED THEN INSERT (
    market_type,
    instrument_type,
    exchange_code,
    stock_code,
    standard_code,
    stock_name,
    stock_name_en,
    currency_code,
    etf_yn,
    nxt_yn,
    active_yn,
    created_at,
    updated_at
) VALUES (
    s.market_type,
    s.instrument_type,
    s.exchange_code,
    s.stock_code,
    s.standard_code,
    s.stock_name,
    s.stock_name_en,
    s.currency_code,
    s.etf_yn,
    s.nxt_yn,
    s.active_yn,
    SYSTIMESTAMP,
    SYSTIMESTAMP
);
