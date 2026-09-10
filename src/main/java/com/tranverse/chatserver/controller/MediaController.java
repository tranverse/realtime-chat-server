package com.tranverse.chatserver.controller;

import com.tranverse.chatserver.dto.response.ApiResponse;
import com.tranverse.chatserver.dto.response.message.MediaUploadResponse;
import com.tranverse.chatserver.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {
    private final MediaService mediaService;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaUploadResponse>> uploadImage(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<MediaUploadResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Image uploaded successfully")
                        .data(mediaService.uploadImage(file))
                        .build());
    }
}
