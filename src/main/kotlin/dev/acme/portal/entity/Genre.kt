package dev.acme.portal.entity

import dev.quatrion.portal.annotation.*
import dev.quatrion.portal.base.AuditableEntity

import jakarta.persistence.*

// ─── Encja: Gatunek literacki ────────────────────────────────────────────────
// Demonstracja: COLOR renderer, samoreferencja (RELATION), softDelete,
// allowDelete=false, @PortalLookup z filterQuery + maxResults, płaski formularz
// FilterType: CONTAINS, STARTS_WITH, BOOLEAN, EXACT
// ─────────────────────────────────────────────────────────────────────────────

@Entity
@PortalEntity(
    label = "Gatunek",
    labelKey = "entity.genre",
    module = "Library",
    group = "Słowniki",
    groupKey = "group.dictionaries",
    icon = "bookmark",
    order = 1,
    description = "Słownik gatunków literackich — może być hierarchiczny (gatunek nadrzędny)",
    allowDelete = false,
    softDelete = true,
    pageSize = 50
)
class Genre : AuditableEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(
        label = "ID", labelKey = "field.common.id",
        order = 0,
        readonly = true,
        showInFilter = false,
        width = 80
    )
    var id: Long = 0

    @Column(length = 100, nullable = false, unique = true)
    @PortalField(
        label = "Nazwa gatunku",
        labelKey = "field.genre.name",
        order = 1,
        required = true,
        renderer = RendererType.TEXT,
        filterType = FilterType.CONTAINS,
        placeholder = "np. Fantastyka naukowa",
        tooltip = "Unikalna nazwa gatunku literackiego"
    )
    var name: String = ""

    @Column(length = 50)
    @PortalField(
        label = "Skrót",
        labelKey = "field.genre.abbreviation",
        order = 2,
        renderer = RendererType.TEXT,
        filterType = FilterType.STARTS_WITH,
        placeholder = "np. SF",
        width = 120,
        tooltip = "Skrótowa nazwa wyświetlana w tagach"
    )
    var abbreviation: String = ""

    @Column(length = 7)
    @PortalField(
        label = "Kolor",
        labelKey = "field.genre.color",
        order = 3,
        renderer = RendererType.COLOR,
        filterType = FilterType.NONE,
        showInFilter = false,
        tooltip = "Kolor etykiety gatunku w interfejsie (hex, np. #3B82F6)",
        defaultValue = "#6366F1"
    )
    var color: String = "#6366F1"

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Opis",
        labelKey = "field.common.description",
        order = 4,
        renderer = RendererType.TEXTAREA,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false,
        placeholder = "Krótki opis gatunku literackiego..."
    )
    var description: String = ""

    @Column(nullable = false)
    @PortalField(
        label = "Aktywny",
        labelKey = "field.common.isActive",
        order = 5,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.BOOLEAN,
        defaultValue = "true"
    )
    var isActive: Boolean = true

    // Samoreferencja: gatunek nadrzędny (np. "Fantastyka" → "Fantastyka naukowa")
    @Column
    @PortalField(
        label = "Gatunek nadrzędny",
        labelKey = "field.genre.parentId",
        order = 6,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT,
        showInTable = false,
        showInFilter = false,
        tooltip = "Opcjonalny gatunek-rodzic tworzy hierarchię"
    )
    @PortalRelation(
        targetEntity = Genre::class,
        editable = true,
        displayFields = ["name", "abbreviation"],
        searchFields = ["name", "abbreviation"],
        labelField = "name",
        filterQuery = "e.isActive = true",
        maxResults = 200
    )
    var parentId: Long? = null

    // Wymagane dla softDelete = true w @PortalEntity
    @Column(nullable = false)
    @PortalField(label = "Usunięty", hidden = true)
    var deleted: Boolean = false
}
