package org.example.youtubeaisummary.exception;

public class NoSubtitlesFoundException extends SubtitleProcessingException {
    public NoSubtitlesFoundException(String message) {
        super(message);
    }

    public NoSubtitlesFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
