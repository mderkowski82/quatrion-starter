package dev.acme.portal

import dev.quatrion.portal.config.EntityRef
import dev.quatrion.portal.config.ModuleDef
import dev.quatrion.portal.config.PortalModuleConfig
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative

@Alternative
@Priority(1)
@ApplicationScoped
class TestPortalModuleConfig : PortalModuleConfig() {
    override fun modules() = listOf(
        ModuleDef(
            name = "TestModule",
            label = "Test Module",
            icon = "folder",
            order = 1,
            defaultEntity = TestEntity::class.java,
            entities = listOf(
                EntityRef(entityClass = TestEntity::class.java, group = "Group A", order = 1),
                EntityRef(entityClass = FlatEntity::class.java, order = 2)
            )
        )
    )
}
