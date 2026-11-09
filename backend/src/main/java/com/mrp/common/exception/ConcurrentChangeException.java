package com.mrp.common.exception;

/** A valid write was based on a stale version and must be refreshed. */
public class ConcurrentChangeException extends IllegalStateException {
    public ConcurrentChangeException(String message) { super(message); }
}
