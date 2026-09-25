package com.ssolab.gallery.config;

import com.ssolab.gallery.storage.GalleryStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GalleryStorageProperties.class)
public class GalleryStorageConfig { }
