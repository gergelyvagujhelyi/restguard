package com.restguard.ui.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restguard.domain.model.ContactMethod
import com.restguard.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactPickerViewModel @Inject constructor(
    private val contactRepo: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactPickerUiState())
    val uiState: StateFlow<ContactPickerUiState> = _uiState.asStateFlow()

    fun loadRecent() {
        viewModelScope.launch {
            val recent = contactRepo.getRecentContacts(10)
            _uiState.update { it.copy(recentContacts = recent) }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.length >= 2) {
            viewModelScope.launch {
                val results = contactRepo.searchContacts(query)
                _uiState.update { it.copy(results = results) }
            }
        } else {
            _uiState.update { it.copy(results = emptyList()) }
        }
    }

    fun select(contact: ContactMethod, method: String) {
        _uiState.update { it.copy(selectedContact = contact, selectedMethod = method) }
    }
}
