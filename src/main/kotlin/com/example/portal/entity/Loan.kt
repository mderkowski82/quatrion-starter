package com.example.portal.entity

import dev.quatrion.portal.annotation.*
import dev.quatrion.portal.model.ActionHandler
import dev.quatrion.portal.model.ActionResult
import dev.quatrion.portal.model.EntityData
import io.quarkus.arc.Unremovable
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.*

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
class ReturnBookHandler : ActionHandler<EntityData> {
    override val actionName = "returnBook"

    override suspend fun validate(entity: EntityData, formData: EntityData?): String? {
        val status = entity["status"] as? String
        return when (status) {
            "RETURNED" -> "Książka została już zwrócona"
            "CANCELLED" -> "Wypożyczenie zostało anulowane — zwrot niemożliwy"
            else -> null
        }
    }

    override suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val id = entity["id"]
        // logika: ustawienie returnDate = today, status = RETURNED, odblokowanie egzemplarza
        return ActionResult.Success(
            "Zwrot wypożyczenia #$id zarejestrowany pomyślnie.",
            refreshTable = true
        )
    }
}

@ApplicationScoped
@Unremovable
class ExtendLoanHandler : ActionHandler<EntityData> {
    override val actionName = "extendLoan"

    override suspend fun validate(entity: EntityData, formData: EntityData?): String? {
        val status = entity["status"] as? String
        val renewalCount = (entity["renewalCount"] as? Int) ?: 0
        return when {
            status == "RETURNED" -> "Nie można przedłużyć zwróconego wypożyczenia"
            status == "CANCELLED" -> "Nie można przedłużyć anulowanego wypożyczenia"
            renewalCount >= 3 -> "Osiągnięto limit 3 przedłużeń dla tego wypożyczenia"
            else -> null
        }
    }

    override suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val id = entity["id"]
        val newDueDate = formData?.get("newDueDate") as? String ?: ""
        val reason = formData?.get("reason") as? String ?: ""
        // logika: aktualizacja dueDate, inkrementacja renewalCount, status = EXTENDED
        return ActionResult.Success(
            "Wypożyczenie #$id przedłużone do $newDueDate. Powód: $reason",
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
@Table(name = "loan")
@PortalEntity(
    label = "Wypożyczenie",
    module = "Library",
    group = "Operacje",
    icon = "calendar-check",
    order = 5,
    description = "Rejestr wypożyczeń — łączy czytelnika z wypożyczoną książką",
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
    icon = "book-check",
    handler = ReturnBookHandler::class,
    confirmMessage = "Czy potwierdzasz zwrot książki? Operacja ustawia datę dzisiejszą jako datę zwrotu.",
    variant = "destructive",
    order = 1
)
@PortalAction(
    name = "extendLoan",
    label = "Przedłuż wypożyczenie",
    icon = "calendar-plus",
    handler = ExtendLoanHandler::class,
    formModel = ExtendLoanForm::class,
    variant = "outline",
    order = 2
)
class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", order = 0, readonly = true, showInFilter = false, width = 80)
    var id: Long = 0

    // Relacja do Czytelnika — zablokowana (editable=false), nie można zmienić po utworzeniu
    @Column(nullable = false)
    @PortalField(
        label = "Czytelnik",
        order = 1, required = true,
        renderer = RendererType.RELATION, filterType = FilterType.EXACT,
        tooltip = "Czytelnik nie może być zmieniony po założeniu wypożyczenia"
    )
    @PortalRelation(
        targetEntity = Member::class,
        editable = false,
        displayFields = ["firstName", "lastName", "email"],
        searchFields = ["firstName", "lastName", "email"]
    )
    @PortalLookup(
        labelField = "lastName",
        valueField = "id",
        filterQuery = "e.isActive = true AND e.deleted = false",
        maxResults = 50
    )
    var memberId: Long? = null

    // ─── Kaskadowy dropdown: Gatunek filtruje listę Książek ──────────────────
    // genreId jest pomocniczym polem @Transient — wartość nie jest persystowana.
    // Frontend przekazuje ją jako dependsOn do endpointu /lookup Książek.

    @Transient
    @PortalField(
        label = "Filtruj po gatunku (pomocnicze)",
        order = 2,
        renderer = RendererType.RELATION,
        filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        tooltip = "Wybierz gatunek, aby zawęzić listę dostępnych książek poniżej"
    )
    @PortalRelation(
        targetEntity = Genre::class,
        editable = true,
        displayFields = ["name"],
        searchFields = ["name"]
    )
    @PortalLookup(
        labelField = "name",
        valueField = "id",
        filterQuery = "e.isActive = true",
        maxResults = 200
    )
    var genreId: Long? = null

    // bookId korzysta z dependsOn="genreId" — backend generuje: AND e.genreId = :genreId
    // Dzięki temu lista książek jest filtrowana do wybranego gatunku
    @Column(nullable = false)
    @PortalField(
        label = "Książka",
        order = 3, required = true,
        renderer = RendererType.RELATION, filterType = FilterType.EXACT,
        tooltip = "Wybierz gatunek powyżej, aby zawęzić listę"
    )
    @PortalRelation(
        targetEntity = Book::class,
        editable = true,
        displayFields = ["title", "isbn", "status"],
        searchFields = ["title", "isbn"],
        createAllowed = false
    )
    @PortalLookup(
        labelField = "title",
        valueField = "id",
        filterQuery = "e.isActive = true AND e.status = 'AVAILABLE'",
        dependsOn = "genreId",
        maxResults = 50
    )
    var bookId: Long? = null

    @Column(nullable = false)
    @PortalField(
        label = "Status wypożyczenia",
        order = 4,
        renderer = RendererType.SELECT, filterType = FilterType.IN,
        selectEnum = LoanStatus::class,
        defaultValue = "ACTIVE"
    )
    @Enumerated(EnumType.STRING)
    var status: LoanStatus = LoanStatus.ACTIVE

    @Column(nullable = false)
    @PortalField(
        label = "Data wypożyczenia",
        order = 5, required = true,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        tooltip = "Format: RRRR-MM-DD"
    )
    var loanDate: String = ""

    @Column(nullable = false)
    @PortalField(
        label = "Termin zwrotu",
        order = 6, required = true,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        tooltip = "Planowana data zwrotu książki"
    )
    var dueDate: String = ""

    // returnDate widoczna TYLKO gdy status = RETURNED
    @Column
    @PortalField(
        label = "Data faktycznego zwrotu",
        order = 7,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        showInTable = false,
        tooltip = "Uzupełniana automatycznie podczas rejestracji zwrotu"
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
        label = "Liczba przedłużeń",
        order = 8,
        renderer = RendererType.NUMBER, filterType = FilterType.RANGE,
        readonly = true, min = 0.0, max = 3.0,
        tooltip = "Maksymalnie 3 przedłużenia na jedno wypożyczenie"
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
        label = "Uwagi (przeterminowanie)",
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

    // createdAt — DATETIME renderer (tylko do odczytu)
    @Column
    @PortalField(
        label = "Utworzono",
        order = 10,
        renderer = RendererType.DATETIME, filterType = FilterType.NONE,
        readonly = true, showInFilter = false,
        tooltip = "Data i godzina założenia rekordu wypożyczenia"
    )
    var createdAt: String = ""
}


