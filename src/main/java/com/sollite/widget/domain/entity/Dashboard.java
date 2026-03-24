package com.sollite.widget.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dashboards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dashboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dashboard_id")
    private Long dashboardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dashboard_name", length = 100)
    private String dashboardName;

    @Column(name = "page_order", nullable = false)
    private Integer pageOrder;

    @Column(name = "default_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String defaultYn = "N";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "dashboard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WidgetLayout> widgetLayouts = new ArrayList<>();

    @Builder
    public Dashboard(Long userId, String dashboardName, Integer pageOrder, String defaultYn) {
        this.userId = userId;
        this.dashboardName = dashboardName;
        this.pageOrder = pageOrder;
        this.defaultYn = defaultYn != null ? defaultYn : "N";
    }

    public void addWidgetLayout(WidgetLayout widgetLayout) {
        this.widgetLayouts.add(widgetLayout);
    }
}
