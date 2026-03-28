package com.restguard.data.repository.impl.platform

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.restguard.domain.model.ContactMethod
import com.restguard.domain.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real Android ContactsContract implementation.
 *
 * Queries the system contacts for email and phone data.
 * Used when a calendar event has no attendees and the user needs
 * to pick someone to notify about a cancellation or reschedule.
 */
class ContactsProviderRepository(
    private val context: Context,
) : ContactRepository {

    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun searchContacts(query: String): List<ContactMethod> {
        return withContext(Dispatchers.IO) {
            val contacts = mutableMapOf<String, ContactMethod>()

            // Search by display name
            val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            val cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ),
                selection,
                selectionArgs,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val contactId = it.getString(0)
                    val name = it.getString(1) ?: continue
                    val photoUri = it.getString(2)

                    val email = getEmail(contactId)
                    val phone = getPhone(contactId)

                    if (email != null || phone != null) {
                        contacts[contactId] = ContactMethod(
                            contactId = contactId,
                            displayName = name,
                            email = email,
                            phoneNumber = phone,
                            photoUri = photoUri,
                        )
                    }
                }
            }

            // Also search by email
            val emailCursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                ),
                "${ContactsContract.CommonDataKinds.Email.ADDRESS} LIKE ?",
                arrayOf("%$query%"),
                null,
            )

            emailCursor?.use {
                while (it.moveToNext()) {
                    val contactId = it.getString(0)
                    if (contactId in contacts) continue
                    val name = it.getString(1) ?: continue
                    val email = it.getString(2)

                    contacts[contactId] = ContactMethod(
                        contactId = contactId,
                        displayName = name,
                        email = email,
                        phoneNumber = getPhone(contactId),
                        photoUri = null,
                    )
                }
            }

            contacts.values.toList().take(20)
        }
    }

    override suspend fun getContactByEmail(email: String): ContactMethod? {
        return withContext(Dispatchers.IO) {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                ),
                "${ContactsContract.CommonDataKinds.Email.ADDRESS} = ?",
                arrayOf(email),
                null,
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val contactId = it.getString(0)
                    val name = it.getString(1) ?: email
                    ContactMethod(
                        contactId = contactId,
                        displayName = name,
                        email = email,
                        phoneNumber = getPhone(contactId),
                        photoUri = null,
                    )
                } else null
            }
        }
    }

    override suspend fun getRecentContacts(limit: Int): List<ContactMethod> {
        return withContext(Dispatchers.IO) {
            val contacts = mutableListOf<ContactMethod>()

            val cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ),
                null,
                null,
                "${ContactsContract.Contacts.LAST_TIME_CONTACTED} DESC",
            )

            cursor?.use {
                while (it.moveToNext() && contacts.size < limit) {
                    val contactId = it.getString(0)
                    val name = it.getString(1) ?: continue
                    val photoUri = it.getString(2)
                    val email = getEmail(contactId)
                    val phone = getPhone(contactId)

                    if (email != null || phone != null) {
                        contacts.add(
                            ContactMethod(
                                contactId = contactId,
                                displayName = name,
                                email = email,
                                phoneNumber = phone,
                                photoUri = photoUri,
                            )
                        )
                    }
                }
            }

            contacts
        }
    }

    // ─── Helpers ────────────────────────────────────────────

    private fun getEmail(contactId: String): String? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun getPhone(contactId: String): String? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
}
