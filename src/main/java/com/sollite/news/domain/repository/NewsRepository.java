package com.sollite.news.domain.repository;

import com.sollite.news.domain.entity.NewsDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface NewsRepository extends MongoRepository<NewsDocument, String> {

    List<NewsDocument> findAllByOrderByPublishedAtDesc(Pageable pageable);

    List<NewsDocument> findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(Date from, Pageable pageable);

    Optional<NewsDocument> findByNewsId(String newsId);
}
