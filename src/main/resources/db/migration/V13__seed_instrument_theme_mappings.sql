-- 종목-테마 매핑 seed 데이터
-- instrument_id는 instruments 테이블의 stock_code + market_type(KOSPI/KOSDAQ)으로 조회

-- ============================================================
-- 반도체 (SEMICONDUCTOR) - 19종목
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '005930' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '000660' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '000990' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '058470' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '042700' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '039030' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '095610' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '036930' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '240810' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '403870' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '140860' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '357780' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '005290' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '064760' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '131970' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '095340' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '348210' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '319660' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SEMICONDUCTOR' FROM instruments WHERE stock_code = '183300' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- 2차전지/배터리 (BATTERY) - 15종목
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '373220' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '006400' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '096770' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '247540' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '086520' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '003670' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '066970' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '361610' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '278280' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '336370' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '137400' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '348370' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '393890' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '005070' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'BATTERY' FROM instruments WHERE stock_code = '078600' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- 자동차/전장 (AUTOMOTIVE) - 12종목
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '005380' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '000270' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '012330' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '204320' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '018880' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '011210' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '005850' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '009150' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '011070' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '307950' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '396270' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'AUTOMOTIVE' FROM instruments WHERE stock_code = '118990' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- IT 플랫폼 (IT_PLATFORM) - 14종목
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '035420' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '035720' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '259960' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '036570' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '251270' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '293490' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '323410' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '377300' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '112040' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '263750' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '078340' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '194480' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '352820' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'IT_PLATFORM' FROM instruments WHERE stock_code = '058970' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- 에너지/화학 (ENERGY_CHEMICAL) - 15종목
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '051910' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '011170' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '009830' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '096770' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '011780' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '006650' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '456040' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '000880' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '010950' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '078930' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '018670' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '017940' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '298000' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '298050' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ENERGY_CHEMICAL' FROM instruments WHERE stock_code = '069260' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- 금융 (FINANCE) - 15종목 (카카오뱅크는 IT_PLATFORM과 중복 매핑)
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '105560' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '055550' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '086790' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '316140' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '032830' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '000810' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '088350' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '005830' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '138040' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '006800' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '071050' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '039490' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '005940' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '016360' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'FINANCE' FROM instruments WHERE stock_code = '323410' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- 조선 (SHIPBUILDING) - 7종목
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SHIPBUILDING' FROM instruments WHERE stock_code = '009540' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SHIPBUILDING' FROM instruments WHERE stock_code = '329180' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SHIPBUILDING' FROM instruments WHERE stock_code = '010140' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SHIPBUILDING' FROM instruments WHERE stock_code = '042660' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SHIPBUILDING' FROM instruments WHERE stock_code = '017960' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SHIPBUILDING' FROM instruments WHERE stock_code = '033500' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'SHIPBUILDING' FROM instruments WHERE stock_code = '075580' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- 방산 (DEFENSE) - 9종목
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '012450' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '079550' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '047810' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '064350' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '103140' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '272210' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '214430' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '065450' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '014940' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- 제조업 (MANUFACTURING) - 16종목
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '005490' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '004020' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '034020' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '241560' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '010120' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '006260' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '004800' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '015760' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '051600' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '000150' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '002380' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '028260' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '000720' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '047040' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '006360' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'MANUFACTURING' FROM instruments WHERE stock_code = '294870' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- 로봇 (ROBOT) - 9종목
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ROBOT' FROM instruments WHERE stock_code = '454910' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ROBOT' FROM instruments WHERE stock_code = '277810' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ROBOT' FROM instruments WHERE stock_code = '090360' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ROBOT' FROM instruments WHERE stock_code = '348340' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ROBOT' FROM instruments WHERE stock_code = '108490' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ROBOT' FROM instruments WHERE stock_code = '389500' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ROBOT' FROM instruments WHERE stock_code = '160190' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ROBOT' FROM instruments WHERE stock_code = '056080' AND market_type IN ('KOSPI','KOSDAQ');
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'ROBOT' FROM instruments WHERE stock_code = '004380' AND market_type IN ('KOSPI','KOSDAQ');

-- ============================================================
-- 중복 매핑 (한 종목이 여러 테마에 속하는 경우)
-- SK이노베이션(096770): BATTERY + ENERGY_CHEMICAL (위에서 각각 INSERT됨)
-- 한화(000880): ENERGY_CHEMICAL + DEFENSE (아래 추가)
-- 카카오뱅크(323410): IT_PLATFORM + FINANCE (위에서 각각 INSERT됨)
-- ============================================================
INSERT INTO instrument_theme_mappings (instrument_id, theme_code)
SELECT instrument_id, 'DEFENSE' FROM instruments WHERE stock_code = '000880' AND market_type IN ('KOSPI','KOSDAQ');
