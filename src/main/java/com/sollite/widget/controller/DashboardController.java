package com.sollite.widget.controller;

import com.sollite.global.util.AuthUtil;
import com.sollite.widget.dto.DashboardPageResponse;
import com.sollite.widget.dto.DashboardSaveRequest;
import com.sollite.widget.dto.PresetApplyRequest;
import com.sollite.widget.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/me")
    public ResponseEntity<List<DashboardPageResponse>> getMyDashboards(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(dashboardService.getMyDashboards(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<List<DashboardPageResponse>> saveMyDashboards(
            Authentication authentication,
            @Valid @RequestBody DashboardSaveRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(dashboardService.saveMyDashboards(userId, request));
    }

    @PostMapping("/presets/apply")
    public ResponseEntity<List<DashboardPageResponse>> applyPreset(
            Authentication authentication,
            @Valid @RequestBody PresetApplyRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(dashboardService.applyPreset(userId, request));
    }
}
