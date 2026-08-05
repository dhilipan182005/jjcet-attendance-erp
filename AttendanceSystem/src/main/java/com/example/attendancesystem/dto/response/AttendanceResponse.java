package com.example.attendancesystem.dto.response;

import com.example.attendancesystem.entity.Session;
import com.example.attendancesystem.entity.Status;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponse {

    private Long id;

    private Long studentId;

    private String registerNumber;

    private String studentName;

    private LocalDate date;

    private Session session;

    private Status status;

    private Boolean eveningClassEnabled;

    private String markedBy; 

    private String message;
}
