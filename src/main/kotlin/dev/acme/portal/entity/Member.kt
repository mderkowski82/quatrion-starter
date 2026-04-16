package dev.acme.portal.entity

import dev.quatrion.portal.annotation.*
import dev.quatrion.portal.model.ActionResult
import dev.quatrion.portal.model.EntityData
import io.quarkus.arc.Unremovable
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheEntityBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.*
// ─── Enums ───────────────────────────────────────────────────────────────────

enum class MembershipType { STANDARD, PREMIUM, VIP }

enum class MemberTag { VIP, REGULAR, STUDENT, SENIOR, STAFF }

// ─── Zakładki formularza Czytelnika ──────────────────────────────────────────
enum class MemberTab(
    override val label: String,
    override val labelKey: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Dane podstawowe", "tab.basic", "user", 0),
    CONTACT("Kontakt", "tab.contact", "phone", 1),
    MEMBERSHIP("Karta biblioteczna", "tab.membership", "credit-card", 2),
    LOANS("Historia wypożyczeń", "tab.loans", "book-copy", 3)
}

// ─── Modele formularzy akcji ─────────────────────────────────────────────────

data class SendWelcomeEmailForm(
    @field:PortalFormField(
        label = "Temat wiadomości",
        renderer = RendererType.TEXT,
        required = true,
        placeholder = "Witamy w bibliotece!",
        order = 1
    )
    val subject: String = "Witamy w Bibliotece Miejskiej",

    @field:PortalFormField(
        label = "Treść wiadomości",
        renderer = RendererType.TEXTAREA,
        required = true,
        placeholder = "Treść powitalnej wiadomości e-mail...",
        order = 2
    )
    val body: String = "",

    @field:PortalFormField(
        label = "Wyślij kopię do administratora",
        renderer = RendererType.SELECT,
        selectOptions = ["TAK", "NIE"],
        order = 3
    )
    val sendCopy: String = "NIE"
)

// ─── Handlery akcji ──────────────────────────────────────────────────────────

@ApplicationScoped
@Unremovable
class ActivateMemberHandler {
    suspend fun validate(entity: dev.acme.portal.entity.Member, formData: EntityData?): String? {
        return if (entity.isActive) "Czytelnik jest już aktywny" else null
    }

    suspend fun execute(entity: dev.acme.portal.entity.Member, formData: EntityData?): ActionResult {
        return ActionResult.Success("Konto czytelnika #${entity.id} zostało aktywowane.", refreshTable = true)
    }
}

@ApplicationScoped
@Unremovable
class SendWelcomeEmailHandler {
    suspend fun validate(entity: dev.acme.portal.entity.Member, formData: EntityData?): String? {
        return if (entity.email.isBlank()) "Czytelnik nie ma przypisanego adresu e-mail" else null
    }

    suspend fun execute(entity: dev.acme.portal.entity.Member, formData: EntityData?): ActionResult {
        val subject = formData?.get("subject") as? String ?: ""
        return ActionResult.Success("Wiadomość powitalna '$subject' wysłana na ${entity.email}.")
    }
}

@ApplicationScoped
@Unremovable
class ExportMembersHandler {
    suspend fun validate(entity: dev.acme.portal.entity.Member, formData: EntityData?) = null

    suspend fun execute(entity: dev.acme.portal.entity.Member, formData: EntityData?): ActionResult {
        val csvBytes = "id,firstName,lastName,email\n${entity.id},${entity.firstName},${entity.lastName},${entity.email}".toByteArray()
        return ActionResult.Download("czytelnicy-eksport.csv", "text/csv", csvBytes)
    }

    suspend fun executeBulk(entities: List<dev.acme.portal.entity.Member>, formData: EntityData?): ActionResult {
        val header = "id,firstName,lastName,email,membershipType\n"
        val rows = entities.joinToString("\n") { e ->
            "${e.id},${e.firstName},${e.lastName},${e.email},${e.membershipType}"
        }
        val csvBytes = (header + rows).toByteArray()
        return ActionResult.Download(
            "czytelnicy-eksport-${entities.size}.csv",
            "text/csv",
            csvBytes
        )
    }
}

// ─── Encja: Czytelnik ─────────────────────────────────────────────────────────
// Demonstracja: softDelete + auditLog, 4 zakładki, PASSWORD, FILE, RELATION_LIST,
// @PortalAction z formModel (SendWelcomeEmailForm), bulkAllowed (eksport CSV),
// @PortalDependency: RANGE (maxLoans per membershipType), allowedValues (tags),
// SHOW warunkowy, @PortalSecurity, @Regex dla telefonu,
// FilterType: CONTAINS, EXACT, STARTS_WITH, RANGE, IN, BOOLEAN
// ─────────────────────────────────────────────────────────────────────────────

@Entity
@PortalEntity(
    label = "Czytelnik",
    labelKey = "entity.member",
    module = "Library",
    group = "Użytkownicy",
    groupKey = "group.users",
    icon = "users",
    description = "Zarejestrowani czytelnicy biblioteki — mogą wypożyczać książki",
    tabs = _root_ide_package_.dev.acme.portal.entity.MemberTab::class,
    softDelete = true,
    auditLog = true,
    pageSize = 25
)
@PortalSecurity(
    viewRoles = ["portal-user", "portal-admin"],
    editRoles = ["portal-admin"],
    deleteRoles = ["portal-admin"],
    actionRoles = ["portal-admin"]
)
@PortalAction(
    name = "activate",
    label = "Aktywuj konto",
    labelKey = "action.member.activate",
    icon = "check-circle",
    handler = _root_ide_package_.dev.acme.portal.entity.ActivateMemberHandler::class,
    confirmMessage = "Czy aktywować konto tego czytelnika?",
    variant = "default",
    order = 1
)
@PortalAction(
    name = "sendWelcomeEmail",
    label = "Wyślij wiadomość powitalną",
    labelKey = "action.member.sendWelcomeEmail",
    icon = "mail",
    handler = _root_ide_package_.dev.acme.portal.entity.SendWelcomeEmailHandler::class,
    formModel = _root_ide_package_.dev.acme.portal.entity.SendWelcomeEmailForm::class,
    variant = "outline",
    order = 2
)
@PortalAction(
    name = "exportMembers",
    label = "Eksportuj do CSV",
    labelKey = "action.member.exportCsv",
    icon = "download",
    handler = _root_ide_package_.dev.acme.portal.entity.ExportMembersHandler::class,
    bulkAllowed = true,
    variant = "secondary",
    order = 3
)
class Member: PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", labelKey = "field.common.id", tab = "BASIC", order = 0, readonly = true, showInFilter = false, width = 80)
    var id: Long = 0

    @Column(length = 80, nullable = false)
    @PortalField(
        label = "Imię", labelKey = "field.member.firstName",
        tab = "BASIC", order = 1, required = true,
        renderer = RendererType.TEXT, filterType = FilterType.CONTAINS,
        placeholder = "Imię czytelnika",
        group = "Dane osobowe"
    )
    var firstName: String = ""

    @Column(length = 100, nullable = false)
    @PortalField(
        label = "Nazwisko", labelKey = "field.member.lastName",
        tab = "BASIC", order = 2, required = true,
        renderer = RendererType.TEXT, filterType = FilterType.CONTAINS,
        placeholder = "Nazwisko czytelnika",
        group = "Dane osobowe"
    )
    var lastName: String = ""

    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    @PortalField(
        label = "Typ członkostwa", labelKey = "field.member.membershipType",
        tab = "BASIC", order = 3,
        renderer = RendererType.SELECT, filterType = FilterType.IN,
        selectEnum = _root_ide_package_.dev.acme.portal.entity.MembershipType::class,
        defaultValue = "STANDARD",
        tooltip = "Typ karty bibliotecznej — wpływa na limity i uprawnienia"
    )
    var membershipType: dev.acme.portal.entity.MembershipType = _root_ide_package_.dev.acme.portal.entity.MembershipType.STANDARD

    @Column(nullable = false)
    @PortalField(
        label = "Konto aktywne", labelKey = "field.common.isActive",
        tab = "BASIC", order = 4,
        renderer = RendererType.BOOLEAN, filterType = FilterType.BOOLEAN,
        defaultValue = "true"
    )
    var isActive: Boolean = true

    // ─── Zakładka CONTACT ────────────────────────────────────────────────────

    @Column(length = 200, unique = true, nullable = false)
    @Regex(
        pattern = """^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$""",
        message = "Podaj poprawny adres e-mail"
    )
    @PortalField(
        label = "E-mail", labelKey = "field.common.email",
        tab = "CONTACT", order = 1, required = true,
        renderer = RendererType.EMAIL, filterType = FilterType.EXACT,
        placeholder = "czytelnik@example.com"
    )
    var email: String = ""

    @Column(length = 20)
    @Regex(
        pattern = """^\+?[\d\s\-]{7,20}$""",
        message = "Numer telefonu może zawierać cyfry, spacje, myślniki i opcjonalny znak +"
    )
    @PortalField(
        label = "Telefon", labelKey = "field.member.phone",
        tab = "CONTACT", order = 2,
        renderer = RendererType.TEXT, filterType = FilterType.STARTS_WITH,
        placeholder = "+48 123 456 789",
        tooltip = "Numer kontaktowy (opcjonalny)"
    )
    var phone: String = ""

    @Column(length = 500)
    @PortalField(
        label = "Zdjęcie profilowe", labelKey = "field.member.avatarUrl",
        tab = "CONTACT", order = 3,
        renderer = RendererType.FILE, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false
    )
    var avatarUrl: String = ""

    @Column(length = 200)
    @PortalField(
        label = "Hasło (hash)", labelKey = "field.member.passwordHash",
        tab = "CONTACT", order = 4,
        renderer = RendererType.PASSWORD, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        tooltip = "Hasło przechowywane w postaci zahaszowanej"
    )
    var passwordHash: String = ""

    // ─── Zakładka MEMBERSHIP ─────────────────────────────────────────────────

    @Column
    @PortalField(
        label = "Data rejestracji", labelKey = "field.member.registrationDate",
        tab = "MEMBERSHIP", order = 1,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        readonly = true,
        tooltip = "Data pierwszej rejestracji w bibliotece"
    )
    var registrationDate: String = ""

    @Column
    @PortalField(
        label = "Data ważności karty", labelKey = "field.member.expiryDate",
        tab = "MEMBERSHIP", order = 2,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        tooltip = "Po tej dacie karta wymaga odnowienia"
    )
    var expiryDate: String = ""

    @Column(nullable = false)
    @PortalField(
        label = "Maks. liczba wypożyczeń jednocześnie", labelKey = "field.member.maxLoans",
        tab = "MEMBERSHIP", order = 3,
        renderer = RendererType.NUMBER, filterType = FilterType.RANGE,
        min = 1.0, max = 50.0,
        tooltip = "Limit aktywnych wypożyczeń zależny od typu członkostwa"
    )
    // STANDARD: max 3 wypożyczenia jednocześnie
    @PortalDependency(
        field = "membershipType",
        operator = DependencyOperator.EQ,
        value = "STANDARD",
        max = "3",
        message = "Karta STANDARD pozwala na maksymalnie 3 wypożyczenia jednocześnie"
    )
    // PREMIUM: max 10 wypożyczeń
    @PortalDependency(
        field = "membershipType",
        operator = DependencyOperator.EQ,
        value = "PREMIUM",
        min = "1",
        max = "10"
    )
    // VIP: max 50 wypożyczeń
    @PortalDependency(
        field = "membershipType",
        operator = DependencyOperator.EQ,
        value = "VIP",
        min = "1",
        max = "50"
    )
    var maxLoans: Int = 3

    @Column
    @PortalField(
        label = "Tagi czytelnika", labelKey = "field.member.tags",
        tab = "MEMBERSHIP", order = 4,
        renderer = RendererType.MULTI_SELECT, filterType = FilterType.IN,
        selectEnum = _root_ide_package_.dev.acme.portal.entity.MemberTag::class,
        showInTable = false,
        tooltip = "Kategorie czytelnika"
    )
    // Dla STANDARD — tylko REGULAR i STUDENT
    @PortalDependency(
        field = "membershipType",
        operator = DependencyOperator.EQ,
        value = "STANDARD",
        allowedValues = ["REGULAR", "STUDENT", "SENIOR"],
        message = "Karta STANDARD obsługuje tagi: REGULAR, STUDENT, SENIOR"
    )
    // Dla VIP — dostęp do wszystkich tagów
    @PortalDependency(
        field = "membershipType",
        operator = DependencyOperator.EQ,
        value = "VIP",
        allowedValues = ["VIP", "REGULAR", "STUDENT", "SENIOR", "STAFF"]
    )
    var tags: String = ""

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Uwagi bibliotekarza", labelKey = "field.common.internalNotes",
        tab = "MEMBERSHIP", order = 5,
        renderer = RendererType.TEXTAREA, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        placeholder = "Wewnętrzne notatki...",
        tooltip = "Widoczne tylko dla bibliotekarzy"
    )
    // Pokaż pole uwag tylko gdy konto jest aktywne
    @PortalDependency(
        field = "isActive",
        operator = DependencyOperator.EQ,
        value = "true",
        visibility = DependencyVisibility.SHOW
    )
    var notes: String = ""

    // ─── Zakładka LOANS — RELATION_LIST (historia wypożyczeń) ────────────────

    @Transient
    @PortalField(
        label = "Historia wypożyczeń", labelKey = "field.member.loans",
        tab = "LOANS", order = 1,
        renderer = RendererType.RELATION_LIST,
        filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        tooltip = "Lista wszystkich wypożyczeń powiązanych z tym czytelnikiem"
    )
    @PortalRelation(
        targetEntity = _root_ide_package_.dev.acme.portal.entity.Loan::class,
        editable = false,
        displayFields = ["bookTitle", "loanDate", "dueDate", "returnDate", "status"],
        searchFields = ["status"],
        cascadeDelete = true,
        orderBy = "loanDate DESC",
        maxItems = 200
    )
    @PortalLookup(
        labelField = "status",
        valueField = "id",
        parentField = "memberId",
        maxResults = 200
    )
    var loans: List<dev.acme.portal.entity.Loan>? = null

    @Column(nullable = false)
    @PortalField(label = "Usunięty", hidden = true)
    var deleted: Boolean = false
}



