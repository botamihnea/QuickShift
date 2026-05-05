package com.licenta.licentabackend.dto;

import java.util.List;

public record StoreStaffResponse(
        ManagerSummaryDto manager,
        List<EmployeeSummaryDto> employees
) {}