package com.sollite.widget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.exception.BusinessException;
import com.sollite.market.dto.StockRankingItem;
import com.sollite.market.service.MarketService;
import com.sollite.widget.domain.entity.Dashboard;
import com.sollite.widget.domain.entity.WidgetLayout;
import com.sollite.widget.domain.repository.DashboardRepository;
import com.sollite.widget.dto.DashboardPageRequest;
import com.sollite.widget.dto.DashboardPageResponse;
import com.sollite.widget.dto.DashboardSaveRequest;
import com.sollite.widget.dto.PresetApplyRequest;
import com.sollite.widget.dto.WidgetLayoutRequest;
import com.sollite.widget.exception.WidgetErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int MAX_PAGES = 5;
    private static final String STOCK_CHART_TYPE = "stock-chart";
    private static final String STOCK_NEWS_TYPE = "stock-news";

    private final DashboardRepository dashboardRepository;
    private final MarketService marketService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<DashboardPageResponse> getMyDashboards(Long userId) {
        return dashboardRepository.findAllByUserIdWithWidgets(userId).stream()
                .map(DashboardPageResponse::from)
                .toList();
    }

    @Transactional
    public List<DashboardPageResponse> saveMyDashboards(Long userId, DashboardSaveRequest request) {
        List<DashboardPageRequest> pages = request.pages();

        long distinctCount = pages.stream()
                .mapToInt(DashboardPageRequest::pageOrder)
                .distinct()
                .count();
        if (distinctCount != pages.size()) {
            throw new BusinessException(WidgetErrorCode.DUPLICATE_PAGE_ORDER);
        }

        dashboardRepository.deleteAllByUserId(userId);

        List<Dashboard> toSave = IntStream.range(0, pages.size())
                .mapToObj(i -> {
                    DashboardPageRequest page = pages.get(i);
                    Dashboard dashboard = Dashboard.builder()
                            .userId(userId)
                            .dashboardName(page.name())
                            .pageOrder(page.pageOrder())
                            .defaultYn(i == 0 ? "Y" : "N")
                            .build();

                    page.widgets().forEach(w -> dashboard.addWidgetLayout(
                            WidgetLayout.builder()
                                    .dashboard(dashboard)
                                    .widgetType(w.widgetType())
                                    .positionX(w.positionX())
                                    .positionY(w.positionY())
                                    .width(w.width())
                                    .height(w.height())
                                    .configJson(w.configJson())
                                    .build()
                    ));
                    return dashboard;
                })
                .toList();

        return dashboardRepository.saveAll(toSave).stream()
                .map(DashboardPageResponse::from)
                .toList();
    }

    @Transactional
    public List<DashboardPageResponse> applyPreset(Long userId, PresetApplyRequest request) {
        // getThemeRanking은 @Cacheable 처리되어 있어 캐시 히트 시 즉시 반환됨
        List<StockRankingItem> ranking = marketService.getThemeRanking(request.theme(), "trading-value");
        StockRankingItem topMarketCapStock = marketService.getTopMarketCapStock(request.theme());

        List<Dashboard> existing = dashboardRepository.findAllByUserIdWithWidgets(userId);
        if (existing.size() >= MAX_PAGES) {
            throw new BusinessException(WidgetErrorCode.PAGE_LIMIT_EXCEEDED);
        }

        int[] stockIdx = {0};
        List<WidgetLayoutRequest> filledWidgets = request.widgets().stream()
                .map(w -> {
                    if (STOCK_NEWS_TYPE.equals(w.widgetType()) && topMarketCapStock != null) {
                        return new WidgetLayoutRequest(
                                w.widgetType(), w.positionX(), w.positionY(),
                                w.width(), w.height(),
                                buildStockConfigJson(w.configJson(), topMarketCapStock)
                        );
                    }
                    if (!STOCK_CHART_TYPE.equals(w.widgetType())) return w;
                    int idx = stockIdx[0]++;
                    if (idx >= ranking.size()) return w;
                    return new WidgetLayoutRequest(
                            w.widgetType(), w.positionX(), w.positionY(),
                            w.width(), w.height(),
                            buildStockConfigJson(w.configJson(), ranking.get(idx))
                    );
                })
                .toList();

        int newPageOrder = existing.size() + 1;
        Dashboard preset = Dashboard.builder()
                .userId(userId)
                .dashboardName(request.presetName())
                .pageOrder(newPageOrder)
                .defaultYn(existing.isEmpty() ? "Y" : "N")
                .build();

        filledWidgets.forEach(w -> preset.addWidgetLayout(
                WidgetLayout.builder()
                        .dashboard(preset)
                        .widgetType(w.widgetType())
                        .positionX(w.positionX())
                        .positionY(w.positionY())
                        .width(w.width())
                        .height(w.height())
                        .configJson(w.configJson())
                        .build()
        ));

        dashboardRepository.save(preset);

        return dashboardRepository.findAllByUserIdWithWidgets(userId).stream()
                .map(DashboardPageResponse::from)
                .toList();
    }

    private String buildStockConfigJson(String existingConfigJson, StockRankingItem stock) {
        try {
            Map<String, Object> config = (existingConfigJson != null && !existingConfigJson.isBlank())
                    ? objectMapper.readValue(existingConfigJson, new TypeReference<>() {})
                    : new LinkedHashMap<>();
            config.put("stockCode", stock.stockCode());
            config.put("stockName", stock.name());
            config.put("marketType", stock.marketType());
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.warn("configJson 파싱 실패, 기본 구조로 대체합니다: {}", e.getMessage());
            try {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("stockCode", stock.stockCode());
                fallback.put("stockName", stock.name());
                fallback.put("marketType", stock.marketType());
                return objectMapper.writeValueAsString(fallback);
            } catch (JsonProcessingException ex) {
                log.error("configJson 직렬화 실패: {}", ex.getMessage());
                return "{}";
            }
        }
    }
}
