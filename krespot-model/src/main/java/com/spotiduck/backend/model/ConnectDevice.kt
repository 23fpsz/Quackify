package com.spotiduck.backend.model

enum class DeviceKind {
    PHONE,
    TABLET,
    COMPUTER,
    SPEAKER,
    TV,
    CAR,
    CONSOLE,
    RECEIVER,
    STICK,
    DONGLE,
    CAST,
    UNKNOWN,
}

data class ConnectDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val canPlay: Boolean,
    val volume: Int,
    val volumeSteps: Int,
    val isControllable: Boolean,
    val disableVolume: Boolean,
    val supportsTransfer: Boolean,
    val supportsLogout: Boolean,
    val supportsCommandRequest: Boolean,
    val supportsSleepTimer: Boolean,
    val isObservable: Boolean,
    val audioQuality: String,
    val brand: String,
    val model: String,
    val softwareVersion: String,
    val license: String,
    val active: Boolean = false,
    val activating: Boolean = false,
    val local: Boolean = false,
) {
    val volumeFraction: Float
        get() = if (volumeSteps > 0) (volume.toFloat() / volumeSteps.toFloat()).coerceIn(0f, 1f) else 0f

    val canControlVolume: Boolean get() = isControllable && !disableVolume && volumeSteps > 0

    val canTransfer: Boolean get() = canPlay && isControllable
}
