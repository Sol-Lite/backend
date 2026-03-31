package com.sollite.market.init;

import com.sollite.foreignmarket.dto.ForeignStockInfoResponse;
import com.sollite.foreignmarket.service.ForeignStockMarketService;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.market.dto.FinanceResponse;
import com.sollite.market.service.MarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.market.cap", name = "enabled", havingValue = "true")
public class MarketCapInitializer implements ApplicationRunner {

    private final InstrumentRepository instrumentRepository;
    private final MarketService marketService;
    private final ForeignStockMarketService foreignStockMarketService;

    @Value("${app.market.cap.domestic.skip:false}")
    private boolean skipDomestic;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!skipDomestic) {
            runDomestic();
        } else {
            log.info("[MarketCap] 국내주식 시가총액 초기화 스킵 (app.market.cap.domestic.skip=true)");
        }
        runForeign();
    }

    private void runDomestic() throws InterruptedException {
        List<String> stockCodes = instrumentRepository.findActiveDomesticNonEtfStockCodes();
        log.info("[MarketCap] 국내주식 시가총액 초기화 시작: 총 {} 종목", stockCodes.size());

        int updated = 0;
        for (String stockCode : stockCodes) {
            try {
                FinanceResponse finance = marketService.getFinance(stockCode);
                String raw = finance.marketCap();
                if (raw != null && !raw.isBlank()) {
                    long cap = Long.parseLong(raw.trim());
                    saveDomesticMarketCap(stockCode, cap);
                    updated++;
                    log.info("[MarketCap] 국내 {}/{} 완료: stockCode={}, marketCap={}억", updated, stockCodes.size(), stockCode, cap);
                }
                Thread.sleep(1100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("[MarketCap] 국내 조회 실패: stockCode={}, reason={}", stockCode, e.getMessage());
            }
        }

        log.info("[MarketCap] 국내주식 완료: {}/{} 종목 업데이트", updated, stockCodes.size());
    }

    private void runForeign() throws InterruptedException {
        List<InstrumentRepository.StockCodeExchangeCodeView> pairs =
                instrumentRepository.findActiveForeignNonEtfStockCodeExchangePairs();
        log.info("[MarketCap] 해외주식 시가총액 초기화 시작: 총 {} 종목", pairs.size());

        int updated = 0;
        for (InstrumentRepository.StockCodeExchangeCodeView pair : pairs) {
            String stockCode = pair.getStockCode();
            String exchangeCode = pair.getExchangeCode();
            try {
                ForeignStockInfoResponse info = foreignStockMarketService.getInfo(stockCode, exchangeCode);
                long shareprc = info.shareprc();
                double exrate = info.exrate();

                if (shareprc <= 0 || exrate <= 0) {
                    log.warn("[MarketCap] 해외 데이터 없음: stockCode={}, shareprc={}, exrate={}", stockCode, shareprc, exrate);
                    continue;
                }

                // USD → KRW → 억원
                long capUkrw = BigDecimal.valueOf(shareprc)
                        .multiply(BigDecimal.valueOf(exrate))
                        .divide(BigDecimal.valueOf(100_000_000L), 0, RoundingMode.HALF_UP)
                        .longValue();

                saveForeignMarketCap(stockCode, capUkrw);
                updated++;
                log.info("[MarketCap] 해외 {}/{} 완료: stockCode={}, shareprc={}USD, marketCap={}억", updated, pairs.size(), stockCode, shareprc, capUkrw);

                Thread.sleep(1100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("[MarketCap] 해외 조회 실패: stockCode={}, exchangeCode={}, reason={}", stockCode, exchangeCode, e.getMessage());
            }
        }

        log.info("[MarketCap] 해외주식 완료: {}/{} 종목 업데이트", updated, pairs.size());
    }

    @Transactional
    public void saveDomesticMarketCap(String stockCode, Long cap) {
        instrumentRepository.updateMarketCapByStockCode(stockCode, cap);
    }

    @Transactional
    public void saveForeignMarketCap(String stockCode, Long cap) {
        instrumentRepository.updateMarketCapByForeignStockCode(stockCode, cap);
    }
}
