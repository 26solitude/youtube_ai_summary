package org.example.youtubeaisummary.exception;

public class YoutubeApiException extends SubtitleProcessingException {

    public YoutubeApiException(String message) {
        super(message);
    }

    public YoutubeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
