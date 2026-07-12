package io.github.kirthar.sddrpg.core

import kotlinx.serialization.json.Json

/**
 * Single strict Json instance for all tests (research.md R8): unknown keys are
 * errors so content typos surface at load time, mirroring FR-013's philosophy.
 */
val TestJson: Json = Json {
    ignoreUnknownKeys = false
}
