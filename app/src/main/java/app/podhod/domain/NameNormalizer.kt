package app.podhod.domain

object NameNormalizer {
    /**
     * Ключ для сравнения названий: без регистра, ё = е, пунктуация и лишние пробелы не важны.
     * «Жим  лёжа», «жим-лежа» и «Жим лежа.» дают один ключ.
     */
    fun key(name: String): String {
        val sb = StringBuilder()
        var lastSpace = true
        for (ch in name.lowercase()) {
            val c = if (ch == 'ё') 'е' else ch
            if (c.isLetterOrDigit()) {
                sb.append(c)
                lastSpace = false
            } else if (!lastSpace) {
                sb.append(' ')
                lastSpace = true
            }
        }
        return sb.toString().trim()
    }

    /** Название для показа: обрезанные края и одиночные пробелы. */
    fun display(name: String): String = name.trim().replace(Regex("\\s+"), " ")
}
