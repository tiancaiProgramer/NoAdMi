package io.github.miclean.module;

/** Throttle only success feedback; preference writes are never delayed or dropped. */
final class SaveNoticeGate {
    private long previous = -1;
    boolean allow(long elapsedMillis) {
        if (previous >= 0 && elapsedMillis >= previous && elapsedMillis - previous < 3000) return false;
        previous = elapsedMillis;
        return true;
    }
}
