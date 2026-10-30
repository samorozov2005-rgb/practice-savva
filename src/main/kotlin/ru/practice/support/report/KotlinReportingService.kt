package ru.practice.support.report

import org.springframework.stereotype.Service
import ru.practice.support.domain.TicketStatus
import ru.practice.support.exception.InvalidRequestException
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class KotlinReportingService(
    private val dataProvider: ReportingDataProvider,
    private val clock: Clock,
) {
    fun summary(
        from: Instant,
        to: Instant,
    ): SummaryReport {
        if (!from.isBefore(to)) throw InvalidRequestException("Параметр from должен быть раньше to")

        val tickets = dataProvider.findCreatedBetween(from, to)
        val now = clock.instant()
        val resolvedDurations =
            tickets
                .asSequence()
                .filter { it.status == TicketStatus.RESOLVED }
                .mapNotNull { ticket -> ticket.closedAt?.let { Duration.between(ticket.createdAt, it).toMillis() / 1000.0 } }
                .toList()

        return SummaryReport(
            createdCount = tickets.size.toLong(),
            byStatus = tickets.groupingBy { it.status.name }.eachCount().mapValues { it.value.toLong() },
            byPriority = tickets.groupingBy { it.priority.name }.eachCount().mapValues { it.value.toLong() },
            overdueOpenCount =
                tickets
                    .count {
                        (it.status == TicketStatus.NEW || it.status == TicketStatus.IN_PROGRESS) && it.dueAt.isBefore(now)
                    }.toLong(),
            averageResolutionTimeSeconds = resolvedDurations.takeIf { it.isNotEmpty() }?.average(),
        )
    }
}
