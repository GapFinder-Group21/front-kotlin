package co.gapfinder.mobile.domain

/**
 * Reglas del registro, las mismas de RegisterRequest en el backend.
 * Cada función devuelve el mensaje del primer problema, o null si el valor sirve.
 * Son funciones puras: se prueban solas (SignUpRulesTest).
 */
object SignUpRules {
    const val NAME_MIN = 2
    const val NAME_MAX = 50
    const val EMAIL_MAX = 100
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 64
    const val PHONE_LENGTH = 10

    private val NAME = Regex("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü]+( [A-Za-zÁÉÍÓÚáéíóúÑñÜü]+)*$")
    private val EMAIL = Regex("^[a-z0-9]+([._-][a-z0-9]+)*@(gmail|outlook)\\.com$", RegexOption.IGNORE_CASE)
    private val PASSWORD = Regex("^[\\x21-\\x7E]+$")
    private val PHONE = Regex("^3[0-9]{9}$")

    /** Quita espacios de los bordes y deja uno solo entre palabras. */
    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun nameProblem(name: String): String? = when {
        name.isEmpty() -> "Please enter your name"
        name.length < NAME_MIN || name.length > NAME_MAX -> "The name must have between $NAME_MIN and $NAME_MAX characters"
        !NAME.matches(name) -> "The name can only have letters and spaces"
        else -> null
    }

    fun emailProblem(email: String): String? = when {
        email.isEmpty() -> "Please enter your email"
        email.length > EMAIL_MAX -> "The email is too long"
        !EMAIL.matches(email) -> "Please enter a valid email (@gmail.com or @outlook.com)"
        else -> null
    }

    fun passwordProblem(password: String): String? = when {
        password.length < PASSWORD_MIN || password.length > PASSWORD_MAX ->
            "The password must have between $PASSWORD_MIN and $PASSWORD_MAX characters"
        !PASSWORD.matches(password) -> "The password can't have spaces, accents or emojis"
        else -> null
    }

    fun phoneProblem(phone: String): String? =
        if (PHONE.matches(phone)) null else "The phone must have $PHONE_LENGTH digits and start with 3"
}