package com.sollite.widget.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.widget.domain.entity.Dashboard;
import com.sollite.widget.domain.entity.WidgetLayout;
import com.sollite.widget.domain.repository.DashboardRepository;
import com.sollite.widget.dto.DashboardPageRequest;
import com.sollite.widget.dto.DashboardPageResponse;
import com.sollite.widget.dto.DashboardSaveRequest;
import com.sollite.widget.exception.WidgetErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepository dashboardRepository;

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
}
