package com.sollite.market.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentDataLoader implements ApplicationRunner {

    private static final String CSV_PATH   = "data/instruments_all.csv";
    private static final int    BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM instruments", Integer.class);
        if (count != null && count > 0) {
            log.info("[InstrumentDataLoader] instruments 테이블에 데이터가 존재합니다 ({}건). 적재를 건너뜁니다.", count);
            return;
        }

        log.info("[InstrumentDataLoader] instruments 초기 데이터 적재 시작...");
        long start = System.currentTimeMillis();

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int total = 0;
        LocalDateTime now = LocalDateTime.now();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(CSV_PATH).getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine(); // 헤더 스킵

            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", -1);
                if (cols.length < 10) continue;

                // CSV 컬럼 순서: market_type,instrument_type,exchange_code,stock_code,
                //                standard_code,stock_name,stock_name_en,currency_code,etf_yn,nxt_yn
                String marketType     = cols[0].trim();
                String instrumentType = cols[1].trim();
                String exchangeCode   = cols[2].trim();
                String stockCode      = cols[3].trim();
                String standardCode   = cols[4].trim();
                String stockName      = cols[5].trim();
                String stockNameEn    = nullIfEmpty(cols[6].trim());
                String currencyCode   = cols[7].trim();
                String etfYn          = cols[8].trim();
                String nxtYn          = cols[9].trim();

                batch.add(new Object[]{
                        marketType, instrumentType, exchangeCode,
                        stockCode, standardCode, stockName, stockNameEn,
                        currencyCode, etfYn, nxtYn,
                        "Y", now, now
                });

                if (batch.size() == BATCH_SIZE) {
                    insertBatch(batch);
                    total += batch.size();
                    batch.clear();
                }
            }

        } catch (Exception e) {
            // 부분 적재된 데이터 정리 후 재시작 시 재시도 가능하도록 롤백
            log.error("[InstrumentDataLoader] 적재 중 오류 발생. 부분 데이터를 삭제합니다. 원인: {}", e.getMessage());
            jdbcTemplate.execute("DELETE FROM instruments");
            throw new IllegalStateException("instruments 초기 데이터 적재 실패", e);
        }

        if (!batch.isEmpty()) {
            insertBatch(batch);
            total += batch.size();
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[InstrumentDataLoader] 적재 완료: {}건, {}ms", total, elapsed);
    }

    private void insertBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO instruments
                    (market_type, instrument_type, exchange_code,
                     stock_code, standard_code, stock_name, stock_name_en,
                     currency_code, etf_yn, nxt_yn,
                     active_yn, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isBlank() || value.equals("nan")) ? null : value;
    }
}
