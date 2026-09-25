package com.ssolab.gallery.storage;

public record StoredArtworkImage(String storageKey, String originalPath, String webImagePath,
    String thumbnailPath, String contentType, int widthPx, int heightPx) { }
