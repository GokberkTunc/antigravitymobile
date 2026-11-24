package com.antigravity.mobile.contracts

import kotlinx.serialization.json.Json

/**
 * Canonical [Json] configuration shared across all modules for serializing and
 * deserializing frozen `:core:contracts` DTOs and sealed events.
 */
val ContractsJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "type"
}
