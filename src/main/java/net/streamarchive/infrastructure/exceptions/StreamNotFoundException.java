package net.streamarchive.infrastructure.exceptions;

public class StreamNotFoundException extends RuntimeException {
    public StreamNotFoundException(Throwable cause) {
        super(cause);
    }

    public StreamNotFoundException(String message) {
        super(message);
    }

    public StreamNotFoundException(String s, Exception e) {
        super(s,e);
    }
}

