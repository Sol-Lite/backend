package com.sollite.market.service;

import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.market.dto.InstrumentSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public List<InstrumentSearchResponse> search(String keyword) {
        return instrumentRepository.searchByKeyword(keyword).stream()
                .map(i -> new InstrumentSearchResponse(
                        i.getStockCode(),
                        i.getStockName(),
                        i.getStockNameEn(),
                        i.getMarketType(),
                        i.getExchangeCode()
                ))
                .toList();
    }
}
