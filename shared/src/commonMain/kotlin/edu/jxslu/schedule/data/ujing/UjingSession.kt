package edu.jxslu.schedule.data.ujing

/** U净 本机会话（DESIGN §4.37）：手机号 + JWT。 */
data class UjingSession(
    val mobile: String,
    val token: String,
    val userId: String = "",
) {
    val isComplete: Boolean get() = mobile.isNotBlank() && token.isNotBlank()
}
