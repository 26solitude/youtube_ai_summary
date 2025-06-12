package org.example.youtubeaisummary;

import org.example.youtubeaisummary.exception.subtitle.NoSubtitlesFoundException;
import org.example.youtubeaisummary.service.subtitle.FileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileManagerTest {

    @TempDir
    Path tempDir; // JUnit 5가 임시 디렉토리를 자동으로 생성하고 테스트 후 삭제합니다.
    private FileManager fileManager;

    @BeforeEach
    void setUp() {
        fileManager = new FileManager();
    }

    @Test
    @DisplayName("성공: 파일 내용 읽기")
    void readFileContent_Success() throws IOException {
        // Arrange (준비)
        Path testFile = tempDir.resolve("test.txt");
        String expectedContent = "Hello, World!";
        Files.writeString(testFile, expectedContent, StandardCharsets.UTF_8);

        // Act (실행)
        String actualContent = fileManager.readFileContent(testFile);

        // Assert (검증)
        assertEquals(expectedContent, actualContent);
    }

    @Test
    @DisplayName("예외: 파일이 존재하지 않을 때")
    void readFileContent_ThrowsException_WhenFileDoesNotExist() {
        // Arrange
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");

        // Act & Assert
        assertThrows(NoSubtitlesFoundException.class, () -> {
            fileManager.readFileContent(nonExistentFile);
        }, "자막 파일이 생성되지 않았거나 내용이 비어있습니다. (영상 데이터 문제)");
    }

    @Test
    @DisplayName("예외: 파일 내용은 비어있을 때")
    void readFileContent_ThrowsException_WhenFileIsEmpty() throws IOException {
        // Arrange
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);

        // Act & Assert
        assertThrows(NoSubtitlesFoundException.class, () -> {
            fileManager.readFileContent(emptyFile);
        });
    }

    @Test
    @DisplayName("성공: 파일 삭제")
    void deleteFile_Success() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("deletable.txt");
        Files.createFile(testFile);
        assertTrue(Files.exists(testFile));

        // Act
        fileManager.deleteFile(testFile);

        // Assert
        assertFalse(Files.exists(testFile));
    }

    @Test
    @DisplayName("성공: 존재하지 않는 파일 삭제 시도 (오류 없음)")
    void deleteFile_DoesNothing_WhenFileDoesNotExist() {
        // Arrange
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");

        // Act & Assert
        // Files.deleteIfExists를 사용했으므로 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> {
            fileManager.deleteFile(nonExistentFile);
        });
    }
}