package com.sollite.index.controller;

import com.sollite.index.dto.IndexResponse;
import com.sollite.index.service.IndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/indices")
@RequiredArgsConstructor
public class IndexController {

    private final IndexService indexService;

    @GetMapping
    public ResponseEntity<List<IndexResponse>> getIndices() {
        return ResponseEntity.ok(indexService.getIndices());
    }
}
