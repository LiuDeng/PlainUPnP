package com.m3sv.plainupnp.presentation.onboarding

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3sv.plainupnp.ContentManager
import com.m3sv.plainupnp.ThemeManager
import com.m3sv.plainupnp.ThemeOption
import com.m3sv.plainupnp.applicationmode.ApplicationMode
import com.m3sv.plainupnp.applicationmode.ApplicationModeManager
import com.m3sv.plainupnp.data.upnp.UriWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class Direction {
    Forward, Backward
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val application: Application,
    private val themeManager: ThemeManager,
    private val contentManager: ContentManager,
    private val applicationModeManager: ApplicationModeManager,
) : ViewModel() {

    var activeTheme: ThemeOption by mutableStateOf(themeManager.currentTheme)
        private set

    val contentUris: StateFlow<List<UriWrapper>> =
        contentManager.persistedUrisFlow().stateIn(viewModelScope, SharingStarted.Lazily, contentManager.getUris())

    private val _currentScreen: MutableSharedFlow<Direction> = MutableSharedFlow()

    val currentScreen: StateFlow<OnboardingScreen> =
        _currentScreen.scan(OnboardingScreen.Greeting) { currentScreen, direction ->
            when (direction) {
                Direction.Forward -> currentScreen.forward()
                Direction.Backward -> currentScreen.backward()
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, OnboardingScreen.Greeting)


    fun onSelectTheme(themeOption: ThemeOption) {
        activeTheme = themeOption
        themeManager.setNightMode(themeOption)
    }

    fun onSelectMode(mode: ApplicationMode) {
        viewModelScope.launch {
            applicationModeManager.setApplicationMode(mode)
        }
    }

    fun onNavigateNext() {
        viewModelScope.launch {
            _currentScreen.emit(Direction.Forward)
        }
    }

    fun onNavigateBack() {
        viewModelScope.launch {
            _currentScreen.emit(Direction.Backward)
        }
    }

    fun saveUri() {
        contentManager.updateUris()
    }

    fun releaseUri(uriWrapper: UriWrapper) {
        contentManager.releaseUri(uriWrapper)
    }

    private suspend fun OnboardingScreen.forward(): OnboardingScreen = when (this) {
        OnboardingScreen.Greeting -> OnboardingScreen.SelectTheme
        OnboardingScreen.SelectTheme -> OnboardingScreen.SelectMode
        OnboardingScreen.SelectMode -> when (getApplicationMode()) {
            ApplicationMode.Streaming -> if (hasStoragePermission()) OnboardingScreen.SelectDirectories else OnboardingScreen.StoragePermission
            ApplicationMode.Player -> OnboardingScreen.Finish
            null -> error("Application mode is not selected")
        }
        OnboardingScreen.StoragePermission -> OnboardingScreen.SelectDirectories
        OnboardingScreen.SelectDirectories -> OnboardingScreen.Finish
        OnboardingScreen.Finish -> error("Can't navigate from finish screen")
    }

    private suspend fun getApplicationMode(): ApplicationMode? = applicationModeManager.getApplicationMode()

    private fun OnboardingScreen.backward(): OnboardingScreen = when (this) {
        OnboardingScreen.Greeting -> OnboardingScreen.Greeting
        OnboardingScreen.SelectTheme -> OnboardingScreen.Greeting
        OnboardingScreen.SelectMode -> OnboardingScreen.SelectTheme
        OnboardingScreen.StoragePermission -> OnboardingScreen.SelectMode
        OnboardingScreen.SelectDirectories -> OnboardingScreen.SelectMode
        OnboardingScreen.Finish -> error("Can't navigate from finish screen")
    }

    fun hasStoragePermission(): Boolean = ContextCompat.checkSelfPermission(
        application,
        STORAGE_PERMISSION
    ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val STORAGE_PERMISSION = READ_EXTERNAL_STORAGE
    }

}
