package dev.acme.portal.entity

import dev.quatrion.portal.annotation.*
import dev.quatrion.portal.base.AuditableEntity

import jakarta.persistence.*

// ─── Zakładki formularza Autora ──────────────────────────────────────────────
enum class AuthorTab(
    override val label: String,
    override val labelKey: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Dane podstawowe", "tab.basic", "user", 0),
    CONTACT("Kontakt i media", "tab.contact", "link", 1),
    NOTES("Biografia", "tab.notes", "file-text", 2)
}

// ─── Encja: Autor ────────────────────────────────────────────────────────────
// Demonstracja: auditLog, zakładki, EMAIL + @Regex, URL, FILE, DATE,
// displayExpression, group (grupowanie pól), tooltip, placeholder, width,
// labelKey, defaultValue, CONTAINS/EXACT/RANGE/BOOLEAN FilterType
// ─────────────────────────────────────────────────────────────────────────────

@Entity
@PortalEntity(
    label = "Autor",
    labelKey = "entity.author",
    module = "Library",
    group = "Słowniki",
    groupKey = "group.dictionaries",
    icon = "pen-line",
    order = 2,
    description = "Autorzy książek dostępnych w bibliotece",
    tabs = AuthorTab::class,
    auditLog = true,
    pageSize = 25
)
class Author : AuditableEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", labelKey = "field.common.id", tab = "BASIC", order = 0, readonly = true, showInFilter = false, width = 80)
    var id: Long = 0

    @Column(length = 80, nullable = false)
    @PortalField(
        label = "Imię", labelKey = "field.author.firstName",
        tab = "BASIC", order = 1, required = true,
        renderer = RendererType.TEXT, filterType = FilterType.CONTAINS,
        placeholder = "Imię autora", group = "Dane osobowe", width = 200
    )
    var firstName: String = ""

    @Column(length = 100, nullable = false)
    @PortalField(
        label = "Nazwisko", labelKey = "field.author.lastName",
        tab = "BASIC", order = 2, required = true,
        renderer = RendererType.TEXT, filterType = FilterType.CONTAINS,
        placeholder = "Nazwisko autora", group = "Dane osobowe", width = 200
    )
    var lastName: String = ""

    @Transient
    @PortalField(
        label = "Pełne imię i nazwisko", labelKey = "field.author.fullName",
        tab = "BASIC", order = 3,
        displayExpression = "\${firstName} \${lastName}",
        readonly = true, showInFilter = false,
        group = "Dane osobowe", width = 250
    )
    var fullName: String = ""

    @Column
    @PortalField(
        label = "Data urodzenia", labelKey = "field.author.birthDate",
        tab = "BASIC", order = 4,
        renderer = RendererType.DATE, filterType = FilterType.RANGE,
        showInTable = false, tooltip = "Format: RRRR-MM-DD",
        group = "Dane osobowe"
    )
    var birthDate: String = ""

    @Column(nullable = false)
    @PortalField(
        label = "Aktywny", labelKey = "field.common.isActive",
        tab = "BASIC", order = 5,
        renderer = RendererType.BOOLEAN, filterType = FilterType.BOOLEAN,
        defaultValue = "true"
    )
    var isActive: Boolean = true

    // ─── Zakładka CONTACT ───────────────────────────────────────────────────

    @Column(length = 200, unique = true)
    @Regex(
        pattern = """^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$""",
        message = "Podaj poprawny adres e-mail"
    )
    @PortalField(
        label = "E-mail", labelKey = "field.common.email",
        tab = "CONTACT", order = 1,
        renderer = RendererType.EMAIL, filterType = FilterType.EXACT,
        placeholder = "autor@example.com",
        tooltip = "Adres e-mail do kontaktu z autorem"
    )
    var email: String = ""

    @Column(length = 300)
    @PortalField(
        label = "Strona internetowa", labelKey = "field.author.website",
        tab = "CONTACT", order = 2,
        renderer = RendererType.URL, filterType = FilterType.NONE,
        showInFilter = false, placeholder = "https://",
        tooltip = "Oficjalna strona lub profil autora"
    )
    var website: String = ""

    @Column(length = 500)
    @PortalField(
        label = "Zdjęcie (URL / ścieżka)", labelKey = "field.author.photoUrl",
        tab = "CONTACT", order = 3,
        renderer = RendererType.FILE, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        tooltip = "Zdjęcie profilowe autora"
    )
    var photoUrl: String = ""

    // ─── Zakładka NOTES ─────────────────────────────────────────────────────

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Biografia", labelKey = "field.author.bio",
        tab = "NOTES", order = 1,
        renderer = RendererType.TEXTAREA, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        placeholder = "Krótka notka biograficzna autora..."
    )
    var bio: String = ""

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Notatki wewnętrzne", labelKey = "field.common.internalNotes",
        tab = "NOTES", order = 2,
        renderer = RendererType.TEXTAREA, filterType = FilterType.NONE,
        showInTable = false, showInFilter = false,
        placeholder = "Uwagi tylko do wewnętrznego użytku...",
        tooltip = "Pole widoczne wyłącznie dla redaktorów"
    )
    var internalNotes: String = ""
}
