package com.whisperflow.model

data class Language(
    val code: String,
    val name: String,
    val nativeName: String,
    val flagEmoji: String = ""
)

object LanguageCatalog {

    val AUTO_DETECT = Language(
        code = "auto",
        name = "Auto Detect",
        nativeName = "Auto Detect",
        flagEmoji = "🌐"
    )

    val KEEP_ORIGINAL = Language(
        code = "original",
        name = "Keep Original (No Translation)",
        nativeName = "Same Language",
        flagEmoji = "🔄"
    )

    val ALL_LANGUAGES: List<Language> = listOf(
        AUTO_DETECT,
        Language("bn", "Bengali", "বাংলা", "🇧🇩"),
        Language("en", "English", "English", "🇺🇸"),
        Language("hi", "Hindi", "हिन्दी", "🇮🇳"),
        Language("es", "Spanish", "Español", "🇪🇸"),
        Language("ar", "Arabic", "العربية", "🇸🇦"),
        Language("fr", "French", "Français", "🇫🇷"),
        Language("de", "German", "Deutsch", "🇩🇪"),
        Language("ja", "Japanese", "日本語", "🇯🇵"),
        Language("zh", "Chinese (Simplified)", "中文", "🇨🇳"),
        Language("ur", "Urdu", "اردو", "🇵🇰"),
        Language("pt", "Portuguese", "Português", "🇧🇷"),
        Language("ru", "Russian", "Русский", "🇷🇺"),
        Language("id", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
        Language("it", "Italian", "Italiano", "🇮🇹"),
        Language("ko", "Korean", "한국어", "🇰🇷"),
        Language("tr", "Turkish", "Türkçe", "🇹🇷"),
        Language("vi", "Vietnamese", "Tiếng Việt", "🇻🇳"),
        Language("th", "Thai", "ไทย", "🇹🇭"),
        Language("ta", "Tamil", "தமிழ்", "🇮🇳"),
        Language("te", "Telugu", "తెలుగు", "🇮🇳"),
        Language("mr", "Marathi", "मराठी", "🇮🇳"),
        Language("gu", "Gujarati", "ગુજરાતી", "🇮🇳"),
        Language("pa", "Punjabi", "ਪੰਜਾਬੀ", "🇮🇳"),
        Language("ms", "Malay", "Bahasa Melayu", "🇲🇾"),
        Language("nl", "Dutch", "Nederlands", "🇳🇱"),
        Language("pl", "Polish", "Polski", "🇵🇱"),
        Language("sv", "Swedish", "Svenska", "🇸🇪"),
        Language("fa", "Persian", "فارسی", "🇮🇷"),
        Language("el", "Greek", "Ελληνικά", "🇬🇷"),
        Language("he", "Hebrew", "עברית", "🇮🇱"),
        Language("da", "Danish", "Dansk", "🇩🇰"),
        Language("fi", "Finnish", "Suomi", "🇫🇮"),
        Language("no", "Norwegian", "Norsk", "🇳🇴"),
        Language("cs", "Czech", "Čeština", "🇨🇿"),
        Language("ro", "Romanian", "Română", "🇷🇴"),
        Language("hu", "Hungarian", "Magyar", "🇭🇺"),
        Language("uk", "Ukrainian", "Українська", "🇺🇦"),
        Language("sw", "Swahili", "Kiswahili", "🇰🇪"),
        Language("ne", "Nepali", "नेपाली", "🇳🇵"),
        Language("si", "Sinhala", "සිංහල", "🇱🇰"),
        Language("my", "Burmese", "မြန်မာဘာသာ", "🇲🇲"),
        Language("km", "Khmer", "ភាសាខ្មែរ", "🇰🇭"),
        Language("fil", "Filipino", "Tagalog", "🇵🇭")
    )

    fun findByCode(code: String): Language {
        if (code == "auto") return AUTO_DETECT
        if (code == "original") return KEEP_ORIGINAL
        return ALL_LANGUAGES.find { it.code.equals(code, ignoreCase = true) }
            ?: Language(code, code.uppercase(), code.uppercase(), "🌐")
    }

    fun search(query: String, forTarget: Boolean = false): List<Language> {
        val q = query.trim().lowercase()
        val list = if (forTarget) {
            listOf(KEEP_ORIGINAL) + ALL_LANGUAGES.filter { it.code != "auto" }
        } else {
            ALL_LANGUAGES
        }

        if (q.isEmpty()) return list

        return list.filter {
            it.name.lowercase().contains(q) ||
            it.nativeName.lowercase().contains(q) ||
            it.code.lowercase().contains(q)
        }
    }

    val POPULAR_LANGUAGES: List<Language> = listOf(
        AUTO_DETECT,
        findByCode("bn"),
        findByCode("en"),
        findByCode("hi"),
        findByCode("es"),
        findByCode("ar"),
        findByCode("fr")
    )
}
