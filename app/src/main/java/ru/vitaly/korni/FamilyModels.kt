package ru.vitaly.korni

import java.util.UUID

enum class Gender { MALE, FEMALE, UNKNOWN }
enum class LinkType { PARENT, SPOUSE, SIBLING }

data class Person(
    val id: String = UUID.randomUUID().toString(),
    val firstName: String = "",
    val lastName: String = "",
    val middleName: String = "",
    val gender: Gender = Gender.UNKNOWN,
    val birthDate: String = "",
    val deathDate: String = "",
    val photoUri: String = "",
    val notes: String = ""
) {
    fun displayName(): String = listOf(firstName, middleName).filter { it.isNotBlank() }.joinToString(" ")
    fun fullName(): String = listOf(lastName, firstName, middleName).filter { it.isNotBlank() }.joinToString(" ")
    fun lifeDates(): String {
        val born = birthDate.ifBlank { "?" }
        val died = deathDate.ifBlank { if (deathDate.isBlank()) "" else deathDate }
        return if (died.isBlank()) born else "$born — $died"
    }
}

data class FamilyLink(
    val id: String = UUID.randomUUID().toString(),
    val fromId: String,
    val toId: String,
    val type: LinkType
)

data class FamilyData(
    val people: List<Person> = emptyList(),
    val links: List<FamilyLink> = emptyList()
)

enum class RelativeChoice(val title: String) {
    SPOUSE("Муж / жена"),
    PARENT("Мать / отец"),
    CHILD("Сын / дочь"),
    SIBLING("Брат / сестра")
}
