package com.restguard.ui.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restguard.domain.model.ContactMethod

data class ContactPickerUiState(
    val query: String = "",
    val results: List<ContactMethod> = emptyList(),
    val recentContacts: List<ContactMethod> = emptyList(),
    val isLoading: Boolean = false,
    val selectedContact: ContactMethod? = null,
    val selectedMethod: String? = null, // "email" or "phone"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerScreen(
    eventId: String,
    onBack: () -> Unit = {},
    onContactSelected: (ContactMethod, String) -> Unit = { _, _ -> },
    viewModel: ContactPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRecent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Contact to Notify") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Search bar
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(16.dp))

            val contacts = if (state.query.isBlank()) state.recentContacts else state.results

            if (contacts.isEmpty() && state.query.isNotBlank()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No contacts found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                if (state.query.isBlank()) {
                    Text(
                        "Recent Contacts",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(contacts) { contact ->
                        ContactCard(
                            contact = contact,
                            isSelected = state.selectedContact?.contactId == contact.contactId,
                            onSelectEmail = {
                                viewModel.select(contact, "email")
                                contact.email?.let { email ->
                                    onContactSelected(contact, email)
                                }
                            },
                            onSelectPhone = {
                                viewModel.select(contact, "phone")
                                contact.phoneNumber?.let { phone ->
                                    onContactSelected(contact, phone)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCard(
    contact: ContactMethod,
    isSelected: Boolean,
    onSelectEmail: () -> Unit,
    onSelectPhone: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            contact.displayName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    contact.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                contact.email?.let { email ->
                    OutlinedButton(
                        onClick = onSelectEmail,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Email, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(email, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
                contact.phoneNumber?.let { phone ->
                    OutlinedButton(
                        onClick = onSelectPhone,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Phone, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(phone, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
