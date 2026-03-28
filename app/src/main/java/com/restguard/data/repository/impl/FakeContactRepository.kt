package com.restguard.data.repository.impl

import com.restguard.domain.model.ContactMethod
import com.restguard.domain.repository.ContactRepository

/**
 * Fake contacts for development and testing.
 */
class FakeContactRepository : ContactRepository {

    private val contacts = listOf(
        ContactMethod("1", "Alice Johnson", "alice@co.com", "+1555000001", null),
        ContactMethod("2", "Bob Smith", "bob@co.com", "+1555000002", null),
        ContactMethod("3", "Carol Williams", "carol@co.com", "+1555000003", null),
        ContactMethod("4", "Dave Brown", "dave@co.com", "+1555000004", null),
        ContactMethod("5", "Eve Davis", "eve@co.com", "+1555000005", null),
        ContactMethod("6", "Manager", "manager@co.com", "+1555000006", null),
        ContactMethod("7", "Designer", "designer@co.com", "+1555000007", null),
        ContactMethod("8", "PM Lead", "pm@co.com", "+1555000008", null),
    )

    override suspend fun searchContacts(query: String): List<ContactMethod> {
        val q = query.lowercase()
        return contacts.filter {
            it.displayName.lowercase().contains(q) ||
                it.email?.lowercase()?.contains(q) == true ||
                it.phoneNumber?.contains(q) == true
        }
    }

    override suspend fun getContactByEmail(email: String): ContactMethod? {
        return contacts.find { it.email.equals(email, ignoreCase = true) }
    }

    override suspend fun getRecentContacts(limit: Int): List<ContactMethod> {
        return contacts.take(limit)
    }
}
