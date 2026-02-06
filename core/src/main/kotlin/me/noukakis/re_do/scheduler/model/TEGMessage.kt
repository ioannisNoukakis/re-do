package me.noukakis.re_do.scheduler.model

sealed class TEGMessageOut {
    data class TEGRunTaskMessage(
        val taskName: String,
        val artefacts: List<TEGArtefact>,
    ) : TEGMessageOut()
}

sealed class TEGMessageIn {
    data class TEGTaskResultMessage(
        val taskName: String,
        val outputArtefacts: List<TEGArtefact>,
    ) : TEGMessageIn()

    data class TEGTaskFailedMessage(
        val taskName: String,
        val reason: String,
    ) : TEGMessageIn()

    data class TEGTaskProgressMessage(
        val taskName: String,
        val progress: Int,
    ) : TEGMessageIn()

    data class TEGTaskLogMessage(
        val taskName: String,
        val log: String,
    ) : TEGMessageIn()
}