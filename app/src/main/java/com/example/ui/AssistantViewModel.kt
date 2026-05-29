package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.CalendarEvent
import com.example.data.model.CommandLog
import com.example.data.model.Email
import com.example.data.repository.AssistantRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class AssistantViewModel(private val repository: AssistantRepository) : ViewModel() {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    // Active tab: "ASSISTANT", "EMAILS", "LOGS"
    private val _activeTab = MutableStateFlow("ASSISTANT")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    // Email folder: "INBOX", "SENT"
    private val _emailFolder = MutableStateFlow("INBOX")
    val emailFolder: StateFlow<String> = _emailFolder.asStateFlow()

    // Speaking event flow for TTS synthesis
    private val _ttsSpeakEvent = MutableSharedFlow<String>(replay = 0)
    val ttsSpeakEvent: SharedFlow<String> = _ttsSpeakEvent.asSharedFlow()

    // Selected email for detail view
    private val _selectedEmail = MutableStateFlow<Email?>(null)
    val selectedEmail: StateFlow<Email?> = _selectedEmail.asStateFlow()

    // Selected calendar event for detail view
    private val _selectedCalendarEvent = MutableStateFlow<CalendarEvent?>(null)
    val selectedCalendarEvent: StateFlow<CalendarEvent?> = _selectedCalendarEvent.asStateFlow()

    // Log & Email Data flows
    val allLogs: StateFlow<List<CommandLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEmails: StateFlow<List<Email>> = repository.allEmails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadEmailCount: StateFlow<Int> = repository.unreadEmailCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val displayedEmails: StateFlow<List<Email>> = combine(allEmails, emailFolder) { emails, folder ->
        emails.filter { it.folder == folder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calendar Data flows
    val allCalendarEvents: StateFlow<List<CalendarEvent>> = repository.allLocalEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _nativeEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val nativeEvents: StateFlow<List<CalendarEvent>> = _nativeEvents.asStateFlow()

    fun refreshNativeEvents() {
        if (repository.hasCalendarPermission()) {
            _nativeEvents.value = repository.getNativeCalendarEvents()
        }
    }

    fun hasCalendarPermission(): Boolean = repository.hasCalendarPermission()

    // Combined or selected UI list
    val calendarFeed: StateFlow<List<CalendarEvent>> = combine(allCalendarEvents, nativeEvents) { local, native ->
        (local + native).distinctBy { "${it.title}_${it.startTime}" }.sortedBy { it.startTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Suggestions lists
    val suggestions = listOf(
        "Check my recent emails",
        "কোম ইমেইল চেক করো",
        "Check upcoming appointments",
        "আমার আজকের মিটিং কি?",
        "Schedule a lunch alignment tomorrow",
        "একটি মেইল চেক কর"
    )

    init {
        viewModelScope.launch {
            repository.seedDatabaseIfEmpty()
            refreshNativeEvents()
        }
    }

    fun setCommandInput(input: String) {
        _commandInput.value = input
    }

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    fun setEmailFolder(folder: String) {
        _emailFolder.value = folder
    }

    fun selectEmail(email: Email?) {
        _selectedEmail.value = email
        if (email != null && !email.isRead) {
            viewModelScope.launch {
                repository.updateEmail(email.copy(isRead = true))
            }
        }
    }

    fun toggleListening() {
        if (_isListening.value) {
            // Stop listening and process
            _isListening.value = false
            val command = _commandInput.value
            if (command.isNotBlank()) {
                submitCommand(command)
            }
        } else {
            // Start listening simulation
            _commandInput.value = ""
            _isListening.value = true
        }
    }

    fun selectCalendarEvent(event: CalendarEvent?) {
        _selectedCalendarEvent.value = event
    }

    fun submitCommand(rawCommandText: String) {
        if (rawCommandText.isBlank()) return

        _isLoading.value = true
        viewModelScope.launch {
            val responseRaw = repository.getGeminiResponse(rawCommandText)
            
            // Parse for actions
            var cleanedSpeech = responseRaw
            var detectedAction = "NONE"
            var actionStatus = "NONE"

            if (responseRaw.contains("[ACTION: GMAIL_CHECK]", ignoreCase = true)) {
                detectedAction = "GMAIL_CHECK"
                actionStatus = "EXECUTED"
                cleanedSpeech = responseRaw.replace("[ACTION: GMAIL_CHECK]", "", ignoreCase = true).trim()
                _activeTab.value = "EMAILS"
                _emailFolder.value = "INBOX"
            } else if (responseRaw.contains("[ACTION: GMAIL_SEND]", ignoreCase = true)) {
                detectedAction = "GMAIL_SEND"
                actionStatus = "EXECUTED"
                cleanedSpeech = responseRaw.replace("[ACTION: GMAIL_SEND]", "", ignoreCase = true).trim()
                
                // Simulate sending a real mail in background
                val extractedSubject = extractSubject(rawCommandText)
                val recipientName = extractRecipient(rawCommandText)
                val simulatedSentEmail = Email(
                    sender = "Me",
                    senderEmail = "mdmasumrana01920@gmail.com",
                    subject = extractedSubject,
                    body = "Assalamu Alaikum $recipientName,\n\nSent automatically via Voice Assistant.\nCommand: \"$rawCommandText\"",
                    folder = "SENT",
                    isRead = true
                )
                repository.insertEmail(simulatedSentEmail)
                _activeTab.value = "EMAILS"
                _emailFolder.value = "SENT"
            } else if (responseRaw.contains("[ACTION: CALENDAR_CHECK]", ignoreCase = true)) {
                detectedAction = "CALENDAR_CHECK"
                actionStatus = "EXECUTED"
                cleanedSpeech = responseRaw.replace("[ACTION: CALENDAR_CHECK]", "", ignoreCase = true).trim()
                _activeTab.value = "CALENDAR"
                refreshNativeEvents()
            } else if (responseRaw.contains("[ACTION: CALENDAR_ADD]", ignoreCase = true)) {
                detectedAction = "CALENDAR_ADD"
                actionStatus = "EXECUTED"
                cleanedSpeech = responseRaw.replace("[ACTION: CALENDAR_ADD]", "", ignoreCase = true).trim()
                
                // Extract event title from instruction
                val extractedTitle = extractEventTitle(rawCommandText)
                val now = System.currentTimeMillis()
                val event = CalendarEvent(
                    title = extractedTitle,
                    description = "Created automatically via Voice Assistant from query: \"$rawCommandText\"",
                    location = "Virtual Meeting Link",
                    startTime = now + 3600000 * 2, // 2 hours from now
                    endTime = now + 3600000 * 3
                )
                repository.insertCalendarEvent(event)
                refreshNativeEvents()
                _activeTab.value = "CALENDAR"
            } else if (responseRaw.contains("[ACTION: CALENDAR_DETAIL]", ignoreCase = true)) {
                detectedAction = "CALENDAR_DETAIL"
                actionStatus = "EXECUTED"
                cleanedSpeech = responseRaw.replace("[ACTION: CALENDAR_DETAIL]", "", ignoreCase = true).trim()
                _activeTab.value = "CALENDAR"
                
                // Retrieve list of events and highlight/select the first upcoming one as the details focus
                refreshNativeEvents()
                val events = calendarFeed.value
                if (events.isNotEmpty()) {
                    _selectedCalendarEvent.value = events.first()
                }
            }

            // Record log in Room database
            val log = CommandLog(
                commandText = rawCommandText,
                responseSpeech = cleanedSpeech,
                detectedAction = detectedAction,
                actionStatus = actionStatus
            )
            repository.insertLog(log)

            // Trigger TTS speech
            _ttsSpeakEvent.emit(cleanedSpeech)

            _commandInput.value = ""
            _isLoading.value = false
        }
    }

    fun composeEmailDirectly(recipient: String, subject: String, body: String) {
        viewModelScope.launch {
            val email = Email(
                sender = "Me",
                senderEmail = "mdmasumrana01920@gmail.com",
                subject = subject,
                body = body,
                folder = "SENT",
                isRead = true
            )
            repository.insertEmail(email)
            
            // Log direct action
            val log = CommandLog(
                commandText = "Direct compose: Send email to $recipient",
                responseSpeech = "Email successfully composed and dispatched to $recipient.",
                detectedAction = "GMAIL_SEND",
                actionStatus = "EXECUTED"
            )
            repository.insertLog(log)
            _activeTab.value = "EMAILS"
            _emailFolder.value = "SENT"
            _ttsSpeakEvent.emit("Email successfully composed and dispatched.")
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun resetMockEmails() {
        viewModelScope.launch {
            repository.clearEmails()
            repository.seedDatabaseIfEmpty()
        }
    }

    fun addCalendarEventDirectly(title: String, description: String, location: String, startTime: Long, endTime: Long) {
        viewModelScope.launch {
            val event = CalendarEvent(
                title = title,
                description = description,
                location = location,
                startTime = startTime,
                endTime = endTime
            )
            repository.insertCalendarEvent(event)
            refreshNativeEvents()
            
            // Log direct action
            val log = CommandLog(
                commandText = "Direct compose: Add calendar event \"$title\"",
                responseSpeech = "Calendar event \"$title\" successfully created.",
                detectedAction = "CALENDAR_ADD",
                actionStatus = "EXECUTED"
            )
            repository.insertLog(log)
            _activeTab.value = "CALENDAR"
            _ttsSpeakEvent.emit("Calendar event successfully created.")
        }
    }

    fun deleteCalendarEventDirectly(event: CalendarEvent) {
        viewModelScope.launch {
            repository.deleteCalendarEvent(event)
            refreshNativeEvents()
            
            val log = CommandLog(
                commandText = "Direct delete: Remove \"${event.title}\"",
                responseSpeech = "Calendar event removed successfully.",
                detectedAction = "CALENDAR_DELETE",
                actionStatus = "EXECUTED"
            )
            repository.insertLog(log)
        }
    }

    fun resetCalendarEvents() {
        viewModelScope.launch {
            repository.clearCalendarEvents()
            repository.seedDatabaseIfEmpty()
            refreshNativeEvents()
        }
    }

    private fun extractEventTitle(text: String): String {
        val lower = text.lowercase()
        val index = lower.indexOf("meeting about ")
        if (index != -1) {
            return "Meeting: " + text.substring(index + 14).trim().replaceFirstChar { it.uppercase() }
        }
        val scheduleIdx = lower.indexOf("schedule ")
        if (scheduleIdx != -1) {
            return text.substring(scheduleIdx + 9).trim().replaceFirstChar { it.uppercase() }
        }
        val addIdx = lower.indexOf("add ")
        if (addIdx != -1) {
            return text.substring(addIdx + 4).trim().replaceFirstChar { it.uppercase() }
        }
        return "📅 Scheduled Event"
    }

    private fun extractRecipient(text: String): String {
        val lower = text.lowercase()
        val toIndex = lower.indexOf("to ")
        if (toIndex != -1) {
            val endWord = lower.substring(toIndex + 3).split(" ").firstOrNull() ?: "Recipient"
            return endWord.replaceFirstChar { it.uppercase() }
        }
        return "Rana"
    }

    private fun extractSubject(text: String): String {
        val lower = text.lowercase()
        val aboutIndex = lower.indexOf("about ")
        if (aboutIndex != -1) {
            return text.substring(aboutIndex + 6).replaceFirstChar { it.uppercase() }
        }
        return "Background Task Update"
    }
}

class AssistantViewModelFactory(private val repository: AssistantRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AssistantViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
