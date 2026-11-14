package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.GitHubReleaseException
import com.example.data.GitHubReleaseRepository
import com.example.model.ReleaseUiState
import com.example.model.VersionComparisonStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReleaseScreenState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val applicationId: String = BuildConfig.APPLICATION_ID,
    val repoInput: String = "",
    val savedRepoSlug: String = "",
    val includePrereleases: Boolean = false,
    val releaseState: ReleaseUiState = ReleaseUiState.Idle
) {
    val hasUpdateAvailable: Boolean
        get() = (releaseState as? ReleaseUiState.Success)
            ?.result?.versionStatus == VersionComparisonStatus.UPDATE_AVAILABLE
}

class ReleaseViewModel(
    private val repository: GitHubReleaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReleaseScreenState())
    val uiState: StateFlow<ReleaseScreenState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = repository.preferencesFlow.first()
            _uiState.update {
                it.copy(
                    repoInput = prefs.repoSlug,
                    savedRepoSlug = prefs.repoSlug,
                    includePrereleases = prefs.includePrereleases
                )
            }
            if (prefs.repoSlug.isNotBlank()) {
                checkUpdates(repoInput = prefs.repoSlug, includePrereleases = prefs.includePrereleases)
            }
        }
    }

    fun updateRepoInput(input: String) {
        _uiState.update { it.copy(repoInput = input) }
    }

    fun toggleIncludePrereleases(enabled: Boolean) {
        _uiState.update { it.copy(includePrereleases = enabled) }
        val currentSlug = _uiState.value.savedRepoSlug
        if (currentSlug.isNotBlank()) {
            viewModelScope.launch {
                repository.savePreferences(currentSlug, enabled)
                checkUpdates(currentSlug, enabled)
            }
        }
    }

    fun saveAndCheckUpdates() {
        val state = _uiState.value
        val normalized = GitHubReleaseRepository.normalizeRepoSlug(state.repoInput)
        if (normalized == null) {
            _uiState.update {
                it.copy(
                    releaseState = ReleaseUiState.Error(
                        "Ange ett giltigt GitHub-repository som ägare/repo eller klistra in din GitHub-länk."
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            repository.savePreferences(normalized, state.includePrereleases)
            _uiState.update {
                it.copy(
                    repoInput = normalized,
                    savedRepoSlug = normalized
                )
            }
            checkUpdates(normalized, state.includePrereleases)
        }
    }

    fun refreshReleases() {
        val state = _uiState.value
        val target = state.savedRepoSlug.ifBlank { state.repoInput }
        if (target.isBlank()) {
            _uiState.update {
                it.copy(
                    releaseState = ReleaseUiState.Error(
                        "Ange ditt GitHub-repository (ägare/repo) ovan för att hämta releaser."
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            checkUpdates(target, state.includePrereleases)
        }
    }

    private suspend fun checkUpdates(repoInput: String, includePrereleases: Boolean) {
        _uiState.update { it.copy(releaseState = ReleaseUiState.Loading) }
        try {
            val result = repository.fetchReleases(
                rawRepoInput = repoInput,
                currentVersionName = BuildConfig.VERSION_NAME,
                includePrereleases = includePrereleases
            )
            _uiState.update {
                it.copy(
                    savedRepoSlug = result.repoSlug,
                    releaseState = ReleaseUiState.Success(result)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GitHubReleaseException) {
            Log.w(TAG, "GitHub release check returned error: ${e.message}")
            _uiState.update {
                it.copy(
                    releaseState = ReleaseUiState.Error(
                        message = e.message ?: "Kunde inte hämta releaser från GitHub.",
                        isNotFound = e.isNotFound
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error checking GitHub releases", e)
            _uiState.update {
                it.copy(
                    releaseState = ReleaseUiState.Error(
                        message = e.localizedMessage ?: "Ett oväntat fel uppstod vid hämtning av releaser."
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "ReleaseViewModel"
    }
}
