package com.tranverse.chatserver.service;

import com.cloudinary.Cloudinary;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.media.CloudinaryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MediaServiceTest {
    @Test
    void rejectsUnsupportedFileTypes() {
        MediaService service = service(new CloudinaryProperties("cloud", "key", "secret", "chat", 100));
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        AppException exception = assertThrows(AppException.class, () -> service.uploadImage(file));

        assertEquals(ErrorCode.INVALID_MEDIA, exception.getErrorCode());
    }

    @Test
    void rejectsOversizedImages() {
        MediaService service = service(new CloudinaryProperties("cloud", "key", "secret", "chat", 3));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[4]);

        AppException exception = assertThrows(AppException.class, () -> service.uploadImage(file));

        assertEquals(ErrorCode.MEDIA_TOO_LARGE, exception.getErrorCode());
    }

    @Test
    void reportsMissingCloudinaryConfiguration() {
        MediaService service = service(new CloudinaryProperties("", "", "", "chat", 100));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});

        AppException exception = assertThrows(AppException.class, () -> service.uploadImage(file));

        assertEquals(ErrorCode.MEDIA_UPLOAD_UNAVAILABLE, exception.getErrorCode());
    }

    private MediaService service(CloudinaryProperties properties) {
        return new MediaService(mock(Cloudinary.class), properties);
    }
}
