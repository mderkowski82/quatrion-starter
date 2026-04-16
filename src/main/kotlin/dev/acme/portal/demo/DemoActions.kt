package dev.acme.portal.demo

import dev.quatrion.portal.model.ActionResult
import dev.quatrion.portal.model.EntityData
import io.quarkus.arc.Unremovable
import jakarta.enterprise.context.ApplicationScoped

// ─────────────────────────────────────────────────────────────
//  DemoCustomer action handlers
//  Plain CDI beans — no interface required.
//  Methods are invoked via KClass.memberFunctions + callSuspend.
// ─────────────────────────────────────────────────────────────

@ApplicationScoped
@Unremovable
class ActivateCustomerHandler {
    suspend fun validate(entity: dev.acme.portal.demo.DemoCustomer, formData: EntityData?): String? = null

    suspend fun execute(entity: dev.acme.portal.demo.DemoCustomer, formData: EntityData?): ActionResult {
        return ActionResult.Success("Klient '${entity.name}' został aktywowany.", refreshTable = true)
    }

    suspend fun executeBulk(entities: List<dev.acme.portal.demo.DemoCustomer>, formData: EntityData?): ActionResult {
        return ActionResult.Success("Aktywowano ${entities.size} klientów.", refreshTable = true)
    }
}

@ApplicationScoped
@Unremovable
class DeactivateCustomerHandler {
    suspend fun validate(entity: dev.acme.portal.demo.DemoCustomer, formData: EntityData?): String? = null

    suspend fun execute(entity: dev.acme.portal.demo.DemoCustomer, formData: EntityData?): ActionResult {
        return ActionResult.Success("Klient '${entity.name}' został dezaktywowany.", refreshTable = true)
    }
}

@ApplicationScoped
@Unremovable
class SendEmailHandler {
    suspend fun validate(entity: dev.acme.portal.demo.DemoCustomer, formData: EntityData?): String? = null

    suspend fun execute(entity: dev.acme.portal.demo.DemoCustomer, formData: EntityData?): ActionResult {
        return ActionResult.Success("E-mail powitalny wysłany do: ${entity.email}")
    }

    suspend fun executeBulk(entities: List<dev.acme.portal.demo.DemoCustomer>, formData: EntityData?): ActionResult {
        return ActionResult.Success("Wysłano ${entities.size} e-maili powitalnych.", refreshTable = false)
    }
}

// ─────────────────────────────────────────────────────────────
//  DemoOrder action handlers
// ─────────────────────────────────────────────────────────────

@ApplicationScoped
@Unremovable
class ProcessOrderHandler {
    suspend fun validate(entity: dev.acme.portal.demo.DemoOrder, formData: EntityData?): String? {
        if (entity.status?.toString() == "CANCELLED") return "Anulowanego zamówienia nie można przetworzyć."
        val priority = formData?.get("priority") as? String
        if (priority != null && priority !in listOf("NORMAL", "HIGH", "URGENT")) {
            return "Nieprawidłowa wartość priorytetu: $priority"
        }
        return null
    }

    suspend fun execute(entity: dev.acme.portal.demo.DemoOrder, formData: EntityData?): ActionResult {
        val priority = formData?.get("priority") ?: "NORMAL"
        val estimatedDelivery = formData?.get("estimatedDelivery")?.let { " (dostawa: $it)" } ?: ""
        val notifyCustomer = formData?.get("notifyCustomer") as? Boolean ?: true
        val notes = formData?.get("notes") as? String ?: ""
        val notifyMsg = if (notifyCustomer) " Klient zostanie powiadomiony e-mailem." else ""
        val notesMsg = if (notes.isNotBlank()) " Uwagi: $notes" else ""
        return ActionResult.Success(
            "Zamówienie #${entity.orderNumber} [priorytet: $priority]$estimatedDelivery przekazane do realizacji.$notifyMsg$notesMsg",
            refreshTable = true
        )
    }
}

@ApplicationScoped
@Unremovable
class CancelOrderHandler {
    suspend fun validate(entity: dev.acme.portal.demo.DemoOrder, formData: EntityData?): String? {
        if (entity.status?.toString() == "DELIVERED") return "Dostarczonego zamówienia nie można anulować."
        return null
    }

    suspend fun execute(entity: dev.acme.portal.demo.DemoOrder, formData: EntityData?): ActionResult {
        return ActionResult.Success("Zamówienie #${entity.orderNumber} zostało anulowane.", refreshTable = true)
    }
}
