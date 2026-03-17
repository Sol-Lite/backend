package com.sollite.market.domain.repository;

import com.sollite.market.domain.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

// 종목 조회 API 구현 시 사용 예정 (초기 적재는 InstrumentDataLoader에서 JdbcTemplate으로 처리)
public interface InstrumentRepository extends JpaRepository<Instrument, Long> {
}
