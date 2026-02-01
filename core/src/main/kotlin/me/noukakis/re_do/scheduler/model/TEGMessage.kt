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
}