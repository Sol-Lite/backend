-- widget_layouts FK에 ON DELETE CASCADE 추가
-- DashboardRepository @Modifying @Query 사용 시 JPA cascade 우회 → DB CASCADE 필요
ALTER TABLE widget_layouts DROP CONSTRAINT fk_wlayouts_dashboard;
ALTER TABLE widget_layouts ADD CONSTRAINT fk_wlayouts_dashboard
    FOREIGN KEY (dashboard_id) REFERENCES dashboards (dashboard_id) ON DELETE CASCADE;
