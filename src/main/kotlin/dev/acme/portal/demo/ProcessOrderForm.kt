package dev.acme.portal.demo

import dev.quatrion.portal.annotation.PortalFormField
import dev.quatrion.portal.annotation.RendererType

/**
 * Form model for the "processOrder" action on [dev.acme.portal.demo.DemoOrder].
 *
 * Fields annotated with @PortalFormField are automatically reflected by
 * MetadataService and sent to the frontend as `formFields` inside ActionMetadata.
 */
data class ProcessOrderForm(

    @field:PortalFormField(
        label = "Priorytet realizacji",
        renderer = RendererType.SELECT,
        required = true,
        selectOptions = ["NORMAL", "HIGH", "URGENT"],
        order = 1
    )
    val priority: String = "NORMAL",

    @field:PortalFormField(
        label = "Przewidywana data realizacji",
        renderer = RendererType.DATE,
        placeholder = "YYYY-MM-DD",
        tooltip = "Wpisz planowaną datę realizacji zamówienia",
        order = 2
    )
    val estimatedDelivery: String = "",

    @field:PortalFormField(
        label = "Powiadom klienta e-mailem",
        renderer = RendererType.BOOLEAN,
        order = 3
    )
    val notifyCustomer: Boolean = true,

    @field:PortalFormField(
        label = "Uwagi do realizacji",
        renderer = RendererType.TEXTAREA,
        placeholder = "Dodatkowe informacje dla magazynu…",
        order = 4
    )
    val notes: String = ""
)



