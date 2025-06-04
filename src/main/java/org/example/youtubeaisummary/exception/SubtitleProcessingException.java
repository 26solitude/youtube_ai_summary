package org.example.youtubeaisummary.exception;

public class SubtitleProcessingException extends RuntimeException {
    public SubtitleProcessingException(String message) {
        super(message);
    }

    public SubtitleProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
