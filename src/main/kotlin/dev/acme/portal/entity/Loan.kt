package dev.acme.portal.entity

import dev.quatrion.portal.annotation.*
import dev.quatrion.portal.base.AuditableEntity
import dev.quatrion.portal.model.ActionResult
import dev.quatrion.portal.model.EntityData
import io.quarkus.arc.Unremovable

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.*
import org.hibernate.annotations.Formula

// ─── Enums ───────────────────────────────────────────────────────────────────

enum class LoanStatus { ACTIVE, RETURNED, OVERDUE, EXTENDED, CANCELLED }

// ─── Modele formularzy akcji ─────────────────────────────────────────────────

data class ExtendLoanForm(
    @field:PortalFormField(
        label = "Nowa data zwrotu",
        renderer = RendererType.DATE,
        required = true,
        tooltip = "Wybierz nową datę — musi być późniejsza niż aktualna",
        order = 1
    )
    val newDueDate: String = "",

    @field:PortalFormField(
        label = "Powód przedłużenia",
        renderer = RendererType.SELECT,
        selectOptions = ["PROŚBA CZYTELNIKA", "CHOROBA", "NIEDOSTĘPNOŚĆ KOPII", "INNE"],
        required = true,
        order = 2
    )
    val reason: String = "PROŚBA CZYTELNIKA",

    @field:PortalFormField(
        label = "Uwagi",
        renderer = RendererType.TEXTAREA,
        placeholder = "Opcjonalne uwagi do przedłużenia...",
        order = 3
    )
    val notes: String = ""
)

// ─── Handlery akcji ──────────────────────────────────────────────────────────

@ApplicationScoped
@Unremovable
class ReturnBookHandler {
    suspend fun validate(entity: dev.acme.portal.entity.Loan, formData: EntityData?): String? {
        return when (entity.status) {
            LoanStatus.RETURNED  -> "Książka została już zwrócona"
            LoanStatus.CANCELLED -> "Wypożyczenie zostało anulowane — zwrot niemożliwy"
            else                 -> null
        }
    }

    suspend fun execute(entity: dev.acme.portal.entity.Loan, formData: EntityData?): ActionResult {
        return ActionResult.Success(
            "Zwrot wypożyczenia #${entity.id} zarejestrowany pomyślnie.",
            refreshTable = true
        )
    }
}

@ApplicationScoped
@Unremovable
class ExtendLoanHandler {
    suspend fun validate(entity: dev.acme.portal.entity.Loan, formData: EntityData?): String? {
        return when {
            entity.status == LoanStatus.RETURNED  -> "Nie można przedłużyć zwróconego wypożyczenia"
            entity.status == LoanStatus.CANCELLED -> "Nie można przedłużyć anulowanego wypożyczenia"
            entity.renewalCount >= 3              -> "Osiągnięto limit 3 przedłużeń dla tego wypożyczenia"
            else                                  -> null
        }
    }

    suspend fun execute(entity: dev.acme.portal.entity.Loan, formData: EntityData?): ActionResult {
        val newDueDate = formData?.get("newDueDate") as? String ?: ""
        val reason     = formData?.get("reason") as? String ?: ""
        return ActionResult.Success(
            "Wypożyczenie #${entity.id} przedłużone do $newDueDate. Powód: $reason",
            refreshTable = true
        )
    }
}

// ─── Encja: Wypożyczenie ──────────────────────────────────────────────────────
// Demonstracja: DATETIME renderer, kaskadowy dropdown (genreId → bookId via dependsOn),
// @PortalRelation editable=false (memberId tylko do odczytu),
// @PortalRelation createAllowed=true (bookId),
// @PortalDependency: SHOW (returnDate tylko gdy RETURNED),
//                   SHOW (notes tylko gdy OVERDUE, clearOnHide=true),
//                   HIDE (renewalCount gdy ACTIVE),
// 2x @PortalAction (destructive confirm, formModel z datą),
// @PortalSecurity, auditLog, FilterType: EXACT, RANGE, IN, NONE
// ─────────────────────────────────────────────────────────────────────────────

@Entity
@PortalEntity(
    label = "Wypożyczenie",
    labelKey = "entity.loan",
    module = "Library",
    group = "Operacje",
    groupKey = "group.operations",
    icon = "calendar-check",
    order = 5,
    description = "Rejestr wypożyczeń — łączy czytelnika z wypożyczoną książką",
    descriptionKey = "entity.loan.description",
    allowEdit = true,
    auditLog = true,
    pageSize = 30
)
@PortalSecurity(
    viewRoles = ["portal-user", "portal-admin"],
    editRoles = ["portal-admin"],
    deleteRoles = ["portal-admin"],
    actionRoles = ["portal-user", "portal-admin"]
)
@PortalAction(
    name = "returnBook",
    label = "Zarejestruj zwrot",
    labelKey = "action.loan.returnBook",
    icon = "book-check",
    handler = ReturnBookHandler::class,
    confirmMessage = "Czy potwierdzasz zwrot książki? Operacja ustawia datę dzisiejszą jako datę zwrotu.",
    confirmMessageKey = "action.loan.returnBook.confirm",
    variant = "destructive",
    order = 1
)
@PortalAction(
    name = "extendLoan",
    label = "Przedłuż wypożyczenie",
    labelKey = "action.loan.extend",
    icon = "calendar-plus",
    handler = ExtendLoanHandler::class,
    formModel = ExtendLoanForm::class,
    variant = "outline",
    order = 2
)
class Loan : AuditableEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", labelKey = "field.common.id", order = 0, readonly = true, showInFilter = false, width = 80)
    var id: Long = 0

    // Relacja do Czytelnika — zablokowana (editable=false), nie można zmienić po utworzeniu
    @Column(nullable = false)
    @PortalField(
        label = "Czytelnik", labelKey = "field.loan.memberId",
        order = 1, required = true,
        renderer = RendererType.RELATION, filterType = FilterType.EXACT,
        tooltip = "Czytelnik nie może być zmieniony po założeniu wypożyczenia",
        tooltipKey = "tooltip.loan.memberId"
    )
    @PortalRelation(
        targetEntity = Member::class,
        editable = false,
        displayFields = ["firstName", "lastName", "email"],
        searchFields = ["firstName", "lastName", "email"],
        labelField = "lastName",
        filterQuery = "e.isActive = true AND e.deleted = false",
        maxResults = 50
    )
    var memberId: Long? = null

    // ─── Kaskadowy dropdown: Gatunek filtruje listę Książek ──────────────────
    // genreId jest pomocniczym polem @Transient — wartość nie jest persystowana.
    // Frontend przekazuje ją jako dependsOn do endpointu /lookup Książek.

    @Transient
    @PortalField(
        label = "Filtruj po gatunku (pomocnicze)", labelKey = "field.loan.genreFilter",
        order = 2,
        renderer = RendererType.RELATION,
        filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        tooltip = "Wybierz gatunek, aby zawęzić listę dostępnych książek poniżej",
        tooltipKey = "tooltip.loan.genreFilter"
    )
    @PortalRelation(
        targetEntity = Genre::class,
        editable = true,
        displayFields = ["name"],
        searchFields = ["name"],
        labelField = "name",
        filterQuery = "e.isActive = true",
        maxResults = 200
    )
    var genreId: Long? = null

    // bookId korzysta z dependsOn="genreId" — backend generuje: AND e.genreId = :genreId
    // Dzięki temu lista książek jest filtrowana do wybranego gatunku
    @Column(nullable = false)
    @PortalField(
        label = "Książka", labelKey = "field.loan.bookId",
        order = 3, required = true,
        renderer = RendererType.RELATION, filterType = FilterType.EXACT,
        tooltip = "Wybierz gatunek powyżej, aby zawęzić listę",
        tooltipKey = "tooltip.loan.bookId"
    )
    @PortalRelation(
        targetEntity = Book::class,
        editable = true,
        displayFields = ["title", "isbn", "status"],
        searchFields = ["title", "isbn"],
        createAllowed = false,
        labelField = "title",
        filterQuery = "e.isActive = true AND e.status = 'AVAILABLE'",
        dependsOn = "genreId",
        maxResults = 50
    )
    var bookId: Long? = null

    @Formula("(SELECT b.title FROM book b WHERE b.id = book_id)")
    @PortalField(
        label = "Tytuł książki", labelKey = "field.loan.bookTitle",
        order = 11,
        renderer = RendererType.TEXT,
        filterType = FilterType.NONE,
        readonly = true,
        showInFilter = false,
        tooltip = "Tytuł książki — pobierany automatycznie na podstawie bookId",
        tooltipKey = "tooltip.loan.bookTitle"
    )
    var bookTitle: String = ""

    // ─── @Formula: imię i nazwisko czytelnika ────────────────────────────────
    @Formula("(SELECT CONCAT(m.first_name, ' ', m.last_name) FROM member m WHERE m.id = member_id)")
    @PortalField(
        label = "Czytelnik (pełne imię)", labelKey = "field.loan.memberName",
        order = 12,
        renderer = RendererType.TEXT,
        filterType = FilterType.NONE,
        readonly = true,
        showInFilter = false,
        showInTable = false,
        tooltip = "Imię i nazwisko czytelnika — pobierane automatycznie na podstawie memberId",
        tooltipKey = "tooltip.loan.memberName"
    )
    var memberName: String = ""

    @Column(nullable = false)
    @PortalField(
        label = "Status wypożyczenia", labelKey = "field.loan.status",
        order = 4,
        renderer = RendererType.SELECT, filterType = FilterType.IN,
        selectEnum = LoanStatus::class,
        defaultValue = "ACTIVE"
    )
    @Enumerated(EnumType.STRING)
    var status: dev.acme.portal.entity.LoanStatus = LoanStatus.ACTIVE

    @Column(nullable = false)
    @PortalField(
        label = "Data wypożyczenia", labelKey = "field.loan.loanDate",
        order = 5, required = true,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        tooltip = "Format: RRRR-MM-DD",
        tooltipKey = "tooltip.loan.loanDate"
    )
    var loanDate: String = ""

    @Column(nullable = false)
    @PortalField(
        label = "Termin zwrotu", labelKey = "field.loan.dueDate",
        order = 6, required = true,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        tooltip = "Planowana data zwrotu książki",
        tooltipKey = "tooltip.loan.dueDate"
    )
    var dueDate: String = ""

    // returnDate widoczna TYLKO gdy status = RETURNED
    @Column
    @PortalField(
        label = "Data faktycznego zwrotu", labelKey = "field.loan.returnDate",
        order = 7,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        showInTable = false,
        tooltip = "Uzupełniana automatycznie podczas rejestracji zwrotu",
        tooltipKey = "tooltip.loan.returnDate"
    )
    @PortalDependency(
        field = "status",
        operator = DependencyOperator.EQ,
        value = "RETURNED",
        visibility = DependencyVisibility.SHOW,
        clearOnHide = true
    )
    var returnDate: String = ""

    @Column(nullable = false)
    @PortalField(
        label = "Liczba przedłużeń", labelKey = "field.loan.renewalCount",
        order = 8,
        renderer = RendererType.NUMBER, filterType = FilterType.RANGE,
        readonly = true, min = 0.0, max = 3.0,
        tooltip = "Maksymalnie 3 przedłużenia na jedno wypożyczenie",
        tooltipKey = "tooltip.loan.renewalCount"
    )
    // Ukryj licznik przedłużeń gdy wypożyczenie jest aktywne (nie ma sensu)
    @PortalDependency(
        field = "status",
        operator = DependencyOperator.EQ,
        value = "ACTIVE",
        visibility = DependencyVisibility.HIDE
    )
    var renewalCount: Int = 0

    // Pole uwag POJAWIAJĄCE się tylko gdy status = OVERDUE (clearOnHide = true)
    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Uwagi (przeterminowanie)", labelKey = "field.loan.overdueNotes",
        order = 9,
        renderer = RendererType.TEXTAREA, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        placeholder = "Powód przeterminowania lub działania podjęte wobec czytelnika..."
    )
    @PortalDependency(
        field = "status",
        operator = DependencyOperator.EQ,
        value = "OVERDUE",
        visibility = DependencyVisibility.SHOW,
        clearOnHide = true,
        message = "Wypełnij powód i kroki podjęte wobec czytelnika"
    )
    var overdueNotes: String = ""

}


