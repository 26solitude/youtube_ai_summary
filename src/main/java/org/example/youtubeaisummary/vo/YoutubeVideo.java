package org.example.youtubeaisummary.vo;

import org.example.youtubeaisummary.exception.subtitle.InvalidYoutubeUrlException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeVideo {
    private static final Pattern ID_PATTERN =
            Pattern.compile("(?:youtu\\.be/|v=)([A-Za-z0-9_-]{11})");
    private final String videoId;

    public YoutubeVideo(String url) {
        if (url == null || url.isEmpty()) {
            throw new InvalidYoutubeUrlException("URL은 비어 있을 수 없습니다.");
        }
        Matcher m = ID_PATTERN.matcher(url);
        if (!m.find()) {
            throw new InvalidYoutubeUrlException("잘못된 YouTube URL 형식입니다: " + url);
        }
        this.videoId = m.group(1);
    }

    public String getVideoId() {
        return this.videoId;
    }
}
