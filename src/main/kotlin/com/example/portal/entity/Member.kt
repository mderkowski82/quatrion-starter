package com.example.portal.entity

import dev.quatrion.portal.annotation.*
import dev.quatrion.portal.model.ActionHandler
import dev.quatrion.portal.model.ActionResult
import dev.quatrion.portal.model.EntityData
import io.quarkus.arc.Unremovable
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.*
// ─── Enums ───────────────────────────────────────────────────────────────────

enum class MembershipType { STANDARD, PREMIUM, VIP }

enum class MemberTag { VIP, REGULAR, STUDENT, SENIOR, STAFF }

// ─── Zakładki formularza Czytelnika ──────────────────────────────────────────
enum class MemberTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Dane podstawowe", "user", 0),
    CONTACT("Kontakt", "phone", 1),
    MEMBERSHIP("Karta biblioteczna", "credit-card", 2),
    LOANS("Historia wypożyczeń", "book-copy", 3)
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
class ActivateMemberHandler : ActionHandler<EntityData> {
    override val actionName = "activate"

    override suspend fun validate(entity: EntityData, formData: EntityData?): String? {
        val isActive = entity["isActive"] as? Boolean ?: false
        return if (isActive) "Czytelnik jest już aktywny" else null
    }

    override suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val id = entity["id"]
        // logika: aktywacja konta czytelnika
        return ActionResult.Success("Konto czytelnika #$id zostało aktywowane.", refreshTable = true)
    }
}

@ApplicationScoped
@Unremovable
class SendWelcomeEmailHandler : ActionHandler<EntityData> {
    override val actionName = "sendWelcomeEmail"

    override suspend fun validate(entity: EntityData, formData: EntityData?): String? {
        val email = entity["email"] as? String
        return if (email.isNullOrBlank()) "Czytelnik nie ma przypisanego adresu e-mail" else null
    }

    override suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val subject = formData?.get("subject") as? String ?: ""
        val email = entity["email"] as? String ?: ""
        // logika: wysyłka wiadomości powitalnej
        return ActionResult.Success("Wiadomość powitalna '$subject' wysłana na $email.")
    }
}

@ApplicationScoped
@Unremovable
class ExportMembersHandler : ActionHandler<EntityData> {
    override val actionName = "exportMembers"

    override suspend fun validate(entity: EntityData, formData: EntityData?) = null

    override suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val id = entity["id"]
        val csvBytes = "id,name,email\n$id,...,...".toByteArray()
        return ActionResult.Download("czytelnicy-eksport.csv", "text/csv", csvBytes)
    }

    override suspend fun executeBulk(entities: List<EntityData>, formData: EntityData?): ActionResult {
        val header = "id,firstName,lastName,email,membershipType\n"
        val rows = entities.joinToString("\n") { e ->
            "${e["id"]},${e["firstName"]},${e["lastName"]},${e["email"]},${e["membershipType"]}"
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
@Table(name = "member")
@PortalEntity(
    label = "Czytelnik",
    module = "Library",
    group = "Użytkownicy",
    icon = "users",
    order = 4,
    description = "Zarejestrowani czytelnicy biblioteki — mogą wypożyczać książki",
    tabs = MemberTab::class,
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
    icon = "check-circle",
    handler = ActivateMemberHandler::class,
    confirmMessage = "Czy aktywować konto tego czytelnika?",
    variant = "default",
    order = 1
)
@PortalAction(
    name = "sendWelcomeEmail",
    label = "Wyślij wiadomość powitalną",
    icon = "mail",
    handler = SendWelcomeEmailHandler::class,
    formModel = SendWelcomeEmailForm::class,
    variant = "outline",
    order = 2
)
@PortalAction(
    name = "exportMembers",
    label = "Eksportuj do CSV",
    icon = "download",
    handler = ExportMembersHandler::class,
    bulkAllowed = true,
    variant = "secondary",
    order = 3
)
class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", tab = "BASIC", order = 0, readonly = true, showInFilter = false, width = 80)
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
        label = "Typ członkostwa",
        tab = "BASIC", order = 3,
        renderer = RendererType.SELECT, filterType = FilterType.IN,
        selectEnum = MembershipType::class,
        defaultValue = "STANDARD",
        tooltip = "Typ karty bibliotecznej — wpływa na limity i uprawnienia"
    )
    var membershipType: MembershipType = MembershipType.STANDARD

    @Column(nullable = false)
    @PortalField(
        label = "Konto aktywne",
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
        label = "E-mail", tab = "CONTACT", order = 1, required = true,
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
        label = "Telefon", tab = "CONTACT", order = 2,
        renderer = RendererType.TEXT, filterType = FilterType.STARTS_WITH,
        placeholder = "+48 123 456 789",
        tooltip = "Numer kontaktowy (opcjonalny)"
    )
    var phone: String = ""

    @Column(length = 500)
    @PortalField(
        label = "Zdjęcie profilowe",
        tab = "CONTACT", order = 3,
        renderer = RendererType.FILE, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false
    )
    var avatarUrl: String = ""

    @Column(length = 200)
    @PortalField(
        label = "Hasło (hash)",
        tab = "CONTACT", order = 4,
        renderer = RendererType.PASSWORD, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        tooltip = "Hasło przechowywane w postaci zahaszowanej"
    )
    var passwordHash: String = ""

    // ─── Zakładka MEMBERSHIP ─────────────────────────────────────────────────

    @Column
    @PortalField(
        label = "Data rejestracji",
        tab = "MEMBERSHIP", order = 1,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        readonly = true,
        tooltip = "Data pierwszej rejestracji w bibliotece"
    )
    var registrationDate: String = ""

    @Column
    @PortalField(
        label = "Data ważności karty",
        tab = "MEMBERSHIP", order = 2,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        tooltip = "Po tej dacie karta wymaga odnowienia"
    )
    var expiryDate: String = ""

    @Column(nullable = false)
    @PortalField(
        label = "Maks. liczba wypożyczeń jednocześnie",
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
        label = "Tagi czytelnika",
        tab = "MEMBERSHIP", order = 4,
        renderer = RendererType.MULTI_SELECT, filterType = FilterType.IN,
        selectEnum = MemberTag::class,
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
        label = "Uwagi bibliotekarza",
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
        label = "Historia wypożyczeń",
        tab = "LOANS", order = 1,
        renderer = RendererType.RELATION_LIST,
        filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        tooltip = "Lista wszystkich wypożyczeń powiązanych z tym czytelnikiem"
    )
    @PortalRelation(
        editable = false,
        displayFields = ["bookId", "loanDate", "dueDate", "returnDate", "status"],
        searchFields = ["status"],
        cascadeDelete = true,
        orderBy = "loanDate DESC",
        maxItems = 200
    )
    @PortalLookup(
        labelField = "status",
        valueField = "id",
        filterQuery = "e.memberId = e.memberId",
        maxResults = 200
    )
    var loans: List<Loan>? = null

    // Wymagane dla softDelete = true w @PortalEntity
    @Column(nullable = false)
    @PortalField(label = "Usunięty", hidden = true)
    var deleted: Boolean = false
}




