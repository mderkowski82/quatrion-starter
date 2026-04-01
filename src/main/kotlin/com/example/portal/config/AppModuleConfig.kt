package com.example.portal.config

import com.example.portal.entity.*
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
        log.info("AppModuleConfig initialized with ${modules().size} module(s).")
    }

    override fun modules() = listOf(
        ModuleDef(
            name = "Library",
            label = "Biblioteka",
            icon = "book-open",
            order = 1,
            defaultEntity = Book::class.java,
            entities = listOf(
                // Słowniki — encje pomocnicze bez zakładek (płaskie formularze)
                EntityRef(entityClass = Genre::class.java,  group = "Słowniki",   order = 1),
                EntityRef(entityClass = Author::class.java, group = "Słowniki",   order = 2),
                // Katalog — centralna encja z zakładkami, akcjami i security
                EntityRef(entityClass = Book::class.java,   group = "Katalog",    order = 1),
                // Użytkownicy i operacje
                EntityRef(entityClass = Member::class.java, group = "Użytkownicy", order = 1),
                EntityRef(entityClass = Loan::class.java,   group = "Operacje",   order = 1)
            )
        )
    )
}

