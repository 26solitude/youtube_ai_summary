package org.example.youtubeaisummary.exception.subtitle;

public class InvalidYoutubeUrlException extends IllegalArgumentException {
    public InvalidYoutubeUrlException(String message) {
        super(message);
    }
}
