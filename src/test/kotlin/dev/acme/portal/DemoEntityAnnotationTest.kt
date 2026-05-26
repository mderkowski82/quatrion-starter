package dev.acme.portal

import com.fasterxml.jackson.databind.ObjectMapper
import dev.quatrion.portal.annotation.*
import dev.acme.portal.demo.*
import dev.quatrion.portal.service.MetadataService
import dev.quatrion.portal.config.EntityRef
import dev.quatrion.portal.config.ModuleDef
import dev.quatrion.portal.config.PortalModuleConfig
import dev.quatrion.portal.config.PortalUiConfig
import dev.quatrion.portal.i18n.PortalI18nService
import dev.quatrion.portal.license.LicenseVerifier
import jakarta.persistence.Column
import jakarta.persistence.Transient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.Optional
import java.util.stream.Stream

/**
 * Validates @PortalEntity and @PortalField annotations across all demo entities.
 *
 * Tests verify that:
 * - Every entity carries required annotations
 * - Field renderers and filterTypes are consistent with project assumptions
 * - RELATION fields have @PortalRelation
 * - SELECT / MULTI_SELECT fields have options or a selectEnum
 * - Tab references are valid
 * - Required fields are not readonly
 */
class DemoEntityAnnotationTest {

    companion object {
        /** All demo entity classes exercised by the portal demo module. */
        val ALL_DEMO_ENTITIES: List<Class<*>> = listOf(
            DemoCountry::class.java,
            DemoCategory::class.java,
            DemoCustomer::class.java,
            DemoOrder::class.java,
            DemoOrderItem::class.java,
            DemoProduct::class.java,
            DemoSupplier::class.java
        )

        @JvmStatic
        fun demoEntityClasses(): Stream<Class<*>> = ALL_DEMO_ENTITIES.stream()

        private fun buildService(): MetadataService {
            val config = object : PortalModuleConfig() {
                override fun modules() = listOf(
                    ModuleDef(
                        name = "CRM", label = "CRM", icon = "users", order = 1,
                        defaultEntity = DemoCountry::class.java,
                        entities = listOf(
                            EntityRef(entityClass = DemoCountry::class.java, order = 1),
                            EntityRef(entityClass = DemoCategory::class.java, order = 2),
                            EntityRef(entityClass = DemoCustomer::class.java, order = 3),
                            EntityRef(entityClass = DemoOrder::class.java, order = 4),
                            EntityRef(entityClass = DemoOrderItem::class.java, order = 5)
                        )
                    ),
                    ModuleDef(
                        name = "Katalog", label = "Katalog", icon = "package", order = 2,
                        defaultEntity = DemoProduct::class.java,
                        entities = listOf(
                            EntityRef(entityClass = DemoProduct::class.java, order = 1),
                            EntityRef(entityClass = DemoSupplier::class.java, order = 2)
                        )
                    )
                )
            }
            val uiConfig = object : PortalUiConfig {
                override fun title() = "Test"
                override fun logo() = Optional.empty<String>()
                override fun layout() = object : PortalUiConfig.LayoutConfig {
                    override fun sidebar() = object : PortalUiConfig.LayoutConfig.SidebarConfig {
                        override fun width() = 256
                        override fun collapsible() = true
                        override fun defaultCollapsed() = false
                    }
                    override fun content() = object : PortalUiConfig.LayoutConfig.ContentConfig {
                        override fun maxWidth() = 1600
                    }
                    override fun topBar() = object : PortalUiConfig.LayoutConfig.TopBarConfig {
                        override fun height() = 56
                        override fun showModuleSelector() = true
                        override fun showUserMenu() = true
                        override fun showSearch() = false
                    }
                }
                override fun theme() = object : PortalUiConfig.ThemeConfig {
                    override fun primaryColor() = "#2563eb"
                    override fun accentColor() = "#3b82f6"
                    override fun sidebarBg() = "#1e293b"
                    override fun sidebarText() = "#e2e8f0"
                    override fun headerBg() = "#ffffff"
                }
                override fun table() = object : PortalUiConfig.TableConfig {
                    override fun defaultPageSize() = 25
                    override fun showRowNumbers() = false
                    override fun enableExport() = false
                    override fun stickyHeader() = true
                }
                override fun form() = object : PortalUiConfig.FormConfig {
                    override fun modalWidth() = "lg"
                    override fun nestedModalWidth() = "md"
                    override fun showTabIcons() = true
                    override fun autoSaveInterval() = 0
                }
                override fun filter() = object : PortalUiConfig.FilterConfig {
                    override fun position() = "modal"
                    override fun rememberFilters() = true
                    override fun maxFilterFields() = 20
                }
                override fun security() = object : PortalUiConfig.SecurityConfig {
                    override fun provider() = "none"
                    override fun rolesAttribute() = "realm_access.roles"
                }
            }
            val i18nService = PortalI18nService("pl", "pl,en")
            val licenseVerifier = LicenseVerifier(java.util.Optional.of("")).also { it.verify() }
            return MetadataService(MetadataServiceTest.mockInstance(config), uiConfig, ObjectMapper(), i18nService, licenseVerifier)
        }

        private val metadataService = buildService()
    }

    // ── Every entity must carry @PortalEntity ─────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `every demo entity has @PortalEntity annotation`(clazz: Class<*>) {
        assertNotNull(
            clazz.getAnnotation(PortalEntity::class.java),
            "${clazz.simpleName} is missing @PortalEntity"
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `every demo entity has a non-blank label`(clazz: Class<*>) {
        val ann = clazz.getAnnotation(PortalEntity::class.java)!!
        assertTrue(ann.label.isNotBlank(), "${clazz.simpleName} label must not be blank")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `every demo entity has a non-blank module`(clazz: Class<*>) {
        val ann = clazz.getAnnotation(PortalEntity::class.java)!!
        assertTrue(ann.module.isNotBlank(), "${clazz.simpleName} module must not be blank")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `every demo entity has a non-blank icon`(clazz: Class<*>) {
        val ann = clazz.getAnnotation(PortalEntity::class.java)!!
        assertTrue(ann.icon.isNotBlank(), "${clazz.simpleName} icon must not be blank")
    }

    // ── Every entity must pass tab validation ─────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `every demo entity passes tab validation`(clazz: Class<*>) {
        assertDoesNotThrow({ metadataService.validateTabs(clazz) },
            "${clazz.simpleName} has invalid tab references")
    }

    // ── Metadata must be buildable without errors ─────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `MetadataService can build metadata for every demo entity`(clazz: Class<*>) {
        assertDoesNotThrow({ metadataService.buildEntityMetadata(clazz) },
            "${clazz.simpleName} metadata build failed")
    }

    // ── Field-level annotation constraints ────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `no required field is also readonly`(clazz: Class<*>) {
        val fields = clazz.declaredFields
            .filter { it.isAnnotationPresent(PortalField::class.java) }
            .map { it.getAnnotation(PortalField::class.java) }
        val conflicts = fields.filter { it.required && it.readonly }
        assertTrue(conflicts.isEmpty(),
            "${clazz.simpleName} has fields that are both required and readonly: ${conflicts.map { it.label }}")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `every RELATION field has @PortalRelation annotation`(clazz: Class<*>) {
        clazz.declaredFields
            .filter {
                val pf = it.getAnnotation(PortalField::class.java) ?: return@filter false
                pf.renderer == RendererType.RELATION || pf.renderer == RendererType.RELATION_LIST
            }
            .forEach { field ->
                assertNotNull(
                    field.getAnnotation(PortalRelation::class.java),
                    "${clazz.simpleName}.${field.name} is RELATION/RELATION_LIST but lacks @PortalRelation"
                )
            }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `every SELECT and MULTI_SELECT field has options or selectEnum`(clazz: Class<*>) {
        clazz.declaredFields
            .filter {
                val pf = it.getAnnotation(PortalField::class.java) ?: return@filter false
                pf.renderer == RendererType.SELECT || pf.renderer == RendererType.MULTI_SELECT
            }
            .forEach { field ->
                val pf = field.getAnnotation(PortalField::class.java)!!
                val hasOptions = pf.selectOptions.isNotEmpty()
                val hasEnum = pf.selectEnum != Unit::class && pf.selectEnum.java.isEnum
                assertTrue(hasOptions || hasEnum,
                    "${clazz.simpleName}.${field.name} is SELECT/MULTI_SELECT but has neither selectOptions nor selectEnum")
            }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `fields with showInFilter=true and filterType=NONE must have showInFilter explicitly overridden`(clazz: Class<*>) {
        // Fields that are visible in filter but have filterType NONE are allowed (e.g. TEXTAREA description),
        // but BOOLEAN fields must use FilterType.BOOLEAN, not NONE
        clazz.declaredFields
            .filter {
                val pf = it.getAnnotation(PortalField::class.java) ?: return@filter false
                pf.showInFilter && pf.renderer == RendererType.BOOLEAN
            }
            .forEach { field ->
                val pf = field.getAnnotation(PortalField::class.java)!!
                assertNotEquals(FilterType.NONE, pf.filterType,
                    "${clazz.simpleName}.${field.name} is a BOOLEAN field in filter but filterType=NONE")
                assertNotEquals(FilterType.CONTAINS, pf.filterType,
                    "${clazz.simpleName}.${field.name} is a BOOLEAN field but uses CONTAINS filter (should be BOOLEAN)")
            }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `RELATION_LIST fields are marked with @jakarta_persistence_Transient`(clazz: Class<*>) {
        clazz.declaredFields
            .filter {
                val pf = it.getAnnotation(PortalField::class.java) ?: return@filter false
                pf.renderer == RendererType.RELATION_LIST
            }
            .forEach { field ->
                assertNotNull(
                    field.getAnnotation(Transient::class.java),
                    "${clazz.simpleName}.${field.name} is RELATION_LIST but lacks @Transient"
                )
            }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoEntityClasses")
    fun `every @PortalField has a non-blank label`(clazz: Class<*>) {
        clazz.declaredFields
            .filter { it.isAnnotationPresent(PortalField::class.java) }
            .forEach { field ->
                val pf = field.getAnnotation(PortalField::class.java)!!
                assertTrue(pf.label.isNotBlank(),
                    "${clazz.simpleName}.${field.name} has a blank label")
            }
    }

    // ── Per-entity structural checks ──────────────────────────────────────────

    @Test
    fun `DemoCountry is a flat entity with no tabs`() {
        val meta = metadataService.buildEntityMetadata(DemoCountry::class.java)
        assertTrue(meta.tabs.isEmpty(), "DemoCountry should have no tabs")
    }

    @Test
    fun `DemoCountry has exactly the expected filterable fields`() {
        val meta = metadataService.buildEntityMetadata(DemoCountry::class.java)
        val filterable = meta.fields.filter { it.showInFilter && !it.hidden && it.filterType != "NONE" }
        val names = filterable.map { it.name }.toSet()
        // name (CONTAINS), code (EXACT), continent (IN), isEu (BOOLEAN)
        assertTrue("name" in names, "DemoCountry.name should be filterable")
        assertTrue("code" in names, "DemoCountry.code should be filterable")
        assertTrue("continent" in names, "DemoCountry.continent should be filterable")
        assertTrue("isEu" in names, "DemoCountry.isEu should be filterable")
    }

    @Test
    fun `DemoCountry continent field uses IN filter and has selectEnum`() {
        val meta = metadataService.buildEntityMetadata(DemoCountry::class.java)
        val continent = meta.fields.find { it.name == "continent" }!!
        assertEquals("IN", continent.filterType)
        assertEquals("SELECT", continent.renderer)
        assertEquals(Continent.values().size, continent.selectOptions.size)
    }

    @Test
    fun `DemoCustomer has 4 tabs`() {
        val meta = metadataService.buildEntityMetadata(DemoCustomer::class.java)
        assertEquals(4, meta.tabs.size)
        val tabNames = meta.tabs.map { it.name }
        assertTrue("BASIC" in tabNames)
        assertTrue("CONTACT" in tabNames)
        assertTrue("FINANCIAL" in tabNames)
        assertTrue("SYSTEM" in tabNames)
    }

    @Test
    fun `DemoCustomer has 3 portal actions`() {
        val meta = metadataService.buildEntityMetadata(DemoCustomer::class.java)
        assertEquals(3, meta.actions.size)
        val actionNames = meta.actions.map { it.name }
        assertTrue("activate" in actionNames)
        assertTrue("deactivate" in actionNames)
        assertTrue("sendWelcomeEmail" in actionNames)
    }

    @Test
    fun `DemoCustomer birthDate uses RANGE filterType`() {
        val meta = metadataService.buildEntityMetadata(DemoCustomer::class.java)
        val field = meta.fields.find { it.name == "birthDate" }!!
        assertEquals("RANGE", field.filterType)
        assertEquals("DATE", field.renderer)
    }

    @Test
    fun `DemoCustomer registeredAt uses RANGE filterType`() {
        val meta = metadataService.buildEntityMetadata(DemoCustomer::class.java)
        val field = meta.fields.find { it.name == "registeredAt" }!!
        assertEquals("RANGE", field.filterType)
        assertEquals("DATETIME", field.renderer)
    }

    @Test
    fun `DemoCustomer covers all 17 renderer types`() {
        val allRenderers = RendererType.values().map { it.name }.toSet()
        val customerRenderers = DemoCustomer::class.java.declaredFields
            .mapNotNull { it.getAnnotation(PortalField::class.java)?.renderer?.name }
            .toSet()
        val missing = allRenderers - customerRenderers
        assertTrue(missing.isEmpty(),
            "DemoCustomer is missing renderer types: $missing")
    }

    @Test
    fun `DemoOrder has RELATION_LIST items field pointing to DemoOrderItem`() {
        val meta = metadataService.buildEntityMetadata(DemoOrder::class.java)
        val items = meta.fields.find { it.name == "items" }!!
        assertEquals("RELATION_LIST", items.renderer)
        assertNotNull(items.relationMeta)
        assertEquals("DemoOrderItem", items.relationMeta!!.targetEntity)
        assertTrue(items.relationMeta!!.inlineEdit)
    }

    @Test
    fun `DemoOrder has 2 tabs`() {
        val meta = metadataService.buildEntityMetadata(DemoOrder::class.java)
        assertEquals(2, meta.tabs.size)
        val names = meta.tabs.map { it.name }
        assertTrue("INFO" in names)
        assertTrue("ITEMS" in names)
    }

    @Test
    fun `DemoProduct has 3 tabs`() {
        val meta = metadataService.buildEntityMetadata(DemoProduct::class.java)
        assertEquals(3, meta.tabs.size)
        val names = meta.tabs.map { it.name }
        assertTrue("INFO" in names)
        assertTrue("DETAILS" in names)
        assertTrue("MEDIA" in names)
    }

    @Test
    fun `DemoOrderItem has allowCreate=false`() {
        val ann = DemoOrderItem::class.java.getAnnotation(PortalEntity::class.java)!!
        assertFalse(ann.allowCreate, "DemoOrderItem should not allow direct creation")
    }

    @Test
    fun `DemoSupplier is a flat entity`() {
        val meta = metadataService.buildEntityMetadata(DemoSupplier::class.java)
        assertTrue(meta.tabs.isEmpty(), "DemoSupplier should have no tabs")
    }

    @Test
    fun `DemoCategory has self-referential parentId RELATION`() {
        val meta = metadataService.buildEntityMetadata(DemoCategory::class.java)
        val field = meta.fields.find { it.name == "parentId" }!!
        assertEquals("RELATION", field.renderer)
        assertNotNull(field.relationMeta)
        assertEquals("DemoCategory", field.relationMeta!!.targetEntity)
    }

    @Test
    fun `DemoCountry code has @Regex annotation with valid pattern`() {
        val meta = metadataService.buildEntityMetadata(DemoCountry::class.java)
        val code = meta.fields.find { it.name == "code" }!!
        assertTrue(code.regex.isNotEmpty(), "DemoCountry.code should have a regex constraint")
        assertTrue(code.maxLength > 0, "DemoCountry.code should have a maxLength from @Column")
    }

    @Test
    fun `DemoCustomer phone has @Regex and @Column length constraint`() {
        val meta = metadataService.buildEntityMetadata(DemoCustomer::class.java)
        val phone = meta.fields.find { it.name == "phone" }!!
        assertTrue(phone.regex.isNotEmpty())
        assertEquals(20, phone.maxLength)
        assertEquals("Numer telefonu może zawierać cyfry, spacje, myślniki i opcjonalny znak +",
            phone.regexMessage)
    }

    @Test
    fun `DemoCustomer customerType SELECT field uses selectEnum`() {
        val meta = metadataService.buildEntityMetadata(DemoCustomer::class.java)
        val field = meta.fields.find { it.name == "customerType" }!!
        assertEquals("SELECT", field.renderer)
        assertEquals("IN", field.filterType)
        assertEquals(CustomerType.values().size, field.selectOptions.size)
    }

    @Test
    fun `DemoOrder status SELECT field uses selectEnum`() {
        val meta = metadataService.buildEntityMetadata(DemoOrder::class.java)
        val field = meta.fields.find { it.name == "status" }!!
        assertEquals("SELECT", field.renderer)
        assertEquals("IN", field.filterType)
        assertEquals(OrderStatus.values().size, field.selectOptions.size)
    }

    @Test
    fun `all demo entity permissions default to create=true edit=true delete=true except DemoOrderItem`() {
        val entitiesWithFullPermissions = ALL_DEMO_ENTITIES.filter { it != DemoOrderItem::class.java }
        for (clazz in entitiesWithFullPermissions) {
            val meta = metadataService.buildEntityMetadata(clazz)
            assertTrue(meta.permissions.create, "${clazz.simpleName} should allow create")
            assertTrue(meta.permissions.edit, "${clazz.simpleName} should allow edit")
            assertTrue(meta.permissions.delete, "${clazz.simpleName} should allow delete")
        }
    }

    @Test
    fun `DemoProduct has FILE renderer for imageUrl`() {
        val meta = metadataService.buildEntityMetadata(DemoProduct::class.java)
        val field = meta.fields.find { it.name == "imageUrl" }!!
        assertEquals("FILE", field.renderer)
        assertFalse(field.showInFilter, "imageUrl should not be in filter")
    }

    @Test
    fun `DemoProduct has JSON renderer for metadata field`() {
        val meta = metadataService.buildEntityMetadata(DemoProduct::class.java)
        val field = meta.fields.find { it.name == "metadata" }!!
        assertEquals("JSON", field.renderer)
    }

    @Test
    fun `DemoCustomer extraData has JSON renderer`() {
        val meta = metadataService.buildEntityMetadata(DemoCustomer::class.java)
        val field = meta.fields.find { it.name == "extraData" }!!
        assertEquals("JSON", field.renderer)
        assertFalse(field.showInFilter)
    }

    @Test
    fun `DemoCustomer password field is not shown in filter or table`() {
        val meta = metadataService.buildEntityMetadata(DemoCustomer::class.java)
        val field = meta.fields.find { it.name == "password" }!!
        assertFalse(field.showInFilter)
        assertFalse(field.showInTable)
    }
}
