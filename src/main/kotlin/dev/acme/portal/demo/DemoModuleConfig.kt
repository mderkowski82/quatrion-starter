package dev.acme.portal.demo

import dev.acme.portal.entity.Loan
import dev.quatrion.portal.config.EntityRef
import dev.quatrion.portal.config.ModuleDef
import dev.quatrion.portal.config.PortalModuleConfig
import dev.quatrion.portal.dashboard.DashboardWidget
import dev.acme.portal.entity.Author
import dev.acme.portal.entity.Book
import dev.acme.portal.entity.Genre
import dev.acme.portal.entity.Member
import dev.acme.portal.task.LoanHistoryCsvTask
import dev.quatrion.portal.task.TaskRun
import dev.quatrion.portal.task.TaskRunFile
import jakarta.enterprise.context.ApplicationScoped

/**
 * Demo module configuration — registers all demo entities across two modules
 * to exercise every UI feature: module switching, groups, ungrouped entities,
 * all field types, all relation types, tabs, filters, and actions.
 *
 * Providing an @ApplicationScoped bean that extends [PortalModuleConfig] is all
 * that is required — [dev.quatrion.portal.service.MetadataService] and
 * [dev.quatrion.portal.service.EntityRegistry] resolve it via CDI Instance and
 * automatically fall back to an empty default when no user bean is registered.
 */
@ApplicationScoped
class DemoModuleConfig : PortalModuleConfig() {

    override fun modules() = listOf(
        libraryModule(),
        crmModule(),
        catalogModule(),
        taskModule(),
        systemModule()
    )

    private fun libraryModule() =         ModuleDef(
        name = "Library",
        label = "Biblioteka",
        labelKey = "module.library",
        icon = "book-open",
        order = 1,
        defaultEntity = _root_ide_package_.dev.acme.portal.entity.Book::class.java,
        entities = listOf(
            // Słowniki — encje pomocnicze bez zakładek (płaskie formularze)
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.entity.Genre::class.java,  group = "Słowniki",   order = 1),
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.entity.Author::class.java, group = "Słowniki",   order = 2),
            // Katalog — centralna encja z zakładkami, akcjami i security
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.entity.Book::class.java,   group = "Katalog",    order = 1),
            // Użytkownicy i operacje
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.entity.Member::class.java, group = "Użytkownicy", order = 1),
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.entity.Loan::class.java,   group = "Operacje",   order = 1)
        )
    )

    /**
     * CRM module — demonstrates:
     * - Two groups ("Słowniki", "Klienci") plus an ungrouped entity (DemoOrder)
     * - Entity with all 17 renderer types (DemoCustomer)
     * - RELATION_LIST (DemoOrder → items)
     * - Multiple @PortalAction handlers with and without confirmation
     * - All FilterType values
     */
    private fun crmModule() = ModuleDef(
        name = "CRM",
        label = "CRM",
        labelKey = "module.crm",
        icon = "users",
        order = 1,
        defaultEntity = _root_ide_package_.dev.acme.portal.demo.DemoCustomer::class.java,
        entities = listOf(
            // ── Group: Słowniki ──────────────────────────────
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.demo.DemoCountry::class.java,  group = "Słowniki", order = 1),
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.demo.DemoCategory::class.java, group = "Słowniki", order = 2),

            // ── Group: Klienci ───────────────────────────────
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.demo.DemoCustomer::class.java, group = "Klienci",  order = 1),

            // ── Ungrouped ────────────────────────────────────
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.demo.DemoOrder::class.java,     order = 10),
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.demo.DemoOrderItem::class.java, order = 11)
        )
    )

    /**
     * Katalog module — demonstrates:
     * - One group ("Produkty") plus an ungrouped entity (DemoSupplier)
     * - Three-tab entity (DemoProduct) covering FILE, JSON, COLOR, URL, MULTI_SELECT
     * - Multiple RELATION fields pointing to different entities
     */
    private fun catalogModule() = ModuleDef(
        name = "Katalog",
        label = "Katalog",
        labelKey = "module.catalog",
        icon = "package",
        order = 2,
        defaultEntity = _root_ide_package_.dev.acme.portal.demo.DemoProduct::class.java,
        entities = listOf(
            // ── Group: Produkty ──────────────────────────────
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.demo.DemoProduct::class.java, group = "Produkty", order = 1),

            // ── Ungrouped ────────────────────────────────────
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.demo.DemoSupplier::class.java, order = 10)
        )
    )

    /**
     * Zadania module — task definitions, run history, and result files.
     * Demonstrates: AbstractTask inheritance, async execution, S3 file storage,
     * CRON scheduling, and ActionResult.links navigation.
     */
    private fun taskModule() = ModuleDef(
        name = "Zadania",
        label = "Zadania",
        labelKey = "module.tasks",
        icon = "zap",
        order = 3,
        defaultEntity = _root_ide_package_.dev.acme.portal.task.LoanHistoryCsvTask::class.java,
        entities = listOf(
            // ── Group: Definicje ─────────────────────────────────────────────
            EntityRef(entityClass = _root_ide_package_.dev.acme.portal.task.LoanHistoryCsvTask::class.java, group = "Definicje", order = 1),

            // ── Group: Historia ──────────────────────────────────────────────
            EntityRef(entityClass = TaskRun::class.java,     group = "Historia", order = 1),
            EntityRef(entityClass = TaskRunFile::class.java, group = "Historia", order = 2)
        )
    )

    /**
     * System module — portal administration entities.
     * Currently hosts dashboard widget configuration.
     */
    private fun systemModule() = ModuleDef(        name = "System",
        label = "System",
        labelKey = "module.system",
        icon = "settings",
        order = 99,
        defaultEntity = DashboardWidget::class.java,
        entities = listOf(
            EntityRef(entityClass = DashboardWidget::class.java, order = 1)
        )
    )
}
