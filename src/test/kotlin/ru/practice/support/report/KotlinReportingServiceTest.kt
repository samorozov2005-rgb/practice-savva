package ru.practice.support.report

import io.mockk.every
import io.mockk.mockk
import org.springframework.data.jpa.repository.JpaRepository
import ru.practice.support.domain.Category
import ru.practice.support.domain.Ticket
import ru.practice.support.domain.TicketPriority
import ru.practice.support.domain.TicketStatus
import ru.practice.support.exception.InvalidRequestException
import ru.practice.support.repository.TicketRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KotlinReportingServiceTest {
    private val now = Instant.parse("2026-01-10T10:00:00Z")
    private val from = now.minusSeconds(3600)
    private val to = now.plusSeconds(1)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `empty selection produces zeros and nullable average`() {
        val provider = mockk<ReportingDataProvider>()
        every { provider.findCreatedBetween(from, to) } returns emptyList()
        val report = KotlinReportingService(provider, clock).summary(from, to)
        assertEquals(0, report.createdCount)
        assertNull(report.averageResolutionTimeSeconds)
    }

    @Test
    fun `calculates distributions overdue and average resolution seconds`() {
        val category = Category("Сеть", null)
        val resolved = Ticket("a", "a", category, TicketPriority.HIGH, from, to)
        resolved.changeStatus(TicketStatus.RESOLVED, from.plusSeconds(120))
        val overdue = Ticket("b", "b", category, TicketPriority.LOW, from, now.minusSeconds(1))
        val provider = mockk<ReportingDataProvider>()
        every { provider.findCreatedBetween(from, to) } returns listOf(resolved, overdue)

        val report = KotlinReportingService(provider, clock).summary(from, to)
        assertEquals(2, report.createdCount)
        assertEquals(1, report.byStatus["RESOLVED"])
        assertEquals(1, report.overdueOpenCount)
        assertEquals(120.0, report.averageResolutionTimeSeconds)
    }

    @Test
    fun `invalid half-open interval is rejected`() {
        val provider = mockk<ReportingDataProvider>()
        assertFailsWith<InvalidRequestException> { KotlinReportingService(provider, clock).summary(to, from) }
    }

    @Test
    fun `Kotlin test verifies Java data provider delegation`() {
        val repository = mockk<TicketRepository>()
        every { repository.findForReport(from, to) } returns emptyList()
        val javaProvider = JpaReportingDataProvider(repository)
        assertEquals(emptyList(), javaProvider.findCreatedBetween(from, to))
    }
}
