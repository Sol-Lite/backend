package com.sollite.widget.domain.repository;

import com.sollite.widget.domain.entity.Dashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DashboardRepository extends JpaRepository<Dashboard, Long> {

    @Query("SELECT DISTINCT d FROM Dashboard d LEFT JOIN FETCH d.widgetLayouts WHERE d.userId = :userId ORDER BY d.pageOrder")
    List<Dashboard> findAllByUserIdWithWidgets(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM dashboards WHERE user_id = :userId", nativeQuery = true)
    void deleteAllByUserId(@Param("userId") Long userId);
}
