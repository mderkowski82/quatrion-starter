package com.example.portal.config

import com.example.portal.entity.Product
import dev.quatrion.portal.config.*
import io.quarkus.runtime.StartupEvent
import jakarta.annotation.PostConstruct
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.jboss.logging.Logger

@ApplicationScoped
class AppModuleConfig : PortalModuleConfig() {

    private val log = Logger.getLogger(AppModuleConfig::class.java)

    /**
     * Wymusza inicjalizację tego beana przed beanami biblioteki runtime (np. LicenseVerifier).
     * Priorytet 100 gwarantuje wykonanie przed domyślnym priorytetem (APPLICATION = 2000).
     */
    fun onStart(@Observes @Priority(100) event: StartupEvent) {
        // Inicjalizacja triggerowana przez StartupEvent – @PostConstruct zostaje wywołany wcześniej przez CDI
    }

    @PostConstruct
    fun init() {
        // This method is called after the bean is constructed and dependencies are injected.
        // You can perform any additional initialization here if needed.
        log.info("AppModuleConfig initialized with ${modules().size} module(s).")
    }

    override fun modules() = listOf(
        ModuleDef(
            name = "Catalog", label = "Katalog", icon = "package", order = 1,
            defaultEntity = Product::class.java,
            entities = listOf(EntityRef(entityClass = Product::class.java, group = "Produkty", order = 1))
        )
    )
}

