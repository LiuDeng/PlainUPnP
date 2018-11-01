package com.m3sv.plainupnp.data.upnp


enum class DeviceType {
    RENDERER, CONTENT_DIRECTORY, UNDEFINED
}

data class DeviceDisplay @JvmOverloads constructor(
    val device: UpnpDevice,
    val extendedInformation: Boolean = false,
    val type: DeviceType = DeviceType.UNDEFINED
)