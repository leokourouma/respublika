// server/src/main/kotlin/com/respublika/service/NotificationService.kt
package com.respublika.service

// TODO: wire up email or webhook here (e.g. SendGrid, Slack incoming webhook, etc.)
object NotificationService {

    fun notify(subject: String, body: String) {
        println("[NOTIFICATION] $subject")
        println("  $body")
    }

    fun notifyBreaking(subject: String, body: String) {
        println("🚨🚨🚨 [BREAKING NOTIFICATION] $subject 🚨🚨🚨")
        println("  $body")
    }
}
