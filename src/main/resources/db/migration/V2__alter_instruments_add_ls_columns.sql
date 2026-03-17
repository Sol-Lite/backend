-- LS증권 API 기준 instruments 테이블 컬럼 추가
--
-- t9945 (주식마스터조회) 응답 필드 매핑:
--   expcode  → standard_code : 확장코드(표준코드). 국내 예) KR7000020008 / 해외 예) NASAAPL
--   etfchk   → etf_yn        : ETF 구분. 1=ETF(Y), 0=일반(N)
--   nxt_chk  → nxt_yn        : NXT 거래소 제공 여부. 1=제공(Y), 0=미제공(N)

ALTER TABLE instruments ADD standard_code VARCHAR2(20);
ALTER TABLE instruments ADD etf_yn        CHAR(1) DEFAULT 'N' NOT NULL;
ALTER TABLE instruments ADD nxt_yn        CHAR(1) DEFAULT 'N' NOT NULL;

CREATE UNIQUE INDEX uq_instruments_standard_code ON instruments (standard_code);
