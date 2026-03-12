package com.carry.notification.domain.vo

data class NotificationTemplate(
    val type: NotificationType,
    val channel: NotificationChannel,
    val titleTemplate: String,
    val contentTemplate: String,
) {
    fun render(params: Map<String, String>): Pair<String, String> {
        var title = titleTemplate
        var content = contentTemplate
        params.forEach { (key, value) ->
            title = title.replace("{$key}", value)
            content = content.replace("{$key}", value)
        }
        return title to content
    }
}
