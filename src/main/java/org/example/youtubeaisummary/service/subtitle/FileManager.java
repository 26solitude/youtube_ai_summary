package org.example.youtubeaisummary.service.subtitle;

import org.example.youtubeaisummary.exception.subtitle.NoSubtitlesFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileManager {
    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);
    private static final String TEMP_DIR_NAME = "temp_subtitles";

    /**
     * 작업 고유의 임시 디렉토리 경로를 반환합니다.
     */
    public Path getTempDir() {
        return Path.of(TEMP_DIR_NAME);
    }

    /**
     * 파일을 읽어 내용을 문자열로 반환합니다.
     */
    public String readFileContent(Path filePath) throws IOException {
        if (!Files.exists(filePath) || Files.size(filePath) == 0) {
            throw new NoSubtitlesFoundException("자막 파일이 생성되지 않았거나 내용이 비어있습니다. (영상 데이터 문제)");
        }
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 파일을 안전하게 삭제합니다.
     */
    public void deleteFile(Path filePath) {
        if (filePath != null) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                logger.warn("임시 자막 파일 삭제 실패: {}", filePath, e);
            }
        }
    }
}