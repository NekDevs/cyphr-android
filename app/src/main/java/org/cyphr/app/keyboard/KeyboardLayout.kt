package org.cyphr.app.keyboard

import org.cyphr.app.R

data class KeyboardLayout(
    val nameResId: Int,
    val code: String,
    val row1: String,
    val row2: String,
    val row3: String,
    val isSymbols: Boolean = false,
    val variants: Map<Char, List<Char>> = emptyMap()
)

val KEYBOARD_LAYOUTS = listOf(
    KeyboardLayout(
        nameResId = R.string.layout_english,
        code = "en",
        row1 = "qwertyuiop",
        row2 = "asdfghjkl",
        row3 = "zxcvbnm"
    ),
    KeyboardLayout(
        nameResId = R.string.layout_spanish,
        code = "es",
        row1 = "qwertyuiop",
        row2 = "asdfghjklñ",
        row3 = "zxcvbnm",
        variants = mapOf(
            'a' to listOf('á'), 'e' to listOf('é'), 'i' to listOf('í'),
            'o' to listOf('ó'), 'u' to listOf('ú', 'ü'), 'n' to listOf('ñ'),
            'A' to listOf('Á'), 'E' to listOf('É'), 'I' to listOf('Í'),
            'O' to listOf('Ó'), 'U' to listOf('Ú', 'Ü'), 'N' to listOf('Ñ')
        )
    ),
    KeyboardLayout(
        nameResId = R.string.layout_german,
        code = "de",
        row1 = "qwertzuiopü",
        row2 = "asdfghjklöä",
        row3 = "yxcvbnm",
        variants = mapOf(
            'a' to listOf('ä'), 'o' to listOf('ö'), 'u' to listOf('ü'), 's' to listOf('ß'),
            'A' to listOf('Ä'), 'O' to listOf('Ö'), 'U' to listOf('Ü')
        )
    ),
    KeyboardLayout(
        nameResId = R.string.layout_french,
        code = "fr",
        row1 = "azertyuiop",
        row2 = "qsdfghjklm",
        row3 = "wxcvbn",
        variants = mapOf(
            'a' to listOf('à', 'â', 'æ'), 'e' to listOf('é', 'è', 'ê', 'ë'),
            'i' to listOf('î', 'ï'), 'o' to listOf('ô', 'œ'),
            'u' to listOf('ù', 'û', 'ü'), 'c' to listOf('ç'),
            'A' to listOf('À', 'Â', 'Æ'), 'E' to listOf('É', 'È', 'Ê', 'Ë'),
            'I' to listOf('Î', 'Ï'), 'O' to listOf('Ô', 'Œ'),
            'U' to listOf('Ù', 'Û', 'Ü'), 'C' to listOf('Ç')
        )
    ),
    KeyboardLayout(
        nameResId = R.string.layout_italian,
        code = "it",
        row1 = "qwertyuiopè",
        row2 = "asdfghjklòà",
        row3 = "zxcvbnmù",
        variants = mapOf(
            'a' to listOf('à'), 'e' to listOf('è', 'é'), 'i' to listOf('ì'),
            'o' to listOf('ò', 'ó'), 'u' to listOf('ù'),
            'A' to listOf('À'), 'E' to listOf('È', 'É'), 'I' to listOf('Ì'),
            'O' to listOf('Ò', 'Ó'), 'U' to listOf('Ù')
        )
    ),
    KeyboardLayout(
        nameResId = R.string.layout_portuguese,
        code = "pt",
        row1 = "qwertyuiop",
        row2 = "asdfghjklç",
        row3 = "zxcvbnm",
        variants = mapOf(
            'a' to listOf('á', 'à', 'â', 'ã'), 'e' to listOf('é', 'ê'),
            'i' to listOf('í'), 'o' to listOf('ó', 'ô', 'õ'),
            'u' to listOf('ú', 'ü'), 'c' to listOf('ç'),
            'A' to listOf('Á', 'À', 'Â', 'Ã'), 'E' to listOf('É', 'Ê'),
            'I' to listOf('Í'), 'O' to listOf('Ó', 'Ô', 'Õ'),
            'U' to listOf('Ú', 'Ü'), 'C' to listOf('Ç')
        )
    ),
    KeyboardLayout(
        nameResId = R.string.layout_greek,
        code = "el",
        row1 = ";ςερτυθιοπ",
        row2 = "ασδφγηξκλ",
        row3 = "ζχψωβνμ",
        variants = mapOf(
            'α' to listOf('ά'), 'ε' to listOf('έ'), 'η' to listOf('ή'),
            'ι' to listOf('ί', 'ϊ'), 'ο' to listOf('ό'),
            'υ' to listOf('ύ', 'ϋ'), 'ω' to listOf('ώ'),
            'Α' to listOf('Ά'), 'Ε' to listOf('Έ'), 'Η' to listOf('Ή'),
            'Ι' to listOf('Ί', 'Ϊ'), 'Ο' to listOf('Ό'),
            'Υ' to listOf('Ύ', 'Ϋ'), 'Ω' to listOf('Ώ')
        )
    ),
    KeyboardLayout(
        nameResId = R.string.layout_russian,
        code = "ru",
        row1 = "йцукенгшщз",
        row2 = "фывапролджэ",
        row3 = "ячсмитьбю",
        variants = mapOf(
            'е' to listOf('ё'), 'Е' to listOf('Ё')
        )
    ),
    KeyboardLayout(
        nameResId = R.string.layout_symbols,
        code = "sym",
        row1 = "1234567890",
        row2 = "@#\$%&-+()",
        row3 = "=*\"'!?/",
        isSymbols = true
    )
)
const val SYMBOLS_LAYOUT_INDEX = 8
