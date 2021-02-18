package com.m3sv.plainupnp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.m3sv.plainupnp.data.upnp.UriWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class ContentManager @Inject constructor(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
) : CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + SupervisorJob()

    private val persistedUris: MutableStateFlow<List<UriWrapper>> = MutableStateFlow(listOf())

    init {
        updateUris()
    }

    fun getPersistedUris(): Flow<List<UriWrapper>> = persistedUris

    fun releaseUri(uriWrapper: UriWrapper) {
        launch {
            context
                .contentResolver
                .releasePersistableUriPermission(uriWrapper.uriPermission.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            updateUris()
        }
    }

    fun updateUris() {
        launch {
            persistedUris.value = getUris()
        }
    }

    private fun getUris(): List<UriWrapper> {
        return context.contentResolver.persistedUriPermissions.map(::UriWrapper)
    }
}