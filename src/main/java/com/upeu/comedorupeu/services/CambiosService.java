package com.upeu.comedorupeu.services;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class CambiosService {

    private final AtomicLong version = new AtomicLong(System.currentTimeMillis() % 100000);

    public void tick() {
        version.incrementAndGet();
    }

    public long version() {
        return version.get();
    }
}
