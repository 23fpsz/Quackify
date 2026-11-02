package com.spotiduck.backend.model

data class AccountAttributes(
    val product: String = "",
    val country: String = "",
    val catalogue: String = "",
    val explicitFilter: Boolean = false,
    val canvasDisabled: Boolean = false,
    val musicVideoDisabled: Boolean = false,
    val otherVideoDisabled: Boolean = false,
    val canvasLocked: Boolean = false,
    val musicVideoLocked: Boolean = false,
    val otherVideoLocked: Boolean = false,
    val hasPodcasts: Boolean = true,
    val hasAudiobooks: Boolean = true,
) {
    val premium: Boolean get() = product.equals("PREMIUM", ignoreCase = true)
}

data class ProfilePrivacy(
    val showFollows: Boolean = false,
    val showPublishedPlaylists: Boolean = false,
    val allowFollows: Boolean = false,
)
