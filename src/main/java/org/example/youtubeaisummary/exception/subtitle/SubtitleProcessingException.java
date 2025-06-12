package org.example.youtubeaisummary.exception.subtitle;

public class SubtitleProcessingException extends RuntimeException {
    public SubtitleProcessingException(String message) {
        super(message);
    }

    public SubtitleProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
