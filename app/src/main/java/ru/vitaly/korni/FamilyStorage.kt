package ru.vitaly.korni

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FamilyStorage {
    private const val PREFS = "korni_family_data"
    private const val KEY_DATA = "family_json"

    fun load(context: Context): FamilyData {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DATA, null)
            ?: return FamilyData()
        return runCatching { fromJson(raw) }.getOrElse { FamilyData() }
    }

    fun save(context: Context, data: FamilyData) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATA, toJson(data))
            .apply()
    }

    fun toJson(data: FamilyData): String {
        val root = JSONObject()
        val peopleArray = JSONArray()
        data.people.forEach { person ->
            peopleArray.put(JSONObject().apply {
                put("id", person.id)
                put("firstName", person.firstName)
                put("lastName", person.lastName)
                put("middleName", person.middleName)
                put("gender", person.gender.name)
                put("birthDate", person.birthDate)
                put("deathDate", person.deathDate)
                put("photoUri", person.photoUri)
                put("notes", person.notes)
            })
        }
        val linksArray = JSONArray()
        data.links.forEach { link ->
            linksArray.put(JSONObject().apply {
                put("id", link.id)
                put("fromId", link.fromId)
                put("toId", link.toId)
                put("type", link.type.name)
            })
        }
        root.put("format", "korni-family")
        root.put("version", 1)
        root.put("people", peopleArray)
        root.put("links", linksArray)
        return root.toString(2)
    }

    fun fromJson(raw: String): FamilyData {
        val root = JSONObject(raw)
        val peopleJson = root.optJSONArray("people") ?: JSONArray()
        val linksJson = root.optJSONArray("links") ?: JSONArray()
        val people = buildList {
            for (index in 0 until peopleJson.length()) {
                val item = peopleJson.optJSONObject(index) ?: continue
                add(
                    Person(
                        id = item.optString("id"),
                        firstName = item.optString("firstName"),
                        lastName = item.optString("lastName"),
                        middleName = item.optString("middleName"),
                        gender = runCatching { Gender.valueOf(item.optString("gender", "UNKNOWN")) }.getOrDefault(Gender.UNKNOWN),
                        birthDate = item.optString("birthDate"),
                        deathDate = item.optString("deathDate"),
                        photoUri = item.optString("photoUri"),
                        notes = item.optString("notes")
                    )
                )
            }
        }
        val links = buildList {
            for (index in 0 until linksJson.length()) {
                val item = linksJson.optJSONObject(index) ?: continue
                val type = runCatching { LinkType.valueOf(item.optString("type")) }.getOrNull() ?: continue
                add(
                    FamilyLink(
                        id = item.optString("id"),
                        fromId = item.optString("fromId"),
                        toId = item.optString("toId"),
                        type = type
                    )
                )
            }
        }
        val ids = people.map { it.id }.toSet()
        return FamilyData(
            people = people,
            links = links.filter { it.fromId in ids && it.toId in ids && it.fromId != it.toId }
        )
    }
}
