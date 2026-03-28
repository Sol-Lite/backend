package com.sollite.news.domain.repository;

import com.sollite.news.domain.entity.StockNewsDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface StockNewsRepository extends MongoRepository<StockNewsDocument, String> {

    List<StockNewsDocument> findByStockCodeOrderByPublishedAtDesc(String stockCode, Pageable pageable);

    Optional<StockNewsDocument> findByNewsId(String newsId);
}
