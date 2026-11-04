package cz.obehy.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.obehy.core.data.MotisServerChecker
import cz.obehy.core.data.ServerCheckResult
import cz.obehy.core.data.ServerCheckStep
import cz.obehy.core.data.SettingsRepository
import cz.obehy.core.model.motisUrlOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the user is in setting up their own server. */
internal sealed interface ServerWizard {
    data class Entering(
        val address: String = "",
        val invalid: Boolean = false,
    ) : ServerWizard

    data class Checking(
        val url: String,
        val step: ServerCheckStep,
    ) : ServerWizard

    data class Passed(
        val url: String,
        val version: String,
    ) : ServerWizard

    data class Failed(
        val url: String,
        val step: ServerCheckStep,
    ) : ServerWizard
}

/** A server is used only once it has passed the check; resetting goes back to Transitous. */
@HiltViewModel
class CustomServerViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val checker: MotisServerChecker,
    ) : ViewModel() {
        private val current = MutableStateFlow<ServerWizard?>(null)
        internal val wizard: StateFlow<ServerWizard?> = current.asStateFlow()
        private var checking: Job? = null

        fun open() {
            current.value = ServerWizard.Entering()
        }

        fun type(address: String) {
            current.value = ServerWizard.Entering(address)
        }

        /** Checks the address entered. */
        fun check() {
            val address = (current.value as? ServerWizard.Entering)?.address ?: return
            val url = motisUrlOf(address)
            if (url == null) current.value = ServerWizard.Entering(address, invalid = true) else check(url)
        }

        /** Checks the address that failed once more. */
        fun retry() {
            (current.value as? ServerWizard.Failed)?.let { check(it.url) }
        }

        private fun check(url: String) {
            checking?.cancel()
            checking =
                viewModelScope.launch {
                    val result =
                        checker.check(url) { step -> current.value = ServerWizard.Checking(url, step) }
                    current.value =
                        when (result) {
                            is ServerCheckResult.Passed -> ServerWizard.Passed(url, result.version)
                            is ServerCheckResult.Failed -> ServerWizard.Failed(url, result.step)
                        }
                }
        }

        /** Back to the address, to change it. */
        fun edit() {
            val url =
                when (val wizard = current.value) {
                    is ServerWizard.Passed -> wizard.url
                    is ServerWizard.Failed -> wizard.url
                    else -> return
                }
            type(url)
        }

        /** Starts searching on the server that passed. */
        fun use() {
            val passed = current.value as? ServerWizard.Passed ?: return
            viewModelScope.launch { settings.setCustomMotisUrl(passed.url) }
            close()
        }

        fun close() {
            checking?.cancel()
            current.value = null
        }

        fun reset() {
            viewModelScope.launch { settings.setCustomMotisUrl(null) }
        }
    }
