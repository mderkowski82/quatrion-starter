package dev.acme.portal

import dev.quatrion.portal.annotation.PortalEntity
import dev.quatrion.portal.annotation.PortalField
import dev.quatrion.portal.annotation.RendererType
import dev.quatrion.portal.model.EntityData
import dev.quatrion.portal.service.EntityMapper
import dev.quatrion.portal.service.FilterParser
import dev.quatrion.portal.service.GenericCrudService
import jakarta.persistence.Transient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

// ── Test fixtures ─────────────────────────────────────────────────────────────

/** Minimal entity with all supported field types for serialization / deserialization tests. */
@PortalEntity(label = "Simple Test", module = "Test")
class SimpleTestEntity {
    var id: Long = 0
    var name: String = ""
    var count: Int = 0
    var price: Double = 0.0
    var active: Boolean = false
    var nullable: Long? = null
}

enum class TestStatus { OPEN, CLOSED;
    override fun toString() = name.lowercase().replaceFirstChar { it.uppercase() }
}

@PortalEntity(label = "Enum Test", module = "Test")
class EnumTestEntity {
    var id: Long = 0
    var status: TestStatus? = null
}

@PortalEntity(label = "Transient Test", module = "Test")
class TransientTestEntity {
    var id: Long = 0
    var name: String = ""

    /** JPA @Transient annotation – NOT a Java transient field; entityToMap must still include it. */
    @Transient
    var computed: String? = null
}

/**
 * Entity with a truly Java-transient field using @kotlin.jvm.Transient (no @JvmField).
 * @kotlin.jvm.Transient sets the Java `transient` modifier on the backing field,
 * which causes entityToMap to skip it (Modifier.isTransient == true).
 *
 * NOTE: Combining @JvmField + @jakarta.persistence.Transient does NOT set the Java
 * transient bit — @jakarta.persistence.Transient is a JPA marker only.  To get a
 * genuinely transient JVM field in Kotlin you need @kotlin.jvm.Transient.
 */
@PortalEntity(label = "Kotlin Transient Test", module = "Test")
class KotlinTransientEntity {
    var id: Long = 0
    @kotlin.jvm.Transient   // sets the Java `transient` modifier → excluded by entityToMap
    var secret: String = "hidden"
}

// ── Test class ────────────────────────────────────────────────────────────────

class GenericCrudServiceUnitTest {

    private val service = GenericCrudService(
        filterParser = FilterParser(),
        metadataService = TODO(),
        entityRegistry = TODO()
    )
    private val mapper = EntityMapper()

    // ── entityToMap ───────────────────────────────────────────────────────────

    @Test
    fun `entityToMap serializes all non-transient fields`() {
        val entity = SimpleTestEntity().apply {
            id = 1L; name = "Alice"; count = 5; price = 9.99; active = true
        }
        val map = mapper.entityToMap(entity)
        assertEquals(1L, map["id"])
        assertEquals("Alice", map["name"])
        assertEquals(5, map["count"])
        assertEquals(9.99, map["price"])
        assertEquals(true, map["active"])
    }

    @Test
    fun `entityToMap includes null values for nullable fields`() {
        val entity = SimpleTestEntity()
        val map = mapper.entityToMap(entity)
        assertTrue(map.containsKey("nullable"))
        assertNull(map["nullable"])
    }

    @Test
    fun `entityToMap converts enum to its toString representation`() {
        val entity = EnumTestEntity().apply { status = TestStatus.OPEN }
        val map = mapper.entityToMap(entity)
        assertEquals("Open", map["status"])
    }

    @Test
    fun `entityToMap includes Jakarta @Transient annotated fields`() {
        // @jakarta.persistence.Transient does NOT set the Java transient bit,
        // so entityToMap must include the field.
        val entity = TransientTestEntity().apply { id = 10; name = "X"; computed = "calc" }
        val map = mapper.entityToMap(entity)
        assertTrue(map.containsKey("computed"), "Jakarta @Transient field must be included")
        assertEquals("calc", map["computed"])
    }

    @Test
    fun `entityToMap excludes Java-transient fields (set by @kotlin_jvm_Transient)`() {
        val entity = KotlinTransientEntity().apply { id = 1 }
        val map = mapper.entityToMap(entity)
        // Kotlin @JvmField @Transient sets the Java transient bit; should be excluded
        assertFalse(map.containsKey("secret"), "Java-transient field must be excluded")
    }

    @Test
    fun `entityToMap skips static companion fields`() {
        val entity = SimpleTestEntity()
        val map = mapper.entityToMap(entity)
        // No Kotlin companion / static fields expected in SimpleTestEntity
        assertTrue(map.fields().keys.none { it.contains("Companion") || it.startsWith("$") })
    }

    // ── mapToEntity ───────────────────────────────────────────────────────────

    @Test
    fun `mapToEntity sets string field from map`() {
        val entity = SimpleTestEntity()
        mapper.mapToEntity(entity, EntityData(mapOf("name" to "Bob")), SimpleTestEntity::class.java)
        assertEquals("Bob", entity.name)
    }

    @Test
    fun `mapToEntity coerces numeric string to Long`() {
        val entity = SimpleTestEntity()
        mapper.mapToEntity(entity, EntityData(mapOf("id" to "42")), SimpleTestEntity::class.java)
        assertEquals(42L, entity.id)
    }

    @Test
    fun `mapToEntity coerces numeric string to Int`() {
        val entity = SimpleTestEntity()
        mapper.mapToEntity(entity, EntityData(mapOf("count" to "7")), SimpleTestEntity::class.java)
        assertEquals(7, entity.count)
    }

    @Test
    fun `mapToEntity coerces numeric string to Double`() {
        val entity = SimpleTestEntity()
        mapper.mapToEntity(entity, EntityData(mapOf("price" to "3.14")), SimpleTestEntity::class.java)
        assertEquals(3.14, entity.price, 1e-9)
    }

    @Test
    fun `mapToEntity coerces string to Boolean`() {
        val entity = SimpleTestEntity()
        mapper.mapToEntity(entity, EntityData(mapOf("active" to "true")), SimpleTestEntity::class.java)
        assertEquals(true, entity.active)
    }

    @Test
    fun `mapToEntity coerces Boolean directly`() {
        val entity = SimpleTestEntity()
        mapper.mapToEntity(entity, EntityData(mapOf("active" to false)), SimpleTestEntity::class.java)
        assertEquals(false, entity.active)
    }

    @Test
    fun `mapToEntity sets nullable Long from number`() {
        val entity = SimpleTestEntity()
        mapper.mapToEntity(entity, EntityData(mapOf("nullable" to 99L)), SimpleTestEntity::class.java)
        assertEquals(99L, entity.nullable)
    }

    @Test
    fun `mapToEntity sets nullable Long to null`() {
        val entity = SimpleTestEntity().apply { nullable = 5L }
        mapper.mapToEntity(entity, EntityData(mapOf("nullable" to null)), SimpleTestEntity::class.java)
        assertNull(entity.nullable)
    }

    @Test
    fun `mapToEntity skips unknown keys`() {
        val entity = SimpleTestEntity().apply { name = "original" }
        mapper.mapToEntity(entity, EntityData(mapOf("nonExistentField" to "x")), SimpleTestEntity::class.java)
        assertEquals("original", entity.name)
    }

    @Test
    fun `mapToEntity skips multiple fields and sets only known ones`() {
        val entity = SimpleTestEntity()
        mapper.mapToEntity(
            entity,
            EntityData(mapOf("name" to "Charlie", "unknownA" to 1, "unknownB" to "x")),
            SimpleTestEntity::class.java
        )
        assertEquals("Charlie", entity.name)
    }

    @Test
    fun `mapToEntity coerces enum by name`() {
        val entity = EnumTestEntity()
        mapper.mapToEntity(entity, EntityData(mapOf("status" to "OPEN")), EnumTestEntity::class.java)
        assertEquals(TestStatus.OPEN, entity.status)
    }

    @Test
    fun `mapToEntity coerces enum by toString`() {
        val entity = EnumTestEntity()
        // TestStatus.toString() returns "Open" (lowercase first char), not "OPEN"
        mapper.mapToEntity(entity, EntityData(mapOf("status" to "Open")), EnumTestEntity::class.java)
        assertEquals(TestStatus.OPEN, entity.status)
    }

    @Test
    fun `mapToEntity sets enum to null when value is null`() {
        val entity = EnumTestEntity().apply { status = TestStatus.CLOSED }
        mapper.mapToEntity(entity, EntityData(mapOf("status" to null)), EnumTestEntity::class.java)
        assertNull(entity.status)
    }

    // ── entityToMap round-trip ────────────────────────────────────────────────

    @Test
    fun `entityToMap then mapToEntity round-trips primitive values`() {
        val original = SimpleTestEntity().apply {
            id = 5L; name = "roundtrip"; count = 3; price = 1.5; active = true
        }
        val map = mapper.entityToMap(original)
        val restored = SimpleTestEntity()
        mapper.mapToEntity(restored, map, SimpleTestEntity::class.java)
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.count, restored.count)
        assertEquals(original.price, restored.price)
        assertEquals(original.active, restored.active)
    }

    @Test
    fun `entityToMap then mapToEntity round-trips enum value`() {
        val original = EnumTestEntity().apply { id = 1L; status = TestStatus.CLOSED }
        val map = mapper.entityToMap(original)
        val restored = EnumTestEntity()
        mapper.mapToEntity(restored, map, EnumTestEntity::class.java)
        assertEquals(TestStatus.CLOSED, restored.status)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ── Regression: Kotlin `var` backing fields are `final` on JVM ──────────
    //    Removing Modifier.isFinal check was the key fix — these tests guard it.
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapToEntity writes all var fields despite JVM final modifier`() {
        // Kotlin var fields have private final backing fields on JVM.
        // mapToEntity must NOT skip them via Modifier.isFinal check.
        val entity = SimpleTestEntity()
        mapper.mapToEntity(
            entity,
            EntityData(mapOf("id" to 1L, "name" to "FinalTest", "count" to 42, "price" to 3.14, "active" to true)),
            SimpleTestEntity::class.java
        )
        assertEquals(1L, entity.id, "id field (Long var) must be written")
        assertEquals("FinalTest", entity.name, "name field (String var) must be written")
        assertEquals(42, entity.count, "count field (Int var) must be written")
        assertEquals(3.14, entity.price, 1e-9, "price field (Double var) must be written")
        assertEquals(true, entity.active, "active field (Boolean var) must be written")
    }

    @Test
    fun `mapToEntity does not skip nullable Long field`() {
        val entity = SimpleTestEntity()
        mapper.mapToEntity(entity, EntityData(mapOf("nullable" to 7)), SimpleTestEntity::class.java)
        assertEquals(7L, entity.nullable, "nullable Long? field must be written")
    }

    // ── Regression: update must use detached instance + merge ─────────────────
    // Writing to backing fields of a Hibernate managed proxy via reflection does NOT
    // trigger dirty-check — Hibernate silently skips the UPDATE.
    // Fix: build a fresh detached instance and call merge() on it.
    // This test verifies that mapToEntity on a brand-new (detached) instance
    // correctly carries all fields — simulating what update() now does.

    @Test
    fun `mapToEntity on fresh instance carries all fields for merge (update regression)`() {
        // Simulate what update() does: create detached, populate, ready for merge.
        val detached = CustomerLikeEntity()
        val payload = EntityData(mapOf(
            "id"            to 1L,
            "name"          to "Updated Name",
            "birthDate"     to "1982-08-26",   // ← the field that was not saved before
            "registeredAt"  to "2026-08-26T12:12",
            "isActive"      to false,
            "customerType"  to "VIP",
            "creditLimit"   to 5000.0,
            "loyaltyPoints" to 42,
            "countryId"     to 1,
            "categoryId"    to 2,
            "tags"          to "VIP,PREMIUM"
        ))
        mapper.mapToEntity(detached, payload, CustomerLikeEntity::class.java)

        // All fields on the detached instance must be populated — merge() will then
        // carry them to the DB via a full UPDATE statement.
        assertEquals(1L,            detached.id)
        assertEquals("Updated Name",detached.name)
        assertEquals("1982-08-26",  detached.birthDate,   "birthDate must be in detached instance")
        assertEquals("2026-08-26T12:12", detached.registeredAt)
        assertFalse(detached.isActive)
        assertEquals(CustomerLikeType.VIP, detached.customerType)
        assertEquals(5000.0,        detached.creditLimit, 1e-9)
        assertEquals(42,            detached.loyaltyPoints)
        assertEquals(1L,            detached.countryId)
        assertEquals(2L,            detached.categoryId)
        assertEquals("VIP,PREMIUM", detached.tags)
    }

    @Test
    fun `update strategy - id injected when missing from payload`() {
        // update() adds "id" to data if not present — verify mapToEntity handles it
        val detached = CustomerLikeEntity()
        val id = 99L
        val dataWithoutId = EntityData(mapOf("name" to "NoId", "birthDate" to "2000-01-01"))
        val fullData = if (dataWithoutId.containsKey("id")) dataWithoutId else dataWithoutId + ("id" to id)
        mapper.mapToEntity(detached, fullData, CustomerLikeEntity::class.java)
        assertEquals(99L,         detached.id,        "id must be injected when missing")
        assertEquals("NoId",      detached.name)
        assertEquals("2000-01-01",detached.birthDate)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ── DemoCustomer field-type coverage ─────────────────────────────────────
    //    Tests one scenario per renderer type used in DemoCustomer.
    //    Fixture: CustomerLikeEntity (mirrors DemoCustomer field types).
    // ════════════════════════════════════════════════════════════════════════════

    // ── TEXT ─────────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer TEXT - name is set from String`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("name" to "Marek Derkowski")), CustomerLikeEntity::class.java)
        assertEquals("Marek Derkowski", e.name)
    }

    // ── TEXTAREA ─────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer TEXTAREA - notes is set from String`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("notes" to "Some long note\nline 2")), CustomerLikeEntity::class.java)
        assertEquals("Some long note\nline 2", e.notes)
    }

    // ── NUMBER ───────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer NUMBER - loyaltyPoints set from Int`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("loyaltyPoints" to 150)), CustomerLikeEntity::class.java)
        assertEquals(150, e.loyaltyPoints)
    }

    @Test
    fun `DemoCustomer NUMBER - loyaltyPoints set from String`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("loyaltyPoints" to "99")), CustomerLikeEntity::class.java)
        assertEquals(99, e.loyaltyPoints)
    }

    // ── DECIMAL ──────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer DECIMAL - creditLimit set from Double`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("creditLimit" to 9999.99)), CustomerLikeEntity::class.java)
        assertEquals(9999.99, e.creditLimit, 1e-9)
    }

    @Test
    fun `DemoCustomer DECIMAL - creditLimit set from Int (JSON integer becomes Int in Jackson)`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("creditLimit" to 500)), CustomerLikeEntity::class.java)
        assertEquals(500.0, e.creditLimit, 1e-9)
    }

    @Test
    fun `DemoCustomer DECIMAL - creditLimit set from String`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("creditLimit" to "1234.56")), CustomerLikeEntity::class.java)
        assertEquals(1234.56, e.creditLimit, 1e-9)
    }

    // ── DATE (stored as ISO String) ───────────────────────────────────────────

    @Test
    fun `DemoCustomer DATE - birthDate set from ISO date string`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("birthDate" to "1982-08-26")), CustomerLikeEntity::class.java)
        assertEquals("1982-08-26", e.birthDate)
    }

    @Test
    fun `DemoCustomer DATE - birthDate set to empty string`() {
        val e = CustomerLikeEntity().apply { birthDate = "2000-01-01" }
        mapper.mapToEntity(e, EntityData(mapOf("birthDate" to "")), CustomerLikeEntity::class.java)
        assertEquals("", e.birthDate)
    }

    // ── DATETIME (stored as ISO String) ──────────────────────────────────────

    @Test
    fun `DemoCustomer DATETIME - registeredAt set from ISO datetime string`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("registeredAt" to "2026-08-26T12:12")), CustomerLikeEntity::class.java)
        assertEquals("2026-08-26T12:12", e.registeredAt)
    }

    // ── BOOLEAN (is-prefix field name) ────────────────────────────────────────

    @Test
    fun `DemoCustomer BOOLEAN - isActive set to false from Boolean`() {
        val e = CustomerLikeEntity().apply { isActive = true }
        mapper.mapToEntity(e, EntityData(mapOf("isActive" to false)), CustomerLikeEntity::class.java)
        assertFalse(e.isActive, "isActive must be overwritten to false")
    }

    @Test
    fun `DemoCustomer BOOLEAN - isActive set to true from String 'true'`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("isActive" to "true")), CustomerLikeEntity::class.java)
        assertTrue(e.isActive)
    }

    @Test
    fun `DemoCustomer BOOLEAN - isActive set to false from String 'false'`() {
        val e = CustomerLikeEntity().apply { isActive = true }
        mapper.mapToEntity(e, EntityData(mapOf("isActive" to "false")), CustomerLikeEntity::class.java)
        assertFalse(e.isActive)
    }

    // ── SELECT (enum) ─────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer SELECT - customerType set from enum name string`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("customerType" to "VIP")), CustomerLikeEntity::class.java)
        assertEquals(CustomerLikeType.VIP, e.customerType)
    }

    @Test
    fun `DemoCustomer SELECT - customerType set from enum toString`() {
        val e = CustomerLikeEntity()
        // CustomerLikeType.PREMIUM.toString() == "Premium"
        mapper.mapToEntity(e, EntityData(mapOf("customerType" to "Premium")), CustomerLikeEntity::class.java)
        assertEquals(CustomerLikeType.PREMIUM, e.customerType)
    }

    @Test
    fun `DemoCustomer SELECT - customerType set to null`() {
        val e = CustomerLikeEntity().apply { customerType = CustomerLikeType.VIP }
        mapper.mapToEntity(e, EntityData(mapOf("customerType" to null)), CustomerLikeEntity::class.java)
        assertNull(e.customerType)
    }

    // ── MULTI_SELECT (comma-separated String) ─────────────────────────────────

    @Test
    fun `DemoCustomer MULTI_SELECT - tags set from comma-separated string`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("tags" to "VIP,NEW,PREMIUM")), CustomerLikeEntity::class.java)
        assertEquals("VIP,NEW,PREMIUM", e.tags)
    }

    @Test
    fun `DemoCustomer MULTI_SELECT - tags set to empty string`() {
        val e = CustomerLikeEntity().apply { tags = "OLD" }
        mapper.mapToEntity(e, EntityData(mapOf("tags" to "")), CustomerLikeEntity::class.java)
        assertEquals("", e.tags)
    }

    // ── EMAIL ─────────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer EMAIL - email field is set`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("email" to "marek@example.com")), CustomerLikeEntity::class.java)
        assertEquals("marek@example.com", e.email)
    }

    @Test
    fun `DemoCustomer EMAIL - email cleared to empty string`() {
        val e = CustomerLikeEntity().apply { email = "old@x.com" }
        mapper.mapToEntity(e, EntityData(mapOf("email" to "")), CustomerLikeEntity::class.java)
        assertEquals("", e.email)
    }

    // ── URL ───────────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer URL - website field is set`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("website" to "https://quatrion.dev")), CustomerLikeEntity::class.java)
        assertEquals("https://quatrion.dev", e.website)
    }

    // ── PASSWORD ──────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer PASSWORD - password field is set`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("password" to "qwerty123")), CustomerLikeEntity::class.java)
        assertEquals("qwerty123", e.password)
    }

    // ── COLOR ─────────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer COLOR - favoriteColor field is set`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("favoriteColor" to "#ff0000")), CustomerLikeEntity::class.java)
        assertEquals("#ff0000", e.favoriteColor)
    }

    @Test
    fun `DemoCustomer COLOR - favoriteColor set to non-hex string`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("favoriteColor" to "czarny")), CustomerLikeEntity::class.java)
        assertEquals("czarny", e.favoriteColor)
    }

    // ── FILE ──────────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer FILE - avatar path is set`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("avatar" to "/uploads/photo.jpg")), CustomerLikeEntity::class.java)
        assertEquals("/uploads/photo.jpg", e.avatar)
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer JSON - extraData is set from JSON string`() {
        val e = CustomerLikeEntity()
        val json = """{"key":"value","num":42}"""
        mapper.mapToEntity(e, EntityData(mapOf("extraData" to json)), CustomerLikeEntity::class.java)
        assertEquals(json, e.extraData)
    }

    // ── CUSTOM ────────────────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer CUSTOM - customField is set`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("customField" to "custom-value")), CustomerLikeEntity::class.java)
        assertEquals("custom-value", e.customField)
    }

    // ── RELATION (Long?) ──────────────────────────────────────────────────────

    @Test
    fun `DemoCustomer RELATION - countryId set from Int (JSON integer)`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("countryId" to 1)), CustomerLikeEntity::class.java)
        assertEquals(1L, e.countryId)
    }

    @Test
    fun `DemoCustomer RELATION - countryId set from Long`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("countryId" to 1L)), CustomerLikeEntity::class.java)
        assertEquals(1L, e.countryId)
    }

    @Test
    fun `DemoCustomer RELATION - categoryId set from Double (edge case)`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("categoryId" to 2.0)), CustomerLikeEntity::class.java)
        assertEquals(2L, e.categoryId)
    }

    @Test
    fun `DemoCustomer RELATION - countryId set to null`() {
        val e = CustomerLikeEntity().apply { countryId = 5L }
        mapper.mapToEntity(e, EntityData(mapOf("countryId" to null)), CustomerLikeEntity::class.java)
        assertNull(e.countryId)
    }

    @Test
    fun `DemoCustomer RELATION - categoryId set from String number`() {
        val e = CustomerLikeEntity()
        mapper.mapToEntity(e, EntityData(mapOf("categoryId" to "3")), CustomerLikeEntity::class.java)
        assertEquals(3L, e.categoryId)
    }

    // ── Full payload round-trip ───────────────────────────────────────────────

    @Test
    fun `DemoCustomer full payload from frontend maps all fields correctly`() {
        val e = CustomerLikeEntity()
        val payload = EntityData(mapOf(
            "id"            to 1,
            "name"          to "Marek Derkowski",
            "customerType"  to "VIP",
            "isActive"      to false,
            "favoriteColor" to "czarny",
            "email"         to "",
            "phone"         to "",
            "website"       to "",
            "birthDate"     to "1982-08-26",
            "registeredAt"  to "2026-08-26T12:12",
            "countryId"     to 1,
            "creditLimit"   to 0.0,
            "loyaltyPoints" to 0,
            "tags"          to "",
            "categoryId"    to 1,
            "password"      to "qwerty123",
            "avatar"        to "",
            "extraData"     to "",
            "notes"         to "",
            "customField"   to ""
        ))

        mapper.mapToEntity(e, payload, CustomerLikeEntity::class.java)

        assertEquals(1L, e.id, "id must be set")
        assertEquals("Marek Derkowski", e.name, "name must be set")
        assertEquals(CustomerLikeType.VIP, e.customerType, "customerType enum must be coerced")
        assertFalse(e.isActive, "isActive must be false (is-prefix Boolean field)")
        assertEquals("czarny", e.favoriteColor, "favoriteColor must be set")
        assertEquals("", e.email, "email must be set (empty)")
        assertEquals("", e.phone, "phone must be set (empty)")
        assertEquals("", e.website, "website must be set (empty)")
        assertEquals("1982-08-26", e.birthDate, "birthDate must be set")
        assertEquals("2026-08-26T12:12", e.registeredAt, "registeredAt must be set")
        assertEquals(1L, e.countryId, "countryId (Long?) must be coerced from Int")
        assertEquals(0.0, e.creditLimit, 1e-9, "creditLimit must be set")
        assertEquals(0, e.loyaltyPoints, "loyaltyPoints must be set")
        assertEquals("", e.tags, "tags must be set")
        assertEquals(1L, e.categoryId, "categoryId (Long?) must be coerced from Int")
        assertEquals("qwerty123", e.password, "password must be set")
        assertEquals("", e.avatar, "avatar must be set")
        assertEquals("", e.extraData, "extraData must be set")
        assertEquals("", e.notes, "notes must be set")
        assertEquals("", e.customField, "customField must be set")
    }

    // ── entityToMap output for DemoCustomer-like entity ───────────────────────

    @Test
    fun `entityToMap serializes CustomerLikeEntity all fields`() {
        val e = CustomerLikeEntity().apply {
            id = 1L
            name = "Test"
            customerType = CustomerLikeType.PREMIUM
            isActive = false
            favoriteColor = "#000000"
            email = "t@t.com"
            phone = "+48 111 222 333"
            website = "https://t.com"
            birthDate = "1990-01-15"
            registeredAt = "2026-01-01T10:00"
            countryId = 2L
            creditLimit = 5000.0
            loyaltyPoints = 100
            tags = "VIP,PREMIUM"
            categoryId = 3L
            password = "secret"
            avatar = "/img/a.jpg"
            extraData = "{}"
            notes = "note"
            customField = "cf"
        }

        val map = mapper.entityToMap(e)

        assertEquals(1L, map["id"])
        assertEquals("Test", map["name"])
        assertEquals("Premium", map["customerType"])   // toString() of enum
        assertEquals(false, map["isActive"])
        assertEquals("#000000", map["favoriteColor"])
        assertEquals("t@t.com", map["email"])
        assertEquals("+48 111 222 333", map["phone"])
        assertEquals("https://t.com", map["website"])
        assertEquals("1990-01-15", map["birthDate"])
        assertEquals("2026-01-01T10:00", map["registeredAt"])
        assertEquals(2L, map["countryId"])
        assertEquals(5000.0, map["creditLimit"])
        assertEquals(100, map["loyaltyPoints"])
        assertEquals("VIP,PREMIUM", map["tags"])
        assertEquals(3L, map["categoryId"])
        assertEquals("secret", map["password"])
        assertEquals("/img/a.jpg", map["avatar"])
        assertEquals("{}", map["extraData"])
        assertEquals("note", map["notes"])
        assertEquals("cf", map["customField"])
    }

    @Test
    fun `entityToMap then mapToEntity full round-trip for CustomerLikeEntity`() {
        val original = CustomerLikeEntity().apply {
            id = 7L
            name = "Round Trip"
            customerType = CustomerLikeType.VIP
            isActive = false
            favoriteColor = "#123456"
            email = "rt@test.com"
            phone = "+48 600 000 000"
            website = "https://rt.com"
            birthDate = "1985-05-20"
            registeredAt = "2026-03-05T08:30"
            countryId = 10L
            creditLimit = 2500.0
            loyaltyPoints = 50
            tags = "REGULAR"
            categoryId = 4L
            password = "pass123"
            avatar = "/img/rt.jpg"
            extraData = """{"info":"test"}"""
            notes = "round-trip note"
            customField = "rtcf"
        }

        val map = mapper.entityToMap(original)
        val restored = CustomerLikeEntity()
        mapper.mapToEntity(restored, map, CustomerLikeEntity::class.java)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.customerType, restored.customerType)
        assertEquals(original.isActive, restored.isActive)
        assertEquals(original.favoriteColor, restored.favoriteColor)
        assertEquals(original.email, restored.email)
        assertEquals(original.phone, restored.phone)
        assertEquals(original.website, restored.website)
        assertEquals(original.birthDate, restored.birthDate)
        assertEquals(original.registeredAt, restored.registeredAt)
        assertEquals(original.countryId, restored.countryId)
        assertEquals(original.creditLimit, restored.creditLimit, 1e-9)
        assertEquals(original.loyaltyPoints, restored.loyaltyPoints)
        assertEquals(original.tags, restored.tags)
        assertEquals(original.categoryId, restored.categoryId)
        assertEquals(original.password, restored.password)
        assertEquals(original.avatar, restored.avatar)
        assertEquals(original.extraData, restored.extraData)
        assertEquals(original.notes, restored.notes)
        assertEquals(original.customField, restored.customField)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ── Extended type coercion tests (LocalDate, LocalDateTime, UUID, BigInteger)
    // ════════════════════════════════════════════════════════════════════════════

    // ── LocalDate ────────────────────────────────────────────────────────────

    @Test
    fun `mapToEntity coerces ISO date string to LocalDate`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("birthDate" to "2000-01-15")), ExtendedTypesEntity::class.java)
        assertEquals(LocalDate.of(2000, 1, 15), e.birthDate)
    }

    @Test
    fun `mapToEntity sets LocalDate to null from null`() {
        val e = ExtendedTypesEntity().apply { birthDate = LocalDate.now() }
        mapper.mapToEntity(e, EntityData(mapOf("birthDate" to null)), ExtendedTypesEntity::class.java)
        assertNull(e.birthDate)
    }

    @Test
    fun `mapToEntity sets LocalDate to null from blank string`() {
        val e = ExtendedTypesEntity().apply { birthDate = LocalDate.now() }
        mapper.mapToEntity(e, EntityData(mapOf("birthDate" to "")), ExtendedTypesEntity::class.java)
        assertNull(e.birthDate)
    }

    @Test
    fun `mapToEntity passes through LocalDate instance directly`() {
        val e = ExtendedTypesEntity()
        val date = LocalDate.of(2025, 6, 15)
        mapper.mapToEntity(e, EntityData(mapOf("birthDate" to date)), ExtendedTypesEntity::class.java)
        assertEquals(date, e.birthDate)
    }

    @Test
    fun `mapToEntity returns null for invalid LocalDate string`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("birthDate" to "not-a-date")), ExtendedTypesEntity::class.java)
        assertNull(e.birthDate)
    }

    // ── LocalDateTime ────────────────────────────────────────────────────────

    @Test
    fun `mapToEntity coerces ISO datetime string to LocalDateTime`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("createdAt" to "2026-03-24T10:30:00")), ExtendedTypesEntity::class.java)
        assertEquals(LocalDateTime.of(2026, 3, 24, 10, 30, 0), e.createdAt)
    }

    @Test
    fun `mapToEntity coerces ISO datetime without seconds to LocalDateTime`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("createdAt" to "2026-03-24T10:30")), ExtendedTypesEntity::class.java)
        assertEquals(LocalDateTime.of(2026, 3, 24, 10, 30), e.createdAt)
    }

    @Test
    fun `mapToEntity sets LocalDateTime to null from null`() {
        val e = ExtendedTypesEntity().apply { createdAt = LocalDateTime.now() }
        mapper.mapToEntity(e, EntityData(mapOf("createdAt" to null)), ExtendedTypesEntity::class.java)
        assertNull(e.createdAt)
    }

    @Test
    fun `mapToEntity sets LocalDateTime to null from blank string`() {
        val e = ExtendedTypesEntity().apply { createdAt = LocalDateTime.now() }
        mapper.mapToEntity(e, EntityData(mapOf("createdAt" to "")), ExtendedTypesEntity::class.java)
        assertNull(e.createdAt)
    }

    @Test
    fun `mapToEntity passes through LocalDateTime instance directly`() {
        val e = ExtendedTypesEntity()
        val dt = LocalDateTime.of(2026, 1, 1, 12, 0)
        mapper.mapToEntity(e, EntityData(mapOf("createdAt" to dt)), ExtendedTypesEntity::class.java)
        assertEquals(dt, e.createdAt)
    }

    @Test
    fun `mapToEntity returns null for invalid LocalDateTime string`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("createdAt" to "not-a-datetime")), ExtendedTypesEntity::class.java)
        assertNull(e.createdAt)
    }

    // ── UUID ─────────────────────────────────────────────────────────────────

    @Test
    fun `mapToEntity coerces UUID string to UUID`() {
        val e = ExtendedTypesEntity()
        val uuidStr = "550e8400-e29b-41d4-a716-446655440000"
        mapper.mapToEntity(e, EntityData(mapOf("externalId" to uuidStr)), ExtendedTypesEntity::class.java)
        assertEquals(UUID.fromString(uuidStr), e.externalId)
    }

    @Test
    fun `mapToEntity sets UUID to null from null`() {
        val e = ExtendedTypesEntity().apply { externalId = UUID.randomUUID() }
        mapper.mapToEntity(e, EntityData(mapOf("externalId" to null)), ExtendedTypesEntity::class.java)
        assertNull(e.externalId)
    }

    @Test
    fun `mapToEntity passes through UUID instance directly`() {
        val e = ExtendedTypesEntity()
        val uuid = UUID.randomUUID()
        mapper.mapToEntity(e, EntityData(mapOf("externalId" to uuid)), ExtendedTypesEntity::class.java)
        assertEquals(uuid, e.externalId)
    }

    @Test
    fun `mapToEntity returns null for invalid UUID string`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("externalId" to "not-a-uuid")), ExtendedTypesEntity::class.java)
        assertNull(e.externalId)
    }

    // ── BigInteger ───────────────────────────────────────────────────────────

    @Test
    fun `mapToEntity coerces string to BigInteger`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("bigNumber" to "999999999999999999")), ExtendedTypesEntity::class.java)
        assertEquals(BigInteger("999999999999999999"), e.bigNumber)
    }

    @Test
    fun `mapToEntity coerces Number to BigInteger`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("bigNumber" to 42L)), ExtendedTypesEntity::class.java)
        assertEquals(BigInteger.valueOf(42L), e.bigNumber)
    }

    @Test
    fun `mapToEntity sets BigInteger to null from null`() {
        val e = ExtendedTypesEntity().apply { bigNumber = BigInteger.TEN }
        mapper.mapToEntity(e, EntityData(mapOf("bigNumber" to null)), ExtendedTypesEntity::class.java)
        assertNull(e.bigNumber)
    }

    @Test
    fun `mapToEntity passes through BigInteger instance directly`() {
        val e = ExtendedTypesEntity()
        val big = BigInteger("123456789012345678901234567890")
        mapper.mapToEntity(e, EntityData(mapOf("bigNumber" to big)), ExtendedTypesEntity::class.java)
        assertEquals(big, e.bigNumber)
    }

    @Test
    fun `mapToEntity returns null for invalid BigInteger string`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("bigNumber" to "not-a-number")), ExtendedTypesEntity::class.java)
        assertNull(e.bigNumber)
    }

    // ── entityToMap for extended types ────────────────────────────────────────

    @Test
    fun `entityToMap serializes LocalDate field`() {
        val e = ExtendedTypesEntity().apply { birthDate = LocalDate.of(2000, 1, 15) }
        val map = mapper.entityToMap(e)
        assertEquals(LocalDate.of(2000, 1, 15), map["birthDate"])
    }

    @Test
    fun `entityToMap serializes LocalDateTime field`() {
        val e = ExtendedTypesEntity().apply { createdAt = LocalDateTime.of(2026, 3, 24, 10, 30) }
        val map = mapper.entityToMap(e)
        assertEquals(LocalDateTime.of(2026, 3, 24, 10, 30), map["createdAt"])
    }

    @Test
    fun `entityToMap serializes UUID field`() {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val e = ExtendedTypesEntity().apply { externalId = uuid }
        val map = mapper.entityToMap(e)
        assertEquals(uuid, map["externalId"])
    }

    @Test
    fun `entityToMap serializes BigInteger field`() {
        val e = ExtendedTypesEntity().apply { bigNumber = BigInteger("999") }
        val map = mapper.entityToMap(e)
        assertEquals(BigInteger("999"), map["bigNumber"])
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ── Instant coercion tests ───────────────────────────────────────────────
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapToEntity coerces ISO instant string to Instant`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("eventTime" to "2026-03-24T10:30:00Z")), ExtendedTypesEntity::class.java)
        assertEquals(Instant.parse("2026-03-24T10:30:00Z"), e.eventTime)
    }

    @Test
    fun `mapToEntity sets Instant to null from null`() {
        val e = ExtendedTypesEntity().apply { eventTime = Instant.now() }
        mapper.mapToEntity(e, EntityData(mapOf("eventTime" to null)), ExtendedTypesEntity::class.java)
        assertNull(e.eventTime)
    }

    @Test
    fun `mapToEntity sets Instant to null from blank string`() {
        val e = ExtendedTypesEntity().apply { eventTime = Instant.now() }
        mapper.mapToEntity(e, EntityData(mapOf("eventTime" to "")), ExtendedTypesEntity::class.java)
        assertNull(e.eventTime)
    }

    @Test
    fun `mapToEntity passes through Instant instance directly`() {
        val e = ExtendedTypesEntity()
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        mapper.mapToEntity(e, EntityData(mapOf("eventTime" to instant)), ExtendedTypesEntity::class.java)
        assertEquals(instant, e.eventTime)
    }

    @Test
    fun `mapToEntity returns null for invalid Instant string`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("eventTime" to "not-an-instant")), ExtendedTypesEntity::class.java)
        assertNull(e.eventTime)
    }

    @Test
    fun `entityToMap serializes Instant field`() {
        val instant = Instant.parse("2026-03-24T10:30:00Z")
        val e = ExtendedTypesEntity().apply { eventTime = instant }
        val map = mapper.entityToMap(e)
        assertEquals(instant, map["eventTime"])
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ── ZonedDateTime coercion tests ─────────────────────────────────────────
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapToEntity coerces ISO zoned datetime string to ZonedDateTime`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("meetingTime" to "2026-03-24T10:30:00+02:00[Europe/Warsaw]")), ExtendedTypesEntity::class.java)
        assertEquals(ZonedDateTime.parse("2026-03-24T10:30:00+02:00[Europe/Warsaw]"), e.meetingTime)
    }

    @Test
    fun `mapToEntity coerces offset datetime string to ZonedDateTime`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("meetingTime" to "2026-03-24T10:30:00+02:00")), ExtendedTypesEntity::class.java)
        assertNotNull(e.meetingTime)
        assertEquals(2026, e.meetingTime!!.year)
        assertEquals(3, e.meetingTime!!.monthValue)
        assertEquals(24, e.meetingTime!!.dayOfMonth)
    }

    @Test
    fun `mapToEntity sets ZonedDateTime to null from null`() {
        val e = ExtendedTypesEntity().apply { meetingTime = ZonedDateTime.now() }
        mapper.mapToEntity(e, EntityData(mapOf("meetingTime" to null)), ExtendedTypesEntity::class.java)
        assertNull(e.meetingTime)
    }

    @Test
    fun `mapToEntity sets ZonedDateTime to null from blank string`() {
        val e = ExtendedTypesEntity().apply { meetingTime = ZonedDateTime.now() }
        mapper.mapToEntity(e, EntityData(mapOf("meetingTime" to "")), ExtendedTypesEntity::class.java)
        assertNull(e.meetingTime)
    }

    @Test
    fun `mapToEntity passes through ZonedDateTime instance directly`() {
        val e = ExtendedTypesEntity()
        val zdt = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"))
        mapper.mapToEntity(e, EntityData(mapOf("meetingTime" to zdt)), ExtendedTypesEntity::class.java)
        assertEquals(zdt, e.meetingTime)
    }

    @Test
    fun `mapToEntity returns null for invalid ZonedDateTime string`() {
        val e = ExtendedTypesEntity()
        mapper.mapToEntity(e, EntityData(mapOf("meetingTime" to "not-a-zoned-datetime")), ExtendedTypesEntity::class.java)
        assertNull(e.meetingTime)
    }

    @Test
    fun `entityToMap serializes ZonedDateTime field`() {
        val zdt = ZonedDateTime.parse("2026-03-24T10:30:00+02:00[Europe/Warsaw]")
        val e = ExtendedTypesEntity().apply { meetingTime = zdt }
        val map = mapper.entityToMap(e)
        assertEquals(zdt, map["meetingTime"])
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ── Fields cache tests ───────────────────────────────────────────────────
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `entityToMap returns same results on repeated calls (cache consistency)`() {
        val e = SimpleTestEntity().apply { id = 1L; name = "CacheTest"; count = 5; price = 9.99; active = true }
        val map1 = mapper.entityToMap(e)
        val map2 = mapper.entityToMap(e)
        assertEquals(map1["id"], map2["id"])
        assertEquals(map1["name"], map2["name"])
        assertEquals(map1["count"], map2["count"])
        assertEquals(map1["price"], map2["price"])
        assertEquals(map1["active"], map2["active"])
    }

    @Test
    fun `mapToEntity works correctly after multiple calls (cache consistency)`() {
        // First call populates the cache
        val e1 = SimpleTestEntity()
        mapper.mapToEntity(e1, EntityData(mapOf("name" to "First")), SimpleTestEntity::class.java)
        assertEquals("First", e1.name)

        // Second call uses the cached fields
        val e2 = SimpleTestEntity()
        mapper.mapToEntity(e2, EntityData(mapOf("name" to "Second", "count" to 42)), SimpleTestEntity::class.java)
        assertEquals("Second", e2.name)
        assertEquals(42, e2.count)
    }

    // ── isSoftDelete ──────────────────────────────────────────────────────────

    @Test
    fun `isSoftDelete returns true for soft delete entity`() {
        assertTrue(service.isSoftDelete(SoftDeleteTestEntity::class.java))
    }

    @Test
    fun `isSoftDelete returns false for hard delete entity`() {
        assertFalse(service.isSoftDelete(HardDeleteTestEntity::class.java))
    }

    @Test
    fun `isSoftDelete returns false for entity without PortalEntity annotation`() {
        assertFalse(service.isSoftDelete(String::class.java))
    }

    @Test
    fun `entityToMap includes deleted field for soft delete entity`() {
        val entity = SoftDeleteTestEntity().apply {
            id = 1L; name = "Test"; deleted = false
        }
        val map = mapper.entityToMap(entity)
        assertEquals(1L, map["id"])
        assertEquals("Test", map["name"])
        assertEquals(false, map["deleted"])
    }

    @Test
    fun `entityToMap includes deleted=true for soft deleted entity`() {
        val entity = SoftDeleteTestEntity().apply {
            id = 2L; name = "Deleted"; deleted = true
        }
        val map = mapper.entityToMap(entity)
        assertEquals(true, map["deleted"])
    }

    // ── restore guard: non-softDelete entities ────────────────────────────────

    @Test
    fun `restore returns false for non-soft-delete entity`() = runBlocking {
        val result = service.restore(HardDeleteTestEntity::class.java, 1L)
        assertFalse(result, "restore should return false for entities without softDelete")
    }

    @Test
    fun `restore returns false for entity without PortalEntity annotation`() = runBlocking {
        val result = service.restore(String::class.java, 1L)
        assertFalse(result, "restore should return false for classes without @PortalEntity")
    }

    // ── batchUpdate ───────────────────────────────────────────────────────────

    @Test
    fun `batchUpdate returns 0 for empty ids list`() = runBlocking {
        val result = service.batchUpdate(SimpleTestEntity::class.java, emptyList(), "name", "test")
        assertEquals(0, result, "batchUpdate with empty ids must return 0")
    }

    @Test
    fun `batchUpdate throws for unknown field name`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.batchUpdate(SimpleTestEntity::class.java, listOf(1L), "nonExistentField", "x")
            }
        }
        assertTrue(exception.message!!.contains("nonExistentField"), "Exception message must contain field name")
        assertTrue(exception.message!!.contains("SimpleTestEntity"), "Exception message must contain entity name")
    }

    @Test
    fun `batchUpdate throws for field not present on entity`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.batchUpdate(SimpleTestEntity::class.java, listOf(1L, 2L), "birthDate", "2000-01-01")
            }
        }
        assertTrue(exception.message!!.contains("birthDate"))
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ── CustomerLikeEntity fixture ────────────────────────────────────────────────
//    Mirrors DemoCustomer field types covering all 17 renderer types:
//    TEXT, TEXTAREA, NUMBER, DECIMAL, DATE, DATETIME, BOOLEAN (is-prefix),
//    SELECT (enum), MULTI_SELECT (String), EMAIL, URL, PASSWORD, COLOR,
//    FILE, JSON, CUSTOM, RELATION×2 (Long?)
// ════════════════════════════════════════════════════════════════════════════════

enum class CustomerLikeType(val label: String) {
    VIP("VIP"), PREMIUM("Premium"), REGULAR("Regular");
    override fun toString() = label
}

@PortalEntity(label = "Customer Like", module = "Test")
class CustomerLikeEntity {
    // ID
    var id: Long = 0

    // TEXT
    var name: String = ""

    // SELECT (enum)
    var customerType: CustomerLikeType? = null

    // BOOLEAN with is-prefix  ← key regression case
    var isActive: Boolean = true

    // COLOR
    var favoriteColor: String = ""

    // EMAIL
    var email: String = ""

    // TEXT (phone)
    var phone: String = ""

    // URL
    var website: String = ""

    // DATE (stored as ISO String)
    var birthDate: String = ""

    // DATETIME (stored as ISO String)
    var registeredAt: String = ""

    // RELATION - Long? (FK to DemoCountry)
    var countryId: Long? = null

    // DECIMAL
    var creditLimit: Double = 0.0

    // NUMBER
    var loyaltyPoints: Int = 0

    // MULTI_SELECT (comma-separated String)
    var tags: String = ""

    // RELATION - Long? (FK to DemoCategory)
    var categoryId: Long? = null

    // PASSWORD
    var password: String = ""

    // FILE
    var avatar: String = ""

    // JSON
    var extraData: String = ""

    // TEXTAREA
    var notes: String = ""

    // CUSTOM
    var customField: String = ""
}

// ════════════════════════════════════════════════════════════════════════════════
// ── ExtendedTypesEntity fixture ──────────────────────────────────────────────
//    Tests new type coercions: LocalDate, LocalDateTime, UUID, BigInteger
// ════════════════════════════════════════════════════════════════════════════════

@PortalEntity(label = "Extended Types Test", module = "Test")
class ExtendedTypesEntity {
    var id: Long = 0
    var birthDate: LocalDate? = null
    var createdAt: LocalDateTime? = null
    var externalId: UUID? = null
    var bigNumber: BigInteger? = null
    var eventTime: Instant? = null
    var meetingTime: ZonedDateTime? = null
}

// ═══ Soft delete test fixtures ═══════════════════════════════════════════════

@PortalEntity(label = "Soft Delete Test", module = "Test", softDelete = true)
class SoftDeleteTestEntity {
    var id: Long = 0
    var name: String = ""
    var deleted: Boolean = false
}

@PortalEntity(label = "Hard Delete Test", module = "Test")
class HardDeleteTestEntity {
    var id: Long = 0
    var name: String = ""
}

