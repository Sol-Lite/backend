package com.sollite.widget.domain.repository;

import com.sollite.widget.domain.entity.Dashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DashboardRepository extends JpaRepository<Dashboard, Long> {

    @Query("SELECT DISTINCT d FROM Dashboard d LEFT JOIN FETCH d.widgetLayouts WHERE d.userId = :userId ORDER BY d.pageOrder")
    List<Dashboard> findAllByUserIdWithWidgets(@Param("userId") Long userId);

    List<Dashboard> findAllByUserIdOrderByPageOrder(Long userId);

    void deleteAllByUserId(Long userId);
}
