package dev.acme.portal.task

import dev.quatrion.portal.annotation.*
import dev.acme.portal.entity.Member
import dev.quatrion.portal.task.AbstractTask
import dev.quatrion.portal.task.RunTaskAsyncHandler
import dev.quatrion.portal.task.RunTaskSyncHandler
import dev.quatrion.portal.task.TaskRun
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*

// ─── Zakładki formularza ──────────────────────────────────────────────────────

enum class LoanHistoryCsvTaskTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    DEFAULT("Ogólne", "cog", 0),
    PARAMS("Parametry", "settings", 1),
    HISTORY("Historia uruchomień", "history", 2)
}

// ─── Encja zadania ────────────────────────────────────────────────────────────

/**
 * Zadanie: generowanie pliku CSV z pełną historią wypożyczeń wybranego czytelnika.
 *
 * Dziedziczy po [AbstractTask] — wszystkie pola bazowe (name, cron, status, audyt)
 * są kopiowane do tabeli `loan_history_csv_task`.
 *
 * Akcje:
 *  - [RunTaskSyncHandler]  — uruchamia zadanie synchronicznie (blokuje do zakończenia)
 *  - [RunTaskAsyncHandler] — uruchamia zadanie asynchronicznie (natychmiastowy powrót)
 *
 * Anulowanie aktywnego [TaskRun] odbywa się przez akcję `cancelRun` na encji [TaskRun].
 */
@Entity
@Table(name = "loan_history_csv_task")
@PortalEntity(
    label = "Zadanie: Historia wypożyczeń CSV",
    module = "Zadania",
    group = "Definicje",
    icon = "file-spreadsheet",
    order = 1,
    description = "Generuje plik CSV z historią wypożyczeń wybranego czytelnika i zapisuje go jako wynik TaskRun.",
    tabs = _root_ide_package_.dev.acme.portal.task.LoanHistoryCsvTaskTab::class,
    pageSize = 20
)
@PortalAction(
    name = "runSync",
    label = "Uruchom synchronicznie",
    icon = "play",
    handler = RunTaskSyncHandler::class,
    confirmMessage = "Uruchomić zadanie synchronicznie? Strona będzie czekać na zakończenie.",
    variant = "default",
    order = 1
)
@PortalAction(
    name = "runAsync",
    label = "Uruchom asynchronicznie",
    icon = "zap",
    handler = RunTaskAsyncHandler::class,
    confirmMessage = "Uruchomić zadanie w tle? Wynik pojawi się w liście uruchomień.",
    variant = "outline",
    order = 2
)
class LoanHistoryCsvTask : AbstractTask(), PanacheEntityBase {

    // ── Parametry zadania (zakładka PARAMS) ──────────────────────────────────

    /**
     * ID czytelnika — opcjonalne. Gdy null → eksport dotyczy wszystkich członków.
     * Relacja editable=true umożliwia wybór z listy.
     */
    @Column
    @PortalField(
        label = "Czytelnik",
        tab = "PARAMS",
        order = 10,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT,
        showInTable = true,
        tooltip = "Zostaw puste, aby wygenerować CSV dla wszystkich czytelników"
    )
    @PortalRelation(
        targetEntity = _root_ide_package_.dev.acme.portal.entity.Member::class,
        editable = true,
        displayFields = ["firstName", "lastName", "email"],
        searchFields = ["firstName", "lastName", "email"]
    )
    @PortalLookup(
        labelField = "lastName",
        valueField = "id",
        filterQuery = "e.isActive = true AND e.deleted = false",
        maxResults = 100
    )
    var memberId: Long? = null

    @Column(nullable = false)
    @PortalField(
        label = "Uwzględnij przeterminowane",
        tab = "PARAMS",
        order = 11,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.NONE,
        showInTable = false,
        defaultValue = "true",
        tooltip = "Czy eksportować wypożyczenia ze statusem OVERDUE"
    )
    var includeOverdue: Boolean = true

    // ── Historia uruchomień (zakładka HISTORY) ────────────────────────────────
    // parentField="taskRefId" + filter na taskRefType pozwoli frontendowi
    // automatycznie pobrać TaskRun dla tego konkretnego zadania.
    @Transient
    @PortalField(
        label = "Historia uruchomień",
        tab = "HISTORY",
        order = 20,
        renderer = RendererType.RELATION_LIST,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false
    )
    @PortalRelation(
        targetEntity = TaskRun::class,
        editable = false,
        inlineEdit = false,
        displayFields = ["status", "startedBy", "startedAt", "finishedAt"],
        maxItems = 50
    )
    @PortalLookup(parentField = "taskRefId")
    var taskRuns: List<TaskRun>? = null
}

