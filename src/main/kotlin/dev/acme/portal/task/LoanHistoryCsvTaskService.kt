package dev.acme.portal.task

import dev.acme.portal.entity.Loan
import dev.acme.portal.entity.LoanStatus
import dev.quatrion.portal.task.TaskExecutionResult
import dev.quatrion.portal.task.TaskExecutor
import io.quarkus.arc.Unremovable
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import org.hibernate.reactive.mutiny.Mutiny
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Serwis wykonawczy dla [dev.acme.portal.task.LoanHistoryCsvTask] — implementuje [TaskExecutor].
 *
 * Generuje plik CSV z historią wypożyczeń. Gdy [LoanHistoryCsvTask.memberId] jest null →
 * eksportuje wypożyczenia wszystkich czytelników.
 *
 * Kolumny: id, memberId, bookId, bookTitle, status, loanDate, dueDate, returnDate, renewalCount, createdAt
 *
 * Odkrywany automatycznie przez [RunTaskSyncHandler] / [RunTaskAsyncHandler] / [TaskSchedulerService]
 * przez CDI `Instance<TaskExecutor<*>>` — brak ręcznej rejestracji.
 */
@ApplicationScoped
@Unremovable
class LoanHistoryCsvTaskService : TaskExecutor<LoanHistoryCsvTask> {

    override val taskClass: Class<LoanHistoryCsvTask> = LoanHistoryCsvTask::class.java

    @Inject
    lateinit var sessionFactoryInstance: Instance<Mutiny.SessionFactory>

    private fun sf() = sessionFactoryInstance.get()

    // ── TaskExecutor API ──────────────────────────────────────────────────────

    override suspend fun generateResult(task: LoanHistoryCsvTask): TaskExecutionResult {
        val loans = queryLoans(task.memberId, task.includeOverdue)
        val (csvBytes, rowCount) = buildCsvPair(loans)
        return TaskExecutionResult(
            bytes = csvBytes,
            fileName = buildFileName(task.id, task.memberId),
            contentType = "text/csv; charset=UTF-8",
            rowCount = rowCount
        )
    }

    override fun generateResultUni(task: LoanHistoryCsvTask): Uni<TaskExecutionResult> {
        return queryLoansUni(task.memberId, task.includeOverdue)
            .map { loans ->
                val (csvBytes, rowCount) = buildCsvPair(loans)
                TaskExecutionResult(
                    bytes = csvBytes,
                    fileName = buildFileName(task.id, task.memberId),
                    contentType = "text/csv; charset=UTF-8",
                    rowCount = rowCount
                )
            }
    }

    // ── Zapytania JPQL ────────────────────────────────────────────────────────

    private suspend fun queryLoans(memberId: Long?, includeOverdue: Boolean): List<Loan> {
        val conditions = mutableListOf<String>()
        if (memberId != null) conditions.add("e.memberId = :memberId")
        if (!includeOverdue) conditions.add("e.status <> :overdueStatus")
        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val hql = "FROM Loan e $whereClause ORDER BY e.loanDate DESC"
        return sf().withSession { session ->
            val q = session.createQuery(hql, Loan::class.java)
            if (memberId != null) q.setParameter("memberId", memberId)
            if (!includeOverdue) q.setParameter("overdueStatus", LoanStatus.OVERDUE)
            q.resultList
        }.awaitSuspending()
    }

    private fun queryLoansUni(memberId: Long?, includeOverdue: Boolean): Uni<List<Loan>> {
        val conditions = mutableListOf<String>()
        if (memberId != null) conditions.add("e.memberId = :memberId")
        if (!includeOverdue) conditions.add("e.status <> :overdueStatus")
        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val hql = "FROM Loan e $whereClause ORDER BY e.loanDate DESC"
        return sf().withSession { session ->
            val q = session.createQuery(hql, Loan::class.java)
            if (memberId != null) q.setParameter("memberId", memberId)
            if (!includeOverdue) q.setParameter("overdueStatus", LoanStatus.OVERDUE)
            q.resultList
        }
    }

    // ── Generowanie CSV ───────────────────────────────────────────────────────

    private fun buildCsvPair(loans: List<Loan>): Pair<ByteArray, Int> {
        val csv = buildString {
            appendLine("id,memberId,bookId,bookTitle,status,loanDate,dueDate,returnDate,renewalCount,createdAt")
            for (loan in loans) {
                append(loan.id).append(',')
                append(loan.memberId ?: "").append(',')
                append(loan.bookId ?: "").append(',')
                append(escapeCsv(loan.bookTitle)).append(',')
                append(loan.status).append(',')
                append(escapeCsv(loan.loanDate)).append(',')
                append(escapeCsv(loan.dueDate)).append(',')
                append(escapeCsv(loan.returnDate)).append(',')
                append(loan.renewalCount).append(',')
                appendLine(escapeCsv(loan.createdAt))
            }
        }
        return Pair(csv.toByteArray(Charsets.UTF_8), loans.size)
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n'))
            "\"${value.replace("\"", "\"\"")}\"" else value

    private fun buildFileName(taskId: Long, memberId: Long?): String {
        val memberSuffix = if (memberId != null) "_member$memberId" else "_all"
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "loan_history${memberSuffix}_task${taskId}_$ts.csv"
    }
}

