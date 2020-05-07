package com.m3sv.plainupnp.upnp.actions

import com.m3sv.plainupnp.upnp.RendererServiceFinder
import org.fourthline.cling.UpnpService
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.avtransport.callback.Play
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PlayAction @Inject constructor(
    service: UpnpService,
    serviceFinder: RendererServiceFinder
) : AvAction(service, serviceFinder) {
    suspend operator fun invoke() = suspendCoroutine<Boolean> { continuation ->
        executeAVAction {
            object : Play(this) {
                override fun success(invocation: ActionInvocation<out Service<*, *>>?) {
                    continuation.resume(true)
                }

                override fun failure(
                    p0: ActionInvocation<out Service<*, *>>?,
                    p1: UpnpResponse?,
                    p2: String?
                ) {
                    Timber.e("Failed to stop")
                    continuation.resume(false)
                }
            }
        }
    }
}