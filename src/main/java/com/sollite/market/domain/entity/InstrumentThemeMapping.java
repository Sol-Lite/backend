package com.sollite.market.domain.entity;

import com.sollite.market.domain.enums.StockTheme;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "instrument_theme_mappings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_instrument_theme",
                columnNames = {"instrument_id", "theme_code"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstrumentThemeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_code", nullable = false, length = 20)
    private StockTheme themeCode;

    public InstrumentThemeMapping(Instrument instrument, StockTheme themeCode) {
        this.instrument = instrument;
        this.themeCode = themeCode;
    }
}
