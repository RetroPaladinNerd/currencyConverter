package com.example.currencyconverter.utils;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InMemoryCache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryCache.class);

    private final int maxCacheSize;
    private final Map<K, V> cache;
    private ByteBuffer sizeBuffer;

    public InMemoryCache(@Value("${cache.max-size:5242}") int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
        this.cache = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                if (sizeBuffer.position() > maxCacheSize) {
                    removeSize(eldest.getKey(), eldest.getValue());
                    logger.info("Cache is full, removing eldest entry: {}", eldest.getKey());
                    return true;
                }
                return false;
            }
        };
        this.sizeBuffer = ByteBuffer.allocate(maxCacheSize);
    }

    public V get(K key) {
        V value = cache.get(key);
        if (value != null) {
            logger.debug("Cache hit for key: {}", key);
        } else {
            logger.debug("Cache miss for key: {}", key);
        }
        return value;
    }

    public void put(K key, V value) {
        int entrySize = estimateSize(key, value);
        if (entrySize > maxCacheSize) {
            logger.warn("Entry size {} exceeds max cache size {}", entrySize, maxCacheSize);
            return;
        }
        while (sizeBuffer.position() + entrySize > maxCacheSize) {
            K eldestKey = cache.keySet().iterator().next();
            V eldestValue = cache.get(eldestKey);
            cache.remove(eldestKey);
            removeSize(eldestKey, eldestValue);
            logger.info("Evicting entry {} to make room", eldestKey);
        }

        cache.put(key, value);
        addSize(key, value);
        logger.debug("Cache put for key: {}", key);
    }

    public void evict(K key) {
        V value = cache.remove(key);
        if (value != null) {
            removeSize(key, value);
            logger.debug("Cache evict for key: {}", key);
        }
    }

    public void clear() {
        cache.clear();
        sizeBuffer.clear();
        logger.info("Cache cleared");
    }

    private int estimateSize(K key, V value) {
        return key.toString().length() * 2 + value.toString().length() * 2;
    }

    private void addSize(K key, V value) {
        int entrySize = estimateSize(key, value);
        if (sizeBuffer.position() + entrySize <= maxCacheSize) {
            sizeBuffer.position(sizeBuffer.position() + entrySize);
        } else {
            logger.error("Attempted to add an entry of size {} when only {} bytes remained", entrySize, maxCacheSize - sizeBuffer.position());
        }
    }

    private void removeSize(K key, V value) {
        int entrySize = estimateSize(key, value);
        sizeBuffer.position(Math.max(0, sizeBuffer.position() - entrySize));
    }

    public int getCurrentSize() {
        return sizeBuffer.position();
    }
}