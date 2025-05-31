package org.example.youtubeaisummary.converter;

import org.example.youtubeaisummary.vo.YoutubeVideo;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToYoutubeVideoConverter implements Converter<String, YoutubeVideo> {

    @Override
    public YoutubeVideo convert(String source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("YouTube URL cannot be null or empty");
        }
        return new YoutubeVideo(source);
    }

}
