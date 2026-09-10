package com.tranverse.chatserver.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tranverse.chatserver.dto.response.message.MediaUploadResponse;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.media.CloudinaryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;

    public MediaUploadResponse uploadImage(MultipartFile file) {
        validate(file);
        if (!properties.isConfigured()) {
            throw new AppException(ErrorCode.MEDIA_UPLOAD_UNAVAILABLE,
                    "Cloudinary is not configured on the server");
        }

        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", properties.folder(),
                    "use_filename", true,
                    "unique_filename", true,
                    "overwrite", false));
            return new MediaUploadResponse(
                    stringValue(result.get("secure_url")),
                    "image/" + stringValue(result.get("format")).toLowerCase(Locale.ROOT),
                    longValue(result.get("bytes"), file.getSize()),
                    stringValue(result.get("public_id")),
                    integerValue(result.get("width")),
                    integerValue(result.get("height")));
        } catch (IOException | RuntimeException exception) {
            log.error("Cloudinary image upload failed", exception);
            throw new AppException(ErrorCode.MEDIA_UPLOAD_UNAVAILABLE);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_MEDIA, "Choose a non-empty image file");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.INVALID_MEDIA,
                    "Supported image formats are JPEG, PNG, WebP, and GIF");
        }
        if (file.getSize() > properties.maxImageBytes()) {
            throw new AppException(ErrorCode.MEDIA_TOO_LARGE);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long longValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
