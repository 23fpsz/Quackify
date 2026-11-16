package com.quackify.compose.components

internal fun matchesCurrent(uri: String, currentUri: String, title: String, currentTitle: String): Boolean =
    if (currentUri.isNotBlank()) uri == currentUri else title.isNotBlank() && title == currentTitle

internal fun matchesCurrentContext(currentContext: String, ownContext: String): Boolean =
    ownContext.isBlank() || currentContext.isBlank() || currentContext == ownContext
