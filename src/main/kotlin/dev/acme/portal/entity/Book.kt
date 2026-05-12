package dev.acme.portal.entity

import dev.quatrion.portal.annotation.*
import dev.quatrion.portal.base.AuditableEntity
import dev.quatrion.portal.model.ActionResult
import dev.quatrion.portal.model.EntityData
import io.quarkus.arc.Unremovable


import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.*
// ─── Enums ───────────────────────────────────────────────────────────────────
enum class BookStatus { AVAILABLE, BORROWED, RESERVED, ARCHIVED }
enum class BookTag { BESTSELLER, NEW_ARRIVAL, RECOMMENDED, CLASSIC, AWARD_WINNER, CHILDRENS }
// ─── Zakładki formularza Książki ─────────────────────────────────────────────
enum class BookTab(
    override val label: String,
    override val labelKey: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Podstawowe", "tab.basic", "book", 0),
    DETAILS("Szczegóły", "tab.details", "info", 1),
    RELATIONS("Powiązania", "tab.relations", "link-2", 2)
}
// ─── Modele formularzy akcji ─────────────────────────────────────────────────
data class NotifyReadersForm(
    @field:PortalFormField(
        label = "Temat powiadomienia",
        renderer = RendererType.TEXT,
        required = true,
        placeholder = "np. Książka dostępna ponownie",
        order = 1
    )
    val subject: String = "",
    @field:PortalFormField(
        label = "Treść wiadomości",
        renderer = RendererType.TEXTAREA,
        required = true,
        placeholder = "Wpisz treść powiadomienia...",
        order = 2
    )
    val message: String = "",
    @field:PortalFormField(
        label = "Kanał wysyłki",
        renderer = RendererType.SELECT,
        selectOptions = ["EMAIL", "SMS", "PUSH"],
        required = true,
        order = 3
    )
    val channel: String = "EMAIL"
)
// ─── Handlery akcji ──────────────────────────────────────────────────────────
//  Plain CDI beans — no interface required.
//  Methods are invoked via KClass.memberFunctions + callSuspend.
@ApplicationScoped
@Unremovable
class ArchiveBookHandler {
    suspend fun validate(entity: dev.acme.portal.entity.Book, formData: EntityData?): String? {
        return if (entity.status == BookStatus.ARCHIVED) "Książka jest już zarchiwizowana" else null
    }
    suspend fun execute(entity: dev.acme.portal.entity.Book, formData: EntityData?): ActionResult {
        entity.status = BookStatus.ARCHIVED
        return ActionResult.Success("Książka #${entity.id} została zarchiwizowana.", refreshTable = true)
    }
}
@ApplicationScoped
@Unremovable
class ExportBookPdfHandler {
    suspend fun validate(entity: dev.acme.portal.entity.Book, formData: EntityData?) = null
    suspend fun execute(entity: dev.acme.portal.entity.Book, formData: EntityData?): ActionResult {
        val pdfBytes = "PDF book ${entity.id}".toByteArray()
        return ActionResult.Download("ksiazka-${entity.id}.pdf", "application/pdf", pdfBytes)
    }
}
@ApplicationScoped
@Unremovable
class NotifyReadersHandler {
    suspend fun validate(entity: dev.acme.portal.entity.Book, formData: EntityData?) = null
    suspend fun execute(entity: dev.acme.portal.entity.Book, formData: EntityData?): ActionResult {
        val subject = formData?.get("subject") as? String ?: ""
        val channel = formData?.get("channel") as? String ?: "EMAIL"
        return ActionResult.Success("Powiadomienie '$subject' wyslane przez $channel.")
    }
    suspend fun executeBulk(entities: List<dev.acme.portal.entity.Book>, formData: EntityData?): ActionResult {
        val subject = formData?.get("subject") as? String ?: ""
        return ActionResult.Success(
            "Powiadomienie '$subject' wyslane dla ${entities.size} ksiazek.",
            refreshTable = false
        )
    }
}
// ─── Encja: Książka ────────────────────────────────────────────────────────
@Entity
@PortalEntity(
    label = "Książka",
    labelKey = "entity.book",
    module = "Library",
    group = "Katalog",
    groupKey = "group.catalog",
    icon = "book-open",
    order = 3,
    description = "Katalog wszystkich książek w bibliotece",
    tabs = BookTab::class,
    pageSize = 50,
    auditLog = true
)
@PortalSecurity(
    viewRoles = ["portal-user", "portal-admin"],
    editRoles = ["portal-admin"],
    deleteRoles = ["portal-admin"],
    actionRoles = ["portal-admin"]
)
@PortalAction(
    name = "archiveBook",
    label = "Archiwizuj",
    labelKey = "action.book.archive",
    icon = "archive",
    handler = ArchiveBookHandler::class,
    confirmMessage = "Czy na pewno zarchiwizowac te ksiazke?",
    variant = "destructive",
    order = 1
)
@PortalAction(
    name = "exportPdf",
    label = "Eksportuj PDF",
    labelKey = "action.book.exportPdf",
    icon = "file-down",
    handler = ExportBookPdfHandler::class,
    variant = "outline",
    order = 2
)
@PortalAction(
    name = "notifyReaders",
    label = "Powiadom czytelników",
    labelKey = "action.book.notifyReaders",
    icon = "bell",
    handler = NotifyReadersHandler::class,
    formModel = NotifyReadersForm::class,
    bulkAllowed = true,
    variant = "secondary",
    order = 3
)
class Book : AuditableEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", labelKey = "field.common.id", tab = "BASIC", order = 0, readonly = true, showInFilter = false, width = 80)
    var id: Long = 0
    @Column(length = 20, unique = true, nullable = false)
    @Regex(
        pattern = """^(97[89])?\d{9}[\dX]$""",
        message = "Nieprawidlowy format ISBN"
    )
    @PortalField(
        label = "ISBN", labelKey = "field.book.isbn",
        tab = "BASIC", order = 1, required = true,
        renderer = RendererType.TEXT, filterType = FilterType.EXACT,
        placeholder = "9788301234567",
        tooltip = "Unikalny numer identyfikacyjny ksiazki (ISBN-10 lub ISBN-13)"
    )
    var isbn: String = ""
    @Column(length = 255, nullable = false)
    @PortalField(
        label = "Tytuł", labelKey = "field.book.title",
        tab = "BASIC", order = 2, required = true,
        renderer = RendererType.TEXT, filterType = FilterType.CONTAINS,
        placeholder = "Tytuł książki",
        width = 300
    )
    var title: String = ""
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    @PortalField(
        label = "Status", labelKey = "field.book.status",
        tab = "BASIC", order = 3,
        renderer = RendererType.SELECT, filterType = FilterType.IN,
        selectEnum = BookStatus::class,
        defaultValue = "AVAILABLE"
    )
    var status: dev.acme.portal.entity.BookStatus = BookStatus.AVAILABLE
    @Column(nullable = false)
    @PortalField(
        label = "Dostępna w wypożyczalni", labelKey = "field.common.isActive",
        tab = "BASIC", order = 4,
        renderer = RendererType.BOOLEAN, filterType = FilterType.BOOLEAN,
        defaultValue = "true"
    )
    var isActive: Boolean = true
    @Column(length = 7)
    @PortalField(
        label = "Kolor okładki", labelKey = "field.book.coverColor",
        tab = "BASIC", order = 5,
        renderer = RendererType.COLOR, filterType = FilterType.NONE,
        showInFilter = false,
        tooltip = "Kolor dominujący okładki (hex)",
        defaultValue = "#E5E7EB"
    )
    var coverColor: String = "#E5E7EB"
    // ─── Zakładka DETAILS ────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Opis / streszczenie", labelKey = "field.common.description",
        tab = "DETAILS", order = 1,
        renderer = RendererType.TEXTAREA, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        placeholder = "Streszczenie lub opis fabularny..."
    )
    @PortalDependency(
        field = "status",
        operator = DependencyOperator.EQ,
        value = "ARCHIVED",
        visibility = DependencyVisibility.HIDE,
        message = "Zarchiwizowane książki nie wymagają opisu"
    )
    var description: String = ""
    @Column
    @PortalField(
        label = "Liczba stron", labelKey = "field.book.pageCount",
        tab = "DETAILS", order = 2,
        renderer = RendererType.NUMBER, filterType = FilterType.RANGE,
        min = 1.0, max = 9999.0,
        placeholder = "np. 320"
    )
    var pageCount: Int = 0
    @Column(nullable = false)
    @PortalField(
        label = "Cena wypożyczenia (zł/dzień)", labelKey = "field.book.dailyPrice",
        tab = "DETAILS", order = 3,
        renderer = RendererType.DECIMAL, filterType = FilterType.RANGE,
        min = 0.0, max = 99.99,
        placeholder = "0.00",
        tooltip = "Dzienna stawka wypożyczenia"
    )
    @PortalDependency(
        field = "status",
        operator = DependencyOperator.IN,
        values = ["RESERVED", "BORROWED"],
        min = "0.50",
        message = "Cena dla aktywnych wypożyczeń musi wynosić co najmniej 0,50 zł/dzień"
    )
    var dailyPrice: Double = 0.0
    @Column
    @PortalField(
        label = "Data wydania", labelKey = "field.book.publishedDate",
        tab = "DETAILS", order = 4,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        showInTable = false,
        tooltip = "Format: RRRR-MM-DD"
    )
    var publishedDate: String = ""
    @Column
    @PortalField(
        label = "Tagi", labelKey = "field.book.tags",
        tab = "DETAILS", order = 5,
        renderer = RendererType.MULTI_SELECT, filterType = FilterType.IN,
        selectEnum = BookTag::class,
        showInTable = false,
        tooltip = "Możesz wybrać kilka tagów jednocześnie"
    )
    @PortalDependency(
        field = "status",
        operator = DependencyOperator.EQ,
        value = "ARCHIVED",
        allowedValues = ["CLASSIC"],
        message = "Zarchiwizowane książki mogą mieć tylko tag CLASSIC"
    )
    var tags: String = ""
    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Metadane (JSON)", labelKey = "field.book.metadata",
        tab = "DETAILS", order = 6,
        renderer = RendererType.JSON, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        tooltip = "Dodatkowe metadane w formacie JSON (np. tłumacz, seria, wydanie)"
    )
    var metadata: String = ""
    // ─── Zakładka RELATIONS ──────────────────────────────────────────────────
    @Column
    @PortalField(
        label = "Autor", labelKey = "field.book.authorId",
        tab = "RELATIONS", order = 1,
        renderer = RendererType.RELATION, filterType = FilterType.EXACT,
        required = true,
        tooltip = "Autor lub współautor książki"
    )
    @PortalRelation(
        targetEntity = Author::class,
        editable = true,
        displayFields = ["firstName", "lastName", "email"],
        searchFields = ["firstName", "lastName"],
        createAllowed = true,
        labelField = "lastName",
        filterQuery = "e.isActive = true",
        maxResults = 100
    )
    var authorId: Long? = null
    @Column
    @PortalField(
        label = "Gatunek", labelKey = "field.book.genreId",
        tab = "RELATIONS", order = 2,
        renderer = RendererType.RELATION, filterType = FilterType.EXACT,
        tooltip = "Gatunek literacki z aktywnego słownika"
    )
    @PortalRelation(
        targetEntity = Genre::class,
        editable = true,
        displayFields = ["name", "abbreviation"],
        searchFields = ["name", "abbreviation"],
        orderBy = "name ASC",
        labelField = "name",
        filterQuery = "e.isActive = true",
        maxResults = 200
    )
    var genreId: Long? = null
}