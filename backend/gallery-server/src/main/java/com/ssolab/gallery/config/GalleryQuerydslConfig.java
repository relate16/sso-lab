package com.ssolab.gallery.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GalleryQuerydslConfig {
    @Bean
    JPAQueryFactory galleryQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
