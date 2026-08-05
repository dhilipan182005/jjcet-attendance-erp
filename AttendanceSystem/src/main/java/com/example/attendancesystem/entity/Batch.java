package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_name", nullable = false, unique = true, length = 50)
    private String name;

    // Batches were previously a flat, global name list with no relation to Department or
    // real start/end years, which made automatic 4-year batch derivation from a registration
    // number impossible. These three fields are additive and nullable so existing rows keep
    // working; new/CSV-derived batches populate them. See V2 migration.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = false;
}
