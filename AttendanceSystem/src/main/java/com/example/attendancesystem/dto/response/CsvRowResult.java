package com.example.attendancesystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CsvRowResult {
    private int rowNumber;
    private String registerNumber;
    private String studentName;
    private String departmentCode;
    private String departmentName;
    private String status;
    private String errorCode;
    private String field;
    private String message;

    public static CsvRowResultBuilder builder() {
        return new CsvRowResultBuilder();
    }

    public static class CsvRowResultBuilder {
        private int rowNumber;
        private String registerNumber;
        private String studentName;
        private String departmentCode;
        private String departmentName;
        private String status;
        private String errorCode;
        private String field;
        private String message;

        public CsvRowResultBuilder rowNumber(int rowNumber) { this.rowNumber = rowNumber; return this; }
        public CsvRowResultBuilder registerNumber(String registerNumber) { this.registerNumber = registerNumber; return this; }
        public CsvRowResultBuilder studentName(String studentName) { this.studentName = studentName; return this; }
        public CsvRowResultBuilder departmentCode(String departmentCode) { this.departmentCode = departmentCode; return this; }
        public CsvRowResultBuilder departmentName(String departmentName) { this.departmentName = departmentName; return this; }
        public CsvRowResultBuilder status(String status) { this.status = status; return this; }
        public CsvRowResultBuilder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public CsvRowResultBuilder field(String field) { this.field = field; return this; }
        public CsvRowResultBuilder message(String message) { this.message = message; return this; }

        public CsvRowResult build() {
            return new CsvRowResult(rowNumber, registerNumber, studentName, departmentCode, departmentName, status, errorCode, field, message);
        }
    }
}
