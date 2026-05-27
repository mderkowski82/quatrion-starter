package dev.acme.portal

import com.fasterxml.jackson.databind.ObjectMapper
import dev.acme.portal.task.LoanHistoryCsvTask
import dev.quatrion.portal.annotation.*
import dev.quatrion.portal.annotation.Regex
import dev.quatrion.portal.config.EntityRef
import dev.quatrion.portal.config.ModuleDef
import dev.quatrion.portal.config.PortalModuleConfig
import dev.quatrion.portal.config.PortalUiConfig
import dev.quatrion.portal.i18n.PortalI18nService
import dev.quatrion.portal.license.LicenseVerifier
import dev.quatrion.portal.model.DependencyConditionMetadata
import dev.quatrion.portal.service.MetadataService
import dev.quatrion.portal.task.AbstractTask
import jakarta.enterprise.inject.Instance
import jakarta.persistence.Column
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Optional

enum class TestTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Basic Info", "info", 0),
    DETAILS("Details", "settings", 1)
}

@PortalEntity(
    label = "Test Entity",
    module = "Test Module",
    group = "Test Group",
    icon = "test",
    order = 1,
    tabs = TestTab::class
)
class TestEntity {
    @PortalField(label = "Name", tab = "BASIC", order = 1, required = true)
    var name: String = ""

    @PortalField(label = "Description", tab = "DETAILS", order = 1, renderer = RendererType.TEXTAREA)
    var description: String = ""
}

@PortalEntity(
    label = "Flat Entity",
    module = "Test Module",
    order = 2
)
class FlatEntity {
    @PortalField(label = "Code", order = 1)
    var code: String = ""

    @PortalField(
        label = "Status",
        order = 2,
        renderer = RendererType.SELECT,
        filterType = FilterType.IN,
        selectOptions = ["Active", "Inactive", "Pending"]
    )
    var status: String = ""
}

enum class TestPriority(val label: String) {
    LOW("Low"), MEDIUM("Medium"), HIGH("High");
    override fun toString() = label
}

@PortalEntity(
    label = "Enum Entity",
    module = "Test Module",
    order = 3
)
class EnumEntity {
    @PortalField(label = "Code", order = 1)
    var code: String = ""

    @PortalField(
        label = "Priority",
        order = 2,
        renderer = RendererType.SELECT,
        filterType = FilterType.IN,
        selectEnum = TestPriority::class
    )
    var priority: TestPriority? = null
}

@PortalEntity(
    label = "Entity with bad tab",
    module = "Test Module"
)
class BadTabEntity {
    @PortalField(label = "Name", tab = "NONEXISTENT", order = 1)
    var name: String = ""
}

// ── Regex validation test fixtures ───────────────────────────────────────────

@PortalEntity(label = "Valid Regex Entity", module = "Test Module")
class ValidRegexEntity {
    @Regex(pattern = """^[A-Z]{2,3}$""", message = "Must be 2-3 uppercase letters")
    @PortalField(label = "Code", order = 1)
    var code: String = ""

    @Regex(pattern = """^\d{4}-\d{2}-\d{2}$""", message = "Date format YYYY-MM-DD")
    @PortalField(label = "Date", order = 2)
    var date: String = ""
}

@PortalEntity(label = "Invalid Regex Entity", module = "Test Module")
class InvalidRegexEntity {
    /** Intentionally broken regex — unclosed bracket */
    @Regex(pattern = """[invalid""", message = "This regex is broken")
    @PortalField(label = "BadField", order = 1)
    var badField: String = ""
}

@PortalEntity(label = "Blank Regex Entity", module = "Test Module")
class BlankRegexEntity {
    /** @Regex with empty pattern should be silently skipped */
    @Regex(pattern = "", message = "")
    @PortalField(label = "Name", order = 1)
    var name: String = ""
}

// ── Inheritance test fixtures ─────────────────────────────────────────────────

abstract class BaseTaskForTest {
    @PortalField(label = "Base Name", order = 1, renderer = RendererType.TEXT, filterType = FilterType.CONTAINS)
    open var baseName: String = ""

    @PortalField(label = "Base Status", order = 2, renderer = RendererType.SELECT, filterType = FilterType.IN)
    var baseStatus: String = ""
}

@PortalEntity(label = "Child Task", module = "Test Module", order = 10)
class ChildTaskEntity : BaseTaskForTest() {
    @PortalField(label = "Child Param", order = 10, renderer = RendererType.NUMBER, filterType = FilterType.NONE)
    var childParam: Int = 0
}

// ── Tab enum for tabbed-inheritance test (must be top-level — Kotlin forbids local enum) ──

enum class InheritanceTestTab(override val label: String, override val icon: String, override val order: Int) : PortalTab {
    PARAMS("Parametry", "settings", 0),
    HISTORY("Historia", "history", 1)
}

@PortalEntity(label = "Tabbed Child", module = "Test Module", order = 12, tabs = InheritanceTestTab::class)
class TabbedChildEntity : BaseTaskForTest() {
    @PortalField(label = "Own Field", tab = "PARAMS", order = 10, renderer = RendererType.TEXT, filterType = FilterType.NONE)
    var ownField: String = ""
}

@PortalEntity(
    label = "Validation Entity",
    module = "Test Module",
    order = 4
)
class ValidationEntity {
    @Column(length = 5)
    @PortalField(label = "Short Code", order = 1)
    var code: String = ""

    // Note: the regex upper bound {7,20} intentionally matches the @Column(length = 20).
    // Both constraints are propagated independently; keeping them in sync is the developer's responsibility.
    @Column(length = 20)
    @Regex(pattern = """^\+?[\d\s\-]{7,20}$""", message = "Nieprawidłowy numer")
    @PortalField(label = "Telefon", order = 2, required = true)
    var phone: String = ""

    @PortalField(label = "Opis", order = 3)
    var description: String = ""
}

// ─── Relation test fixtures ───────────────────────────────────────────────────

@PortalEntity(label = "Related Target", module = "Test Module", order = 10)
class RelationTarget {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "Name", order = 1)
    var name: String = ""
}

@PortalEntity(label = "Child Item", module = "Test Module", order = 11)
class ChildItem {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0
}

@PortalEntity(label = "Relation Entity", module = "Test Module", order = 12)
class RelationEntity {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "Related", order = 1, renderer = RendererType.RELATION, filterType = FilterType.EXACT)
    @PortalRelation(
        targetEntity = RelationTarget::class,
        editable = true,
        displayFields = ["name"],
        searchFields = ["name"]
    )
    @PortalLookup(labelField = "name", valueField = "id")
    var relatedId: Long? = null

    @PortalField(
        label = "Children",
        order = 2,
        renderer = RendererType.RELATION_LIST,
        showInFilter = false,
        showInTable = false
    )
    @PortalRelation(
        targetEntity = ChildItem::class,
        editable = true,
        inlineEdit = true,
        displayFields = ["id"],
        maxItems = 50
    )
    var children: List<ChildItem>? = null

    @PortalField(label = "Name", order = 3)
    var name: String = ""
}

@PortalEntity(label = "Filtered Relation Entity", module = "Test Module", order = 13)
class FilteredRelationEntity {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "Country", order = 1, renderer = RendererType.RELATION)
    @PortalRelation(
        targetEntity = RelationTarget::class,
        editable = true,
        displayFields = ["name"]
    )
    @PortalLookup(labelField = "name", valueField = "id", filterQuery = "e.isActive = true")
    var countryId: Long? = null
}

// ─── Cascading (dependsOn) relation test fixtures ─────────────────────────────

@PortalEntity(label = "Cascading Relation Entity", module = "Test Module", order = 14)
class CascadingRelationEntity {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "Country", order = 1, renderer = RendererType.RELATION)
    @PortalRelation(
        targetEntity = RelationTarget::class,
        editable = true,
        displayFields = ["name"]
    )
    @PortalLookup(labelField = "name", valueField = "id")
    var countryId: Long? = null

    @PortalField(label = "City", order = 2, renderer = RendererType.RELATION)
    @PortalRelation(
        targetEntity = RelationTarget::class,
        editable = true,
        displayFields = ["name"]
    )
    @PortalLookup(labelField = "name", valueField = "id", dependsOn = "countryId")
    var cityId: Long? = null
}

// ─── OrderBy relation test fixtures ───────────────────────────────────────────

@PortalEntity(label = "OrderBy Relation Entity", module = "Test Module", order = 18)
class OrderByRelationEntity {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "Category", order = 1, renderer = RendererType.RELATION)
    @PortalRelation(
        targetEntity = RelationTarget::class,
        editable = true,
        displayFields = ["name"],
        orderBy = "name ASC"
    )
    @PortalLookup(labelField = "name", valueField = "id")
    var categoryId: Long? = null

    @PortalField(label = "Tag", order = 2, renderer = RendererType.RELATION)
    @PortalRelation(
        targetEntity = RelationTarget::class,
        editable = true,
        displayFields = ["name"]
    )
    @PortalLookup(labelField = "name", valueField = "id")
    var tagId: Long? = null
}

@PortalEntity(label = "Dependent Field Entity", module = "Test Module", order = 19)
class DependentFieldEntity {
    @PortalField(label = "Mode", order = 1, renderer = RendererType.SELECT, selectOptions = ["BASIC", "ADVANCED"])
    var mode: String = ""

    @PortalField(label = "Level", order = 2, renderer = RendererType.SELECT, selectOptions = ["LOW", "MEDIUM", "HIGH"])
    @PortalDependency(
        field = "mode",
        operator = DependencyOperator.EQ,
        value = "ADVANCED",
        visibility = DependencyVisibility.SHOW,
        allowedValues = ["MEDIUM", "HIGH"],
        message = "Level jest dostępny tylko w trybie ADVANCED."
    )
    var level: String = ""

    @PortalField(label = "Score", order = 3, renderer = RendererType.NUMBER, min = 0.0, max = 100.0)
    @PortalDependency(
        field = "mode",
        operator = DependencyOperator.EQ,
        value = "ADVANCED",
        min = "10",
        max = "50"
    )
    var score: Int = 0
}

@PortalEntity(label = "Invalid Dependency Entity", module = "Test Module", order = 20)
class InvalidDependencyEntity {
    @PortalField(label = "Mode", order = 1)
    var mode: String = ""

    @PortalField(label = "Broken", order = 2)
    @PortalDependency(
        field = "missingField",
        operator = DependencyOperator.EQ,
        value = "X",
        visibility = DependencyVisibility.SHOW
    )
    var broken: String = ""
}

@PortalEntity(label = "Invalid Mixed Dependency Entity", module = "Test Module", order = 21)
class InvalidMixedDependencyEntity {
    @PortalField(label = "Mode", order = 1)
    var mode: String = ""

    @PortalField(label = "Broken", order = 2)
    @PortalDependency(
        field = "mode",
        operator = DependencyOperator.EQ,
        value = "X",
        condition = """{"field":"mode","operator":"eq","value":"X"}"""
    )
    var broken: String = ""
}

// ─── Entity with description and displayExpression fixtures ───────────────────

@PortalEntity(
    label = "Described Entity",
    module = "Test Module",
    order = 14,
    description = "An entity with a longer description for tooltip display"
)
class DescribedEntity {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "Name", order = 1)
    var name: String = ""
}

@PortalEntity(
    label = "Expression Entity",
    module = "Test Module",
    order = 15
)
class ExpressionEntity {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "First Name", order = 1)
    var firstName: String = ""

    @PortalField(label = "Last Name", order = 2)
    var lastName: String = ""

    @PortalField(
        label = "Full Name",
        order = 3,
        displayExpression = "\${firstName} \${lastName}",
        readonly = true,
        showInTable = true
    )
    var fullName: String = ""
}

// ─── Entity with min/max/defaultValue fixtures ─────────────────────────────

@PortalEntity(
    label = "MinMax Entity",
    module = "Test Module",
    order = 16
)
class MinMaxEntity {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "Rating", order = 1, renderer = RendererType.NUMBER, min = 1.0, max = 5.0)
    var rating: Int = 0

    @PortalField(label = "Price", order = 2, renderer = RendererType.DECIMAL, min = 0.0, max = 9999.99)
    var price: Double = 0.0

    @PortalField(label = "Status", order = 3, defaultValue = "ACTIVE")
    var status: String = ""

    @PortalField(label = "Count", order = 4, renderer = RendererType.NUMBER, defaultValue = "10", min = 0.0)
    var count: Int = 0

    @PortalField(label = "Name", order = 5)
    var name: String = ""
}

@PortalEntity(
    label = "Soft Delete Entity",
    module = "Test Module",
    order = 17,
    softDelete = true
)
class SoftDeleteEntity {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "Name", order = 1)
    var name: String = ""

    var deleted: Boolean = false
}

@PortalEntity(
    label = "Audit Entity",
    module = "Test Module",
    order = 18,
    auditLog = true
)
class AuditEntity {
    @PortalField(label = "ID", order = 0, readonly = true)
    var id: Long = 0

    @PortalField(label = "Name", order = 1)
    var name: String = ""
}

class MetadataServiceTest {

    companion object {
        /** Wraps a [PortalModuleConfig] in a CDI [Instance] mock for unit-testing [MetadataService]. */
        @Suppress("UNCHECKED_CAST")
        internal fun mockInstance(config: PortalModuleConfig): Instance<PortalModuleConfig> =
            mock<Instance<PortalModuleConfig>> {
                on { isUnsatisfied } doReturn false
                on { get() } doReturn config
            }

        private val config = object : PortalModuleConfig() {
            override fun modules() = listOf(
                ModuleDef(
                    name = "Test Module",
                    label = "Test Module",
                    icon = "test",
                    order = 1,
                    defaultEntity = TestEntity::class.java,
                    entities = listOf(
                        EntityRef(entityClass = TestEntity::class.java, group = "Test Group", order = 1),
                        EntityRef(entityClass = FlatEntity::class.java, order = 2)
                    )
                )
            )
        }

        private val uiConfig = object : PortalUiConfig {
            override fun title() = "Test Portal"
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

        private val i18nService = PortalI18nService("pl", "pl,en")
        private val licenseVerifier = LicenseVerifier(java.util.Optional.of("")).also { it.verify() }
        private val service = MetadataService(mockInstance(config), uiConfig, ObjectMapper(), i18nService, licenseVerifier)
    }

    @Test
    fun `buildEntityMetadata extracts tabs from enum`() {
        val metadata = service.buildEntityMetadata(TestEntity::class.java)
        assertEquals(2, metadata.tabs.size)
        assertEquals("BASIC", metadata.tabs[0].name)
        assertEquals("Basic Info", metadata.tabs[0].label)
        assertEquals("info", metadata.tabs[0].icon)
        assertEquals(0, metadata.tabs[0].order)
        assertEquals("DETAILS", metadata.tabs[1].name)
        assertEquals(1, metadata.tabs[1].order)
    }

    @Test
    fun `buildEntityMetadata extracts fields correctly`() {
        val metadata = service.buildEntityMetadata(TestEntity::class.java)
        assertEquals(2, metadata.fields.size)
        val nameField = metadata.fields.find { it.name == "name" }!!
        assertEquals("Name", nameField.label)
        assertEquals("BASIC", nameField.tab)
        assertTrue(nameField.required)
        assertEquals("AUTO", nameField.renderer)
        val descField = metadata.fields.find { it.name == "description" }!!
        assertEquals("TEXTAREA", descField.renderer)
    }

    @Test
    fun `buildEntityMetadata for flat entity has empty tabs`() {
        val metadata = service.buildEntityMetadata(FlatEntity::class.java)
        assertTrue(metadata.tabs.isEmpty())
        assertEquals(2, metadata.fields.size)
    }

    @Test
    fun `validateTabs throws on invalid tab reference`() {
        assertThrows<IllegalStateException> {
            service.validateTabs(BadTabEntity::class.java)
        }
    }

    @Test
    fun `validateTabs passes for valid tab reference`() {
        assertDoesNotThrow {
            service.validateTabs(TestEntity::class.java)
        }
    }

    @Test
    fun `validateTabs passes for flat entity with no tab refs`() {
        assertDoesNotThrow {
            service.validateTabs(FlatEntity::class.java)
        }
    }

    // ── validateRegexPatterns ─────────────────────────────────────────────────

    @Test
    fun `validateRegexPatterns passes for entity with valid regex patterns`() {
        assertDoesNotThrow {
            service.validateRegexPatterns(ValidRegexEntity::class.java)
        }
    }

    @Test
    fun `validateRegexPatterns throws IllegalStateException for entity with invalid regex`() {
        val ex = assertThrows<IllegalStateException> {
            service.validateRegexPatterns(InvalidRegexEntity::class.java)
        }
        assertTrue(ex.message!!.contains("InvalidRegexEntity"), "Should mention entity name")
        assertTrue(ex.message!!.contains("badField"), "Should mention field name")
        assertTrue(ex.message!!.contains("[invalid"), "Should include the bad pattern")
    }

    @Test
    fun `validateRegexPatterns skips blank pattern without throwing`() {
        assertDoesNotThrow {
            service.validateRegexPatterns(BlankRegexEntity::class.java)
        }
    }

    @Test
    fun `validateRegexPatterns passes for entity without any @Regex annotations`() {
        assertDoesNotThrow {
            service.validateRegexPatterns(FlatEntity::class.java)
        }
    }

    @Test
    fun `validateRegexPatterns error message includes pattern and field name`() {
        val ex = assertThrows<IllegalStateException> {
            service.validateRegexPatterns(InvalidRegexEntity::class.java)
        }
        // Message should be developer-friendly: entity + field + pattern
        assertTrue("[invalid" in ex.message!!, "Pattern should appear in error: ${ex.message}")
    }

    @Test
    fun `buildEntityMetadata includes fields inherited from superclass`() {
        val metadata = service.buildEntityMetadata(ChildTaskEntity::class.java)
        val fieldNames = metadata.fields.map { it.name }

        // own field
        assertTrue("childParam" in fieldNames, "childParam should be present")
        // inherited fields from BaseTaskForTest
        assertTrue("baseName" in fieldNames, "baseName inherited from superclass should be present")
        assertTrue("baseStatus" in fieldNames, "baseStatus inherited from superclass should be present")

        assertEquals(3, metadata.fields.size, "all 3 fields (1 own + 2 inherited) must be present")

        val baseName = metadata.fields.find { it.name == "baseName" }!!
        assertEquals("Base Name", baseName.label)
        assertEquals("TEXT", baseName.renderer)

        // fields must be sorted by order: baseName(1), baseStatus(2), childParam(10)
        assertEquals(listOf("baseName", "baseStatus", "childParam"), metadata.fields.map { it.name })
    }

    @Test
    fun `buildEntityMetadata assigns no-tab inherited fields to first tab when entity has tabs`() {
        // AbstractTask-style pattern: base fields have tab="" but the tabbed subclass defines tabs.
        // Those fields must appear in the first tab so the frontend can render them.
        val metadata = service.buildEntityMetadata(TabbedChildEntity::class.java)

        // baseName and baseStatus have tab="" in BaseTaskForTest — should be promoted to "PARAMS" (first tab)
        val baseName = metadata.fields.find { it.name == "baseName" }!!
        val baseStatus = metadata.fields.find { it.name == "baseStatus" }!!
        assertEquals("PARAMS", baseName.tab, "inherited no-tab field must be assigned to first tab")
        assertEquals("PARAMS", baseStatus.tab, "inherited no-tab field must be assigned to first tab")

        // own field explicitly tagged PARAMS stays PARAMS
        val ownField = metadata.fields.find { it.name == "ownField" }!!
        assertEquals("PARAMS", ownField.tab)
    }

    @Test
    fun `buildEntityMetadata subclass field shadows superclass field with same name`() {
        // If a subclass re-declares a field with the same name, only the subclass version appears once.
        @PortalEntity(label = "Shadow Entity", module = "Test Module", order = 11)
        class ShadowEntity : BaseTaskForTest() {
            @PortalField(label = "Overridden baseName", order = 99, renderer = RendererType.TEXTAREA, filterType = FilterType.NONE)
            override var baseName: String = "overridden"
        }
        val metadata = service.buildEntityMetadata(ShadowEntity::class.java)
        val baseNameFields = metadata.fields.filter { it.name == "baseName" }
        assertEquals(1, baseNameFields.size, "baseName must appear exactly once")
        assertEquals("Overridden baseName", baseNameFields[0].label, "subclass field must take precedence")
    }

    // ── LoanHistoryCsvTask integration smoke test ─────────────────────────────

    @Test
    fun `LoanHistoryCsvTask metadata has 3 tabs including DEFAULT`() {
        val metadata = service.buildEntityMetadata(LoanHistoryCsvTask::class.java)

        // Must have exactly 3 tabs
        assertEquals(3, metadata.tabs.size, "expected DEFAULT + PARAMS + HISTORY tabs")
        val tabNames = metadata.tabs.map { it.name }
        assertTrue("DEFAULT" in tabNames, "DEFAULT tab must be present")
        assertTrue("PARAMS" in tabNames, "PARAMS tab must be present")
        assertTrue("HISTORY" in tabNames, "HISTORY tab must be present")

        // DEFAULT must be first (order=0)
        assertEquals("DEFAULT", metadata.tabs[0].name)
        assertEquals("Ogólne", metadata.tabs[0].label)

        // AbstractTask fields (tab="") must be assigned to DEFAULT
        val abstractTaskFieldNames = AbstractTask::class.java.declaredFields
            .filter { it.isAnnotationPresent(PortalField::class.java) }
            .map { it.name }
        assertTrue(abstractTaskFieldNames.isNotEmpty(), "AbstractTask should have @PortalField fields")
        abstractTaskFieldNames.forEach { fieldName ->
            val field = metadata.fields.find { it.name == fieldName }
            assertNotNull(field, "AbstractTask field '$fieldName' must appear in LoanHistoryCsvTask metadata")
            assertEquals("DEFAULT", field!!.tab,
                "AbstractTask field '$fieldName' must be assigned to DEFAULT (first) tab, was '${field.tab}'")
        }

        // LoanHistoryCsvTask own fields keep their explicit tabs
        assertEquals("PARAMS",  metadata.fields.find { it.name == "memberId" }!!.tab)
        assertEquals("PARAMS",  metadata.fields.find { it.name == "includeOverdue" }!!.tab)
        assertEquals("HISTORY", metadata.fields.find { it.name == "taskRuns" }!!.tab)
    }

    @Test
    fun `getPortalMetadata returns correct structure`() {
        val metadata = service.getPortalMetadata()
        assertEquals("Test Portal", metadata.portalTitle)
        assertEquals(1, metadata.modules.size)
        val module = metadata.modules[0]
        assertEquals("Test Module", module.name)
        assertEquals(1, module.groups.size)
        assertEquals("Test Group", module.groups[0].name)
        assertEquals(1, module.ungroupedEntities.size)
    }

    @Test
    fun `buildEntityMetadata entity permissions from annotation`() {
        val metadata = service.buildEntityMetadata(TestEntity::class.java)
        assertTrue(metadata.permissions.create)
        assertTrue(metadata.permissions.edit)
        assertTrue(metadata.permissions.delete)
    }

    @Test
    fun `buildEntityMetadata extracts selectOptions for SELECT fields`() {
        val metadata = service.buildEntityMetadata(FlatEntity::class.java)
        val statusField = metadata.fields.find { it.name == "status" }!!
        assertEquals("SELECT", statusField.renderer)
        assertEquals(listOf("Active", "Inactive", "Pending"), statusField.selectOptions)
    }

    @Test
    fun `buildEntityMetadata selectOptions is empty for non-SELECT fields`() {
        val metadata = service.buildEntityMetadata(FlatEntity::class.java)
        val codeField = metadata.fields.find { it.name == "code" }!!
        assertTrue(codeField.selectOptions.isEmpty())
    }

    @Test
    fun `buildEntityMetadata extracts selectOptions from selectEnum using toString`() {
        val metadata = service.buildEntityMetadata(EnumEntity::class.java)
        val priorityField = metadata.fields.find { it.name == "priority" }!!
        assertEquals("SELECT", priorityField.renderer)
        assertEquals(listOf("Low", "Medium", "High"), priorityField.selectOptions)
    }

    @Test
    fun `buildEntityMetadata selectEnum takes precedence over selectOptions`() {
        val metadata = service.buildEntityMetadata(EnumEntity::class.java)
        val priorityField = metadata.fields.find { it.name == "priority" }!!
        // Options are derived from enum toString(), not from any hardcoded strings
        assertEquals(TestPriority.values().map { it.toString() }, priorityField.selectOptions)
    }

    @Test
    fun `buildEntityMetadata label and icon from annotation`() {
        val metadata = service.buildEntityMetadata(TestEntity::class.java)
        assertEquals("Test Entity", metadata.label)
        assertEquals("test", metadata.icon)
    }

    @Test
    fun `buildEntityMetadata extracts maxLength from Column annotation`() {
        val metadata = service.buildEntityMetadata(ValidationEntity::class.java)
        val codeField = metadata.fields.find { it.name == "code" }!!
        assertEquals(5, codeField.maxLength)
    }

    @Test
    fun `buildEntityMetadata maxLength is 0 when Column has default length`() {
        val metadata = service.buildEntityMetadata(ValidationEntity::class.java)
        val descField = metadata.fields.find { it.name == "description" }!!
        assertEquals(0, descField.maxLength)
    }

    @Test
    fun `buildEntityMetadata extracts regex pattern and message from Regex annotation`() {
        val metadata = service.buildEntityMetadata(ValidationEntity::class.java)
        val phoneField = metadata.fields.find { it.name == "phone" }!!
        assertEquals("""^\+?[\d\s\-]{7,20}$""", phoneField.regex)
        assertEquals("Nieprawidłowy numer", phoneField.regexMessage)
    }

    @Test
    fun `buildEntityMetadata regex is empty when Regex annotation is absent`() {
        val metadata = service.buildEntityMetadata(ValidationEntity::class.java)
        val codeField = metadata.fields.find { it.name == "code" }!!
        assertEquals("", codeField.regex)
        assertEquals("", codeField.regexMessage)
    }

    @Test
    fun `buildEntityMetadata combines maxLength and regex on same field`() {
        val metadata = service.buildEntityMetadata(ValidationEntity::class.java)
        val phoneField = metadata.fields.find { it.name == "phone" }!!
        assertEquals(20, phoneField.maxLength)
        assertTrue(phoneField.regex.isNotEmpty())
    }

    @Test
    fun `buildEntityMetadata populates relationMeta for RELATION field`() {
        val metadata = service.buildEntityMetadata(RelationEntity::class.java)
        val field = metadata.fields.find { it.name == "relatedId" }!!
        assertNotNull(field.relationMeta)
        val rm = field.relationMeta!!
        assertEquals("RelationTarget", rm.targetEntity)
        assertEquals("name", rm.labelField)
        assertEquals("id", rm.valueField)
        assertEquals(listOf("name"), rm.displayFields)
        assertEquals(listOf("name"), rm.searchFields)
        assertTrue(rm.editable)
        assertFalse(rm.inlineEdit)
    }

    @Test
    fun `buildEntityMetadata populates relationMeta for RELATION_LIST field`() {
        val metadata = service.buildEntityMetadata(RelationEntity::class.java)
        val field = metadata.fields.find { it.name == "children" }!!
        assertNotNull(field.relationMeta)
        val rm = field.relationMeta!!
        assertEquals("ChildItem", rm.targetEntity)
        assertTrue(rm.inlineEdit)
        assertEquals(50, rm.maxItems)
        assertEquals(listOf("id"), rm.displayFields)
    }

    @Test
    fun `buildEntityMetadata relationMeta is null for non-relation fields`() {
        val metadata = service.buildEntityMetadata(RelationEntity::class.java)
        val nameField = metadata.fields.find { it.name == "name" }!!
        assertNull(nameField.relationMeta)
    }

    @Test
    fun `buildEntityMetadata propagates filterQuery from PortalLookup`() {
        val metadata = service.buildEntityMetadata(FilteredRelationEntity::class.java)
        val countryField = metadata.fields.find { it.name == "countryId" }!!
        assertNotNull(countryField.relationMeta)
        assertEquals("e.isActive = true", countryField.relationMeta!!.filterQuery)
    }

    @Test
    fun `buildEntityMetadata filterQuery is empty when not set in PortalLookup`() {
        val metadata = service.buildEntityMetadata(RelationEntity::class.java)
        val relatedField = metadata.fields.find { it.name == "relatedId" }!!
        assertNotNull(relatedField.relationMeta)
        assertEquals("", relatedField.relationMeta!!.filterQuery)
    }

    // ─── Entity description tests ─────────────────────────────────────────────

    @Test
    fun `buildEntityMetadata propagates description from PortalEntity`() {
        val metadata = service.buildEntityMetadata(DescribedEntity::class.java)
        assertEquals("An entity with a longer description for tooltip display", metadata.description)
    }

    @Test
    fun `buildEntityMetadata has empty description when not set`() {
        val metadata = service.buildEntityMetadata(TestEntity::class.java)
        assertEquals("", metadata.description)
    }

    // ─── displayExpression tests ──────────────────────────────────────────────

    @Test
    fun `buildEntityMetadata propagates displayExpression from PortalField`() {
        val metadata = service.buildEntityMetadata(ExpressionEntity::class.java)
        val fullNameField = metadata.fields.find { it.name == "fullName" }!!
        assertEquals("\${firstName} \${lastName}", fullNameField.displayExpression)
    }

    @Test
    fun `buildEntityMetadata has empty displayExpression when not set`() {
        val metadata = service.buildEntityMetadata(TestEntity::class.java)
        val nameField = metadata.fields.find { it.name == "name" }!!
        assertEquals("", nameField.displayExpression)
    }

    // ─── Min / Max / DefaultValue propagation tests ──────────────────────────

    @Test
    fun `buildEntityMetadata propagates min and max from PortalField`() {
        val metadata = service.buildEntityMetadata(MinMaxEntity::class.java)
        val ratingField = metadata.fields.find { it.name == "rating" }!!
        assertEquals(1.0, ratingField.min)
        assertEquals(5.0, ratingField.max)
    }

    @Test
    fun `buildEntityMetadata propagates decimal min and max`() {
        val metadata = service.buildEntityMetadata(MinMaxEntity::class.java)
        val priceField = metadata.fields.find { it.name == "price" }!!
        assertEquals(0.0, priceField.min)
        assertEquals(9999.99, priceField.max)
    }

    @Test
    fun `buildEntityMetadata has null min max when not set`() {
        val metadata = service.buildEntityMetadata(MinMaxEntity::class.java)
        val nameField = metadata.fields.find { it.name == "name" }!!
        assertNull(nameField.min)
        assertNull(nameField.max)
    }

    @Test
    fun `buildEntityMetadata propagates defaultValue from PortalField`() {
        val metadata = service.buildEntityMetadata(MinMaxEntity::class.java)
        val statusField = metadata.fields.find { it.name == "status" }!!
        assertEquals("ACTIVE", statusField.defaultValue)
    }

    @Test
    fun `buildEntityMetadata propagates numeric defaultValue`() {
        val metadata = service.buildEntityMetadata(MinMaxEntity::class.java)
        val countField = metadata.fields.find { it.name == "count" }!!
        assertEquals("10", countField.defaultValue)
        assertEquals(0.0, countField.min)
    }

    @Test
    fun `buildEntityMetadata has empty defaultValue when not set`() {
        val metadata = service.buildEntityMetadata(MinMaxEntity::class.java)
        val nameField = metadata.fields.find { it.name == "name" }!!
        assertEquals("", nameField.defaultValue)
    }

    // ─── Soft Delete propagation tests ───────────────────────────────────────

    @Test
    fun `buildEntityMetadata propagates softDelete true from PortalEntity`() {
        val metadata = service.buildEntityMetadata(SoftDeleteEntity::class.java)
        assertTrue(metadata.softDelete)
    }

    @Test
    fun `buildEntityMetadata has softDelete false by default`() {
        val metadata = service.buildEntityMetadata(TestEntity::class.java)
        assertFalse(metadata.softDelete)
    }

    // ─── Audit Log propagation tests ─────────────────────────────────────────

    @Test
    fun `buildEntityMetadata propagates auditLog true from PortalEntity`() {
        val metadata = service.buildEntityMetadata(AuditEntity::class.java)
        assertTrue(metadata.auditLog)
    }

    @Test
    fun `buildEntityMetadata has auditLog false by default`() {
        val metadata = service.buildEntityMetadata(TestEntity::class.java)
        assertFalse(metadata.auditLog)
    }

    // ─── dependsOn (cascading select) tests ───────────────────────────────────

    @Test
    fun `buildEntityMetadata propagates dependsOn from PortalLookup`() {
        val metadata = service.buildEntityMetadata(CascadingRelationEntity::class.java)
        val cityField = metadata.fields.find { it.name == "cityId" }!!
        assertNotNull(cityField.relationMeta)
        assertEquals("countryId", cityField.relationMeta!!.dependsOn)
    }

    @Test
    fun `buildEntityMetadata dependsOn is empty when not set in PortalLookup`() {
        val metadata = service.buildEntityMetadata(CascadingRelationEntity::class.java)
        val countryField = metadata.fields.find { it.name == "countryId" }!!
        assertNotNull(countryField.relationMeta)
        assertEquals("", countryField.relationMeta!!.dependsOn)
    }

    // ─── orderBy propagation tests ────────────────────────────────────────────

    @Test
    fun `buildEntityMetadata propagates orderBy from PortalRelation`() {
        val metadata = service.buildEntityMetadata(OrderByRelationEntity::class.java)
        val categoryField = metadata.fields.find { it.name == "categoryId" }!!
        assertNotNull(categoryField.relationMeta)
        assertEquals("name ASC", categoryField.relationMeta!!.orderBy)
    }

    @Test
    fun `buildEntityMetadata orderBy is empty when not set in PortalRelation`() {
        val metadata = service.buildEntityMetadata(OrderByRelationEntity::class.java)
        val tagField = metadata.fields.find { it.name == "tagId" }!!
        assertNotNull(tagField.relationMeta)
        assertEquals("", tagField.relationMeta!!.orderBy)
    }

    @Test
    fun `buildEntityMetadata propagates complex field dependencies`() {
        val metadata = service.buildEntityMetadata(DependentFieldEntity::class.java)
        val levelField = metadata.fields.find { it.name == "level" }!!
        val scoreField = metadata.fields.find { it.name == "score" }!!

        assertEquals(1, levelField.dependencies.size)
        assertEquals("SHOW", levelField.dependencies.first().visibility)
        assertEquals(listOf("MEDIUM", "HIGH"), levelField.dependencies.first().allowedValues)
        val levelCondition = levelField.dependencies.first().condition
        assertTrue(levelCondition is DependencyConditionMetadata.Leaf)
        levelCondition as DependencyConditionMetadata.Leaf
        assertEquals("mode", levelCondition.field)
        assertEquals("ADVANCED", levelCondition.value)

        assertEquals("STATIC", scoreField.dependencies.first().min?.type)
        assertEquals(10.0, scoreField.dependencies.first().min?.value)
        assertEquals("STATIC", scoreField.dependencies.first().max?.type)
        assertEquals(50.0, scoreField.dependencies.first().max?.value)
    }

    @Test
    fun `buildEntityMetadata fails fast for dependency referencing missing field`() {
        val ex = assertThrows<IllegalStateException> {
            service.buildEntityMetadata(InvalidDependencyEntity::class.java)
        }

        assertTrue(ex.message!!.contains("missingField"))
    }

    @Test
    fun `buildEntityMetadata fails fast for mixed typed and JSON dependency condition`() {
        val ex = assertThrows<IllegalStateException> {
            service.buildEntityMetadata(InvalidMixedDependencyEntity::class.java)
        }

        assertTrue(ex.message!!.contains("typed condition"))
    }

    // ─── @PortalSecurity / securityRoles propagation ──────────────────────────

    @Test
    fun `buildEntityMetadata sets securityRoles to null when @PortalSecurity is absent`() {
        val metadata = service.buildEntityMetadata(TestEntity::class.java)
        assertNull(metadata.securityRoles,
            "Expected securityRoles to be null when @PortalSecurity is not present")
    }

    @Test
    fun `buildEntityMetadata propagates securityRoles from @PortalSecurity`() {
        val metadata = service.buildEntityMetadata(SecuredEntity::class.java)
        assertNotNull(metadata.securityRoles)
        with(metadata.securityRoles!!) {
            assertEquals(listOf("viewer", "admin"), viewRoles)
            assertEquals(listOf("editor", "admin"), editRoles)
            assertEquals(listOf("admin"), deleteRoles)
            assertEquals(listOf("admin"), actionRoles)
        }
    }

    @Test
    fun `buildEntityMetadata preserves empty role arrays from @PortalSecurity`() {
        val metadata = service.buildEntityMetadata(PartiallySecuredEntity::class.java)
        assertNotNull(metadata.securityRoles)
        with(metadata.securityRoles!!) {
            assertEquals(emptyList<String>(), viewRoles)   // empty = unrestricted
            assertEquals(listOf("editor"), editRoles)
            assertEquals(listOf("admin"), deleteRoles)
            assertEquals(emptyList<String>(), actionRoles) // empty = unrestricted
        }
    }
}

// ─── Security test fixtures ───────────────────────────────────────────────────

@PortalSecurity(
    viewRoles = ["viewer", "admin"],
    editRoles = ["editor", "admin"],
    deleteRoles = ["admin"],
    actionRoles = ["admin"]
)
@PortalEntity(label = "Secured Entity", module = "Test Module", order = 99)
class SecuredEntity {
    @PortalField(label = "Name", order = 1)
    var name: String = ""
}

@PortalSecurity(
    viewRoles = [],
    editRoles = ["editor"],
    deleteRoles = ["admin"],
    actionRoles = []
)
@PortalEntity(label = "Partially Secured Entity", module = "Test Module", order = 100)
class PartiallySecuredEntity {
    @PortalField(label = "Code", order = 1)
    var code: String = ""
}
