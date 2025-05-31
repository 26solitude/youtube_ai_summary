package org.example.youtubeaisummary.vo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeVideo {
    private static final Pattern ID_PATTERN =
            Pattern.compile("(?:youtu\\.be/|v=)([A-Za-z0-9_-]{11})");
    private final String videoId;

    public YoutubeVideo(String url) {
        Matcher m = ID_PATTERN.matcher(url);
        if (!m.find()) {
            throw new IllegalArgumentException("잘못된 YouTube URL 형식입니다: " + url);
        }
        this.videoId = m.group(1);
    }

    public String getVideoId() {
        return this.videoId;
    }
}
