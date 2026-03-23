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
        List<Dashboard> existing = dashboardRepository.findAllByUserIdOrderByPageOrder(userId);
        dashboardRepository.deleteAll(existing);
        dashboardRepository.flush();

        List<Dashboard> toSave = request.pages().stream()
                .map(page -> {
                    Dashboard dashboard = Dashboard.builder()
                            .userId(userId)
                            .dashboardName(page.name())
                            .pageOrder(page.pageOrder())
                            .defaultYn(page.pageOrder() == 1 ? "Y" : "N")
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
