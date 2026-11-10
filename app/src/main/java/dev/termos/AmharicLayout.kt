package dev.termos

/** Compact Ge'ez syllable sets — one consonant family per page, seven vowels (Fidel row). */
object AmharicLayout {
    data class Family(val name: String, val cells: List<String>)

    val families = listOf(
        Family("ሀ", listOf("ሀ", "ሁ", "ሂ", "ሃ", "ሄ", "ህ", "ሆ")),
        Family("ለ", listOf("ለ", "ሉ", "ሊ", "ላ", "ሌ", "ል", "ሎ")),
        Family("መ", listOf("መ", "ሙ", "ሚ", "ማ", "ሜ", "ም", "ሞ")),
        Family("ረ", listOf("ረ", "ሩ", "ሪ", "ራ", "ር", "ሬ", "ሮ")),
        Family("ሰ", listOf("ሰ", "ሱ", "ሲ", "ሳ", "ሴ", "ስ", "ሶ")),
        Family("በ", listOf("በ", "ቡ", "ቢ", "ባ", "ቤ", "ብ", "ቦ")),
        Family("ተ", listOf("ተ", "ቱ", "ቲ", "ታ", "ቴ", "ት", "ቶ")),
        Family("አ", listOf("አ", "ኡ", "ኢ", "ኣ", "ኤ", "እ", "ኦ")),
        Family("ከ", listOf("ከ", "ኩ", "ኪ", "ካ", "ኬ", "ክ", "ኮ")),
        Family("የ", listOf("የ", "ዩ", "ዪ", "ያ", "ዬ", "ይ", "ዮ")),
        Family("ደ", listOf("ደ", "ዱ", "ዲ", "ዳ", "ዴ", "ድ", "ዶ")),
        Family("ገ", listOf("ገ", "ጉ", "ጊ", "ጋ", "ጌ", "ግ", "ጎ")),
    )

    /** Common extras on every Amharic page (punctuation / digits in Ge'ez context). */
    val extras = listOf("፡", "።", "፣", "፤", "፥", "፦", "፧", "፨")

    fun safeIndex(i: Int) = ((i % families.size) + families.size) % families.size

    fun rows(familyIndex: Int): List<List<String>> {
        val f = families[safeIndex(familyIndex)]
        val a = f.cells
        val b = extras.take(4)
        return listOf(a.take(4), a.drop(4) + b.take(1), b.drop(1))
    }

    fun familyLabel(familyIndex: Int) = families[safeIndex(familyIndex)].name
}
