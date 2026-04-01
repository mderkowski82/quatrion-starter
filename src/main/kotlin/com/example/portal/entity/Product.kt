package com.example.portal.entity

import dev.quatrion.portal.annotation.*
import jakarta.persistence.*

enum class ProductCategory { ELECTRONICS, CLOTHING, FOOD, OTHER }

@Entity @Table(name = "product")
@PortalEntity(label = "Produkt", module = "Catalog", icon = "package", order = 1)
class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", order = 0, readonly = true, showInFilter = false)
    var id: Long = 0

    @Column(length = 100, nullable = false)
    @PortalField(label = "Nazwa", labelKey = "field.product.name", order = 1, required = true,
        renderer = RendererType.TEXT, filterType = FilterType.CONTAINS)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    @PortalField(label = "Opis", labelKey = "field.product.description", order = 2,
        renderer = RendererType.TEXTAREA, showInTable = false)
    var description: String = ""

    @Column
    @PortalField(label = "Cena", labelKey = "field.product.price", order = 3,
        renderer = RendererType.DECIMAL, filterType = FilterType.RANGE, min = 0.0)
    var price: Double = 0.0

    @Column(length = 20) @Enumerated(EnumType.STRING)
    @PortalField(label = "Kategoria", order = 4, renderer = RendererType.SELECT,
        selectEnum = ProductCategory::class, filterType = FilterType.IN)
    var category: ProductCategory? = null

    @Column
    @PortalField(label = "Aktywny", order = 5, renderer = RendererType.BOOLEAN, filterType = FilterType.BOOLEAN)
    var active: Boolean = true
}

