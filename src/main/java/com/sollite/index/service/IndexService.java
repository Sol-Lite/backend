package com.sollite.index.service;

import com.sollite.index.dto.IndexResponse;

import java.util.List;

public interface IndexService {
    List<IndexResponse> getIndices();
}
