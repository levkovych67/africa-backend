package com.africe.backend.admin.controller;

import com.africe.backend.admin.service.S3PresignService;
import com.africe.backend.common.dto.PresignRequest;
import com.africe.backend.common.dto.PresignResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products/images")
public class ImageController {

    private final S3PresignService s3PresignService;

    public ImageController(S3PresignService s3PresignService) {
        this.s3PresignService = s3PresignService;
    }

    @PostMapping("/presign")
    public PresignResponse getPresignedUrl(@Valid @RequestBody PresignRequest request) {
        return s3PresignService.generatePresignedUrl(request);
    }
}
