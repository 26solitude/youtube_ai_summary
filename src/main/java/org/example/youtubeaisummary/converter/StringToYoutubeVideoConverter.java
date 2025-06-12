package org.example.youtubeaisummary.converter;

import org.example.youtubeaisummary.exception.subtitle.InvalidYoutubeUrlException;
import org.example.youtubeaisummary.vo.YoutubeVideo;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToYoutubeVideoConverter implements Converter<String, YoutubeVideo> {

    @Override
    public YoutubeVideo convert(String source) {
        if (source == null || source.isEmpty()) {
            throw new InvalidYoutubeUrlException("ouTube URL은 null이거나 비어 있을 수 없습니다.");
        }
        return new YoutubeVideo(source);
    }

}
