package me.noukakis.re_do.common.model

import me.noukakis.re_do.scheduler.model.TEGArtefact
import kotlin.time.Duration

sealed interface TEGMessageOut {
    data class TEGRunTaskMessage(
        val taskName: String,
        val implementationName: String,
        val artefacts: List<TEGArtefact>,
        val arguments: List<String>,
        val timeout: Duration,
    ) : TEGMessageOut
}

sealed interface TEGMessageIn {
    data class TEGTaskResultMessage(
        val taskName: String,
        val outputArtefacts: List<TEGArtefact>,
    ) : TEGMessageIn

    data class TEGTaskFailedMessage(
        val taskName: String,
        val reason: String,
    ) : TEGMessageIn

    data class TEGTaskProgressMessage(
        val taskName: String,
        val progress: Int,
        val step: String,
    ) : TEGMessageIn

    data class TEGTaskLogMessage(
        val taskName: String,
        val log: String,
    ) : TEGMessageIn
}