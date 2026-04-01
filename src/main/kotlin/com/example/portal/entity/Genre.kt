package com.example.portal.entity

import dev.quatrion.portal.annotation.*
import jakarta.persistence.*

// ─── Encja: Gatunek literacki ────────────────────────────────────────────────
// Demonstracja: COLOR renderer, samoreferencja (RELATION), softDelete,
// allowDelete=false, @PortalLookup z filterQuery + maxResults, płaski formularz
// FilterType: CONTAINS, STARTS_WITH, BOOLEAN, EXACT
// ─────────────────────────────────────────────────────────────────────────────

@Entity
@Table(name = "genre")
@PortalEntity(
    label = "Gatunek",
    module = "Library",
    group = "Słowniki",
    icon = "bookmark",
    order = 1,
    description = "Słownik gatunków literackich — może być hierarchiczny (gatunek nadrzędny)",
    allowDelete = false,
    softDelete = true,
    pageSize = 50
)
class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(
        label = "ID",
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
        searchFields = ["name", "abbreviation"]
    )
    @PortalLookup(
        labelField = "name",
        valueField = "id",
        filterQuery = "e.isActive = true",
        maxResults = 200
    )
    var parentId: Long? = null

    // Wymagane dla softDelete = true w @PortalEntity
    @Column(nullable = false)
    @PortalField(label = "Usunięty", hidden = true)
    var deleted: Boolean = false
}

