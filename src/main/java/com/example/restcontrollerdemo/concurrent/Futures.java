package com.example.restcontrollerdemo.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class Futures {

    private Futures() {}

    /** Joins {@code future}, rethrowing its own exception so an AppException still maps to its status (e.g. 404). */
    public static <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            throw e.getCause() instanceof RuntimeException cause ? cause : e;
        }
    }
}
