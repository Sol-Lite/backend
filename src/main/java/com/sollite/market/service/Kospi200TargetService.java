package com.sollite.market.service;

import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.market.dto.Kospi200Target;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class Kospi200TargetService {

    private static final String TARGETS_CSV_PATH = "data/kospi200_targets.csv";
    private static final String MARKET_TYPE = "KOSPI";

    private final InstrumentRepository instrumentRepository;

    private volatile List<Kospi200Target> cachedTargets;
    private volatile Map<String, Kospi200Target> cachedByCode;

    public List<Kospi200Target> getTargets() {
        List<Kospi200Target> current = cachedTargets;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (cachedTargets == null) {
                List<Kospi200Target> loaded = List.copyOf(loadTargets());
                cachedTargets = loaded;
                cachedByCode = loaded.stream()
                        .collect(Collectors.toUnmodifiableMap(Kospi200Target::stockCode, t -> t));
            }
            return cachedTargets;
        }
    }

    public boolean isKospi200(String stockCode) {
        getTargets();
        return cachedByCode.containsKey(stockCode);
    }

    public Optional<Kospi200Target> findByStockCode(String stockCode) {
        getTargets();
        return Optional.ofNullable(cachedByCode.get(stockCode));
    }

    private List<Kospi200Target> loadTargets() {
        List<Kospi200Target> targets = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();
        Set<String> seenNames = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(TARGETS_CSV_PATH).getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (!"stock_code,stock_name".equals(header)) {
                throw new IllegalStateException("KOSPI200 seed header mismatch: " + header);
            }

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(",", 2);
                if (parts.length != 2) {
                    throw new IllegalStateException("Invalid KOSPI200 seed row at line " + lineNo + ": " + line);
                }

                String stockCode = parts[0].trim();
                String stockName = parts[1].trim();

                if (!seenCodes.add(stockCode)) {
                    throw new IllegalStateException("Duplicate KOSPI200 seed stock code: " + stockCode);
                }
                if (!seenNames.add(stockName)) {
                    throw new IllegalStateException("Duplicate KOSPI200 seed stock name: " + stockName);
                }

                Instrument instrument = instrumentRepository.findByStockCodeAndMarketType(stockCode, MARKET_TYPE)
                        .orElseThrow(() -> new IllegalStateException(
                                "Missing KOSPI instrument for seed target: " + stockCode + " (" + stockName + ")"));

                if (!stockName.equals(instrument.getStockName())) {
                    throw new IllegalStateException(
                            "KOSPI200 seed mismatch for stock code " + stockCode +
                                    ": csv=" + stockName + ", db=" + instrument.getStockName());
                }

                targets.add(new Kospi200Target(
                        instrument.getInstrumentId(),
                        instrument.getStockCode(),
                        instrument.getStockName()
                ));
            }

            if (targets.size() != 200) {
                throw new IllegalStateException("Expected 200 KOSPI200 targets but found " + targets.size());
            }

            return targets;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read KOSPI200 seed file: " + TARGETS_CSV_PATH, e);
        }
    }
}
