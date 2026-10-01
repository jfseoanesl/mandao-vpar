package com.mandao.identity;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de ventana fija en memoria. Suficiente para una sola instancia (MVP);
 * al escalar horizontalmente se reemplaza por Redis/Bucket4j distribuido.
 */
@Component
public class RateLimiter {

    private record Window(long startMillis, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000;
        Window w = windows.compute(key, (k, current) -> {
            if (current == null || now - current.startMillis() >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(current.startMillis(), current.count() + 1);
        });
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis() >= 3_600_000);
        }
        return w.count() <= limit;
    }

    public void reset() {
        windows.clear();
    }
}
