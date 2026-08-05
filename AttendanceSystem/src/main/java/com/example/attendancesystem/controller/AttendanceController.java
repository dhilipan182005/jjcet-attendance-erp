package com.example.attendancesystem.controller;

import com.example.attendancesystem.dto.response.AttendanceSummary;
import com.example.attendancesystem.dto.request.AttendanceRequest;
import com.example.attendancesystem.dto.request.BarcodeAttendanceRequest;
import com.example.attendancesystem.dto.response.ApiResponse;
import com.example.attendancesystem.dto.response.AttendanceResponse;
import com.example.attendancesystem.service.AttendanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping
    public ResponseEntity<ApiResponse<AttendanceResponse>> markAttendance(
            @Valid @RequestBody AttendanceRequest request) {

        log.info("Marking manual attendance for student ID: {}", request.getStudentId());
        return buildCreatedResponse(
                "Attendance marked successfully",
                attendanceService.markAttendanceWithTeacher(request)
        );
    }

    @PostMapping("/barcode")
    public ResponseEntity<ApiResponse<AttendanceResponse>> markByBarcode(
            @Valid @RequestBody BarcodeAttendanceRequest request) {

        log.info("Marking barcode attendance for data: {}", request.getRegisterNumber());
        return buildCreatedResponse(
                "Attendance marked via barcode",
                attendanceService.markAttendanceByBarcode(request)
        );
    }

    @GetMapping("/summary/{studentId}")
    public ResponseEntity<ApiResponse<AttendanceSummary>> getSummary(
            @PathVariable @Positive Long studentId) {

        log.info("Fetching attendance summary for student ID: {}", studentId);
        return buildOkResponse(
                "Attendance summary fetched",
                attendanceService.getAttendanceSummary(studentId)
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> buildCreatedResponse(String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<T>builder().success(true)
                        .message(message)
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> buildOkResponse(String message, T data) {
        return ResponseEntity.ok(
                ApiResponse.<T>builder().success(true)
                        .message(message)
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }
}
