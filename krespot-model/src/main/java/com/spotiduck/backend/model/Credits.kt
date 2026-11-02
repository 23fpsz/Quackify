package com.spotiduck.backend.model

data class CreditPerson(
    val name: String,
    val role: String,
    val uri: String,
)

data class CreditGroup(
    val name: String,
    val people: List<CreditPerson>,
)

data class Credits(
    val trackName: String,
    val groups: List<CreditGroup>,
    val sources: List<String>,
) {
    val contributors: Int get() = groups.sumOf { it.people.size }
    val isEmpty: Boolean get() = groups.isEmpty() && sources.isEmpty()
}
