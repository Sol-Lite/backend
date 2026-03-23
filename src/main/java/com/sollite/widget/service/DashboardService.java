package com.sollite.widget.service;

import com.sollite.widget.domain.entity.Dashboard;
import com.sollite.widget.domain.entity.WidgetLayout;
import com.sollite.widget.domain.repository.DashboardRepository;
import com.sollite.widget.dto.DashboardPageResponse;
import com.sollite.widget.dto.DashboardSaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        dashboardRepository.deleteAllByUserId(userId);
        dashboardRepository.flush();

        AtomicInteger index = new AtomicInteger(0);
        List<Dashboard> toSave = request.pages().stream()
                .map(page -> {
                    boolean isFirst = index.getAndIncrement() == 0;
                    Dashboard dashboard = Dashboard.builder()
                            .userId(userId)
                            .dashboardName(page.name())
                            .pageOrder(page.pageOrder())
                            .defaultYn(isFirst ? "Y" : "N")
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
