package org.example.youtubeaisummary.exception;

public class InvalidYoutubeUrlException extends IllegalArgumentException {
    public InvalidYoutubeUrlException(String message) {
        super(message);
    }
}
