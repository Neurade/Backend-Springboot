package app.demo.neurade.controllers;

import app.demo.neurade.services.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/file")
@Tag(name = "Files", description = "File management APIs")
public class FileController {

    private final FileService fileService;

    @Operation(
            summary = "Upload profile picture",
            description = "Uploads a profile picture and returns the URL"
    )
    @PostMapping(
            value = "/upload-pfp",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadPfp(
            @Parameter(description = "Profile picture file")
            @RequestParam("file") MultipartFile file
    ) {
        String fileUrl = fileService.uploadPfp(file);
        return ResponseEntity.ok(
                Map.of(
                        "message", "File uploaded successfully",
                        "url", fileUrl
                )
        );
    }

}
