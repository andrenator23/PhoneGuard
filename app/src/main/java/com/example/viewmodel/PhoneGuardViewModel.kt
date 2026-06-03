package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.IntruderLog
import com.example.data.IntruderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppScreen {
    TRAP,
    DASHBOARD
}

data class TrapUiState(
    val isTriggered: Boolean = false,
    val triggeredApp: String? = null,
    val showFakeLoading: Boolean = false,
    val showFakeCrashDialog: Boolean = false,
    val imageCaptureStatus: String? = null // "capturing", "saved", "failed"
)

class PhoneGuardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: IntruderRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = IntruderRepository(database.intruderDao())
    }

    val allLogs: StateFlow<List<IntruderLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentScreen = MutableStateFlow(AppScreen.TRAP)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _trapState = MutableStateFlow(TrapUiState())
    val trapState: StateFlow<TrapUiState> = _trapState.asStateFlow()

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen == AppScreen.TRAP) {
            _trapState.value = TrapUiState()
        }
    }

    fun triggerTrap(triggeredAppName: String, takePhotoLambda: (onSuccess: (String) -> Unit, onError: (Exception) -> Unit) -> Unit) {
        if (_trapState.value.isTriggered) return

        _trapState.value = TrapUiState(
            isTriggered = true,
            triggeredApp = triggeredAppName,
            showFakeLoading = true,
            imageCaptureStatus = "capturing"
        )

        takePhotoLambda(
            { filePath -> // onSuccess
                viewModelScope.launch {
                    val log = IntruderLog(
                        filePath = filePath,
                        triggeredApp = triggeredAppName
                    )
                    repository.insertLog(log)
                    _trapState.update { 
                        it.copy(
                            showFakeLoading = false,
                            showFakeCrashDialog = true,
                            imageCaptureStatus = "saved"
                        )
                    }
                }
            },
            { exception -> // onError
                _trapState.update {
                    it.copy(
                        showFakeLoading = false,
                        showFakeCrashDialog = true,
                        imageCaptureStatus = "failed"
                    )
                }
            }
        )
    }

    fun dismissCrashDialog() {
        _trapState.update { it.copy(showFakeCrashDialog = false, isTriggered = false, triggeredApp = null) }
    }

    fun deleteLog(log: IntruderLog) {
        viewModelScope.launch {
            repository.deleteLog(log)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAllLogs(allLogs.value)
        }
    }
}
