package com.sollite.balance.dto;

import java.util.List;

public record AssetFlowResponse(
        List<AssetFlowPointResponse> points
) {}
