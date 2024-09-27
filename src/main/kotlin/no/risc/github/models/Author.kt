package no.risc.github.models

import java.text.SimpleDateFormat
import java.util.Date

data class Author(
    val name: String?,
    val email: String?,
    val date: Date,
) {
    fun formattedDate(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date)
}
