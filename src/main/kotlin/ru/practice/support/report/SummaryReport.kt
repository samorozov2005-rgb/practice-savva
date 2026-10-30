package ru.practice.support.report

data class SummaryReport(
    val createdCount: Long,
    val byStatus: Map<String, Long>,
    val byPriority: Map<String, Long>,
    val overdueOpenCount: Long,
    val averageResolutionTimeSeconds: Double?,
)
