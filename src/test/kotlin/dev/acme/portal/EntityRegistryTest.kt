package dev.acme.portal

import dev.quatrion.portal.config.EntityRef
import dev.quatrion.portal.config.ModuleDef
import dev.quatrion.portal.config.PortalModuleConfig
import dev.quatrion.portal.service.EntityRegistry
import jakarta.ws.rs.NotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class EntityRegistryTest {

    private val config = object : PortalModuleConfig() {
        override fun modules() = listOf(
            ModuleDef(
                name = "TestModule",
                label = "Test Module",
                order = 1,
                defaultEntity = TestEntity::class.java,
                entities = listOf(
                    EntityRef(entityClass = TestEntity::class.java),
                    EntityRef(entityClass = FlatEntity::class.java)
                )
            )
        )
    }

    private val registry = EntityRegistry(MetadataServiceTest.mockInstance(config)).also { it.init() }

    @Test
    fun `getEntityClass returns correct class for registered entity`() {
        val clazz = registry.getEntityClass("TestEntity")
        assertEquals(TestEntity::class.java, clazz)
    }

    @Test
    fun `getEntityClass throws NotFoundException for unknown entity`() {
        assertThrows<NotFoundException> {
            registry.getEntityClass("NonExistentEntity")
        }
    }

    @Test
    fun `getAllEntityNames returns all registered entities`() {
        val names = registry.getAllEntityNames()
        assertTrue(names.contains("TestEntity"))
        assertTrue(names.contains("FlatEntity"))
        assertEquals(2, names.size)
    }
}
