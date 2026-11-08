package com.example.restcontrollerdemo.controller;

/** Keyset pagination bounds, shared by every list endpoint. */
final class Pagination {

    static final String DEFAULT_LIMIT = "20";
    static final long MAX_LIMIT = 100;

    private Pagination() {}

    /** Newest-first lists: no cursor means "from the newest". */
    static long before(Long beforeId) {
        return beforeId == null ? Long.MAX_VALUE : beforeId;
    }

    /** Oldest-first lists: no cursor means "from the oldest". */
    static long after(Long afterId) {
        return afterId == null ? 0 : afterId;
    }
}
