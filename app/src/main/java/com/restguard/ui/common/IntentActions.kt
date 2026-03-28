package com.restguard.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Intent-based actions for sending emails and SMS.
 *
 * All messages are composed via the user's own apps (Gmail, Messages, etc.)
 * so the user always sees and approves the content before sending.
 * This avoids OAuth complexity and preserves user control.
 */
object IntentActions {

    /**
     * Open an email compose intent with pre-filled fields.
     * Returns false if no email app is available.
     */
    fun composeEmail(
        context: Context,
        to: List<String>,
        subject: String,
        body: String,
    ): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Send cancellation/reschedule email"))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Open an SMS compose intent with pre-filled message.
     */
    fun composeSms(
        context: Context,
        phoneNumber: String,
        body: String,
    ): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", body)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Share text via any messaging app (WhatsApp, Telegram, etc.)
     */
    fun shareText(
        context: Context,
        text: String,
        title: String = "Send message",
    ): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, title))
            true
        } catch (e: Exception) {
            false
        }
    }
}
