package com.example.attendancesystem.controller;

import com.example.attendancesystem.dto.response.ApiResponse;
import com.example.attendancesystem.dto.response.CsvImportSummary;
import com.example.attendancesystem.service.CsvImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/admin/students/csv")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CsvImportController {

    private final CsvImportService csvImportService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] content = csvImportService.csvTemplate().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jjcet_erp_student_import_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(content);
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<CsvImportSummary>> preview(@RequestParam("file") MultipartFile file) {
        CsvImportSummary summary = csvImportService.preview(file);
        return ResponseEntity.ok(ApiResponse.<CsvImportSummary>builder().success(true)
                .message("Preview ready. Nothing has been saved yet.")
                .data(summary)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<CsvImportSummary>> confirm(@RequestParam("file") MultipartFile file) {
        CsvImportSummary summary = csvImportService.confirm(file);
        return ResponseEntity.ok(ApiResponse.<CsvImportSummary>builder().success(true)
                .message("Students imported successfully.")
                .data(summary)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
