package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.AppDatabase
import com.example.data.Note
import com.example.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen {
    HOME, EDITOR, FAVORITES, SETTINGS, TRASH
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class NoteViewModel(
    application: Application,
    private val repository: NoteRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("mando_ai_settings", Context.MODE_PRIVATE)

    var accentColorHex by mutableStateOf(prefs.getString("accent_color_hex", "#3B82F6") ?: "#3B82F6")
        private set

    val accentColor: androidx.compose.ui.graphics.Color
        get() = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(accentColorHex))

    var editorFontSize by mutableStateOf(prefs.getFloat("editor_font_size", 17f))
        private set

    var aiAutoFormatEnabled by mutableStateOf(prefs.getBoolean("ai_auto_format", true))
        private set

    var aiAutoSummarizeEnabled by mutableStateOf(prefs.getBoolean("ai_auto_summarize", true))
        private set

    var aiAutoTagsEnabled by mutableStateOf(prefs.getBoolean("ai_auto_tags", true))
        private set

    var secureLockEnabled by mutableStateOf(prefs.getBoolean("secure_lock", false))
        private set

    fun updateAccentColor(hex: String) {
        accentColorHex = hex
        prefs.edit().putString("accent_color_hex", hex).apply()
        com.example.ui.theme.DynamicAccentColor = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
    }

    fun updateEditorFontSize(size: Float) {
        editorFontSize = size
        prefs.edit().putFloat("editor_font_size", size).apply()
    }

    fun toggleAiAutoFormat() {
        aiAutoFormatEnabled = !aiAutoFormatEnabled
        prefs.edit().putBoolean("ai_auto_format", aiAutoFormatEnabled).apply()
    }

    fun toggleAiAutoSummarize() {
        aiAutoSummarizeEnabled = !aiAutoSummarizeEnabled
        prefs.edit().putBoolean("ai_auto_summarize", aiAutoSummarizeEnabled).apply()
    }

    fun toggleAiAutoTags() {
        aiAutoTagsEnabled = !aiAutoTagsEnabled
        prefs.edit().putBoolean("ai_auto_tags", aiAutoTagsEnabled).apply()
    }

    fun toggleSecureLock() {
        secureLockEnabled = !secureLockEnabled
        prefs.edit().putBoolean("secure_lock", secureLockEnabled).apply()
    }

    // Bottom Navigation Tab
    var currentScreen by mutableStateOf(AppScreen.HOME)
        private set

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Selected category filter
    private val _selectedCategory = MutableStateFlow("الكل")
    val selectedCategory = _selectedCategory.asStateFlow()

    // Sorting order
    private val _sortBy = MutableStateFlow("date_desc") // "date_desc", "date_asc", "title_asc"
    val sortBy = _sortBy.asStateFlow()

    // Notes collected from Room and combined with search query & category & sorting
    val notes: StateFlow<List<Note>> = combine(
        repository.allNotes,
        _searchQuery,
        _selectedCategory,
        _sortBy
    ) { allNotes, query, category, sortOrder ->
        var filtered = allNotes
        if (category != "الكل") {
            filtered = filtered.filter { it.category.lowercase() == category.lowercase() || (category == "مفضلة" && it.isFavorite) }
        }
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        }
        when (sortOrder) {
            "date_asc" -> filtered.sortedBy { it.timestamp }
            "title_asc" -> filtered.sortedBy { it.title.lowercase() }
            else -> filtered.sortedByDescending { it.timestamp }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Favorites screen notes
    val favoriteNotes: StateFlow<List<Note>> = combine(
        repository.favoriteNotes,
        _searchQuery
    ) { favorites, query ->
        if (query.isNotBlank()) {
            favorites.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        } else {
            favorites
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Trash screen notes
    val trashNotes: StateFlow<List<Note>> = combine(
        repository.trashNotes,
        _searchQuery
    ) { trash, query ->
        if (query.isNotBlank()) {
            trash.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        } else {
            trash
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Editor State
    var currentEditingNote by mutableStateOf<Note?>(null)
        private set
    var editorTitle by mutableStateOf("")
    var editorContent by mutableStateOf("")
    var editorColorHex by mutableStateOf("#112E51") // Default glass blue
    var editorCategory by mutableStateOf("عام")
    var editorIsFavorite by mutableStateOf(false)

    // AI States
    var aiSummary by mutableStateOf<String?>(null)
    var aiKeywords by mutableStateOf<String?>(null)
    var isGeneratingSummary by mutableStateOf(false)
    var isGeneratingKeywords by mutableStateOf(false)

    // Chat assistant inside Note
    var chatMessages = mutableStateOf<List<ChatMessage>>(emptyList())
    var chatInputText by mutableStateOf("")
    var isChatSending by mutableStateOf(false)

    init {
        // Initialize global dynamic accent color
        try {
            com.example.ui.theme.DynamicAccentColor = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(accentColorHex))
        } catch (e: Exception) {
            // Fallback
        }
        // Prepopulate on first launch if empty
        viewModelScope.launch {
            repository.allNotes.collect { list ->
                if (list.isEmpty()) {
                    prepopulateDatabase()
                }
            }
        }
    }

    fun selectScreen(screen: AppScreen) {
        currentScreen = screen
        if (screen == AppScreen.HOME || screen == AppScreen.FAVORITES || screen == AppScreen.TRASH) {
            currentEditingNote = null
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun changeSortOrder(order: String) {
        _sortBy.value = order
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    // Navigation and editor operations
    fun startNewNote() {
        currentEditingNote = null
        editorTitle = ""
        editorContent = ""
        editorColorHex = "#143A24" // emerald green default
        editorCategory = "عام"
        editorIsFavorite = false
        aiSummary = null
        aiKeywords = null
        chatMessages.value = emptyList()
        chatInputText = ""
        currentScreen = AppScreen.EDITOR
    }

    fun openNoteForEditing(note: Note) {
        currentEditingNote = note
        editorTitle = note.title
        editorContent = note.content
        editorColorHex = note.colorHex
        editorCategory = note.category
        editorIsFavorite = note.isFavorite
        aiSummary = note.aiSummary
        aiKeywords = note.aiKeywords
        chatMessages.value = emptyList()
        chatInputText = ""
        currentScreen = AppScreen.EDITOR
    }

    fun saveNote() {
        val titleText = editorTitle.trim().ifBlank { "ملاحظة جديدة" }
        val contentText = editorContent.trim()

        viewModelScope.launch {
            val noteToSave = Note(
                id = currentEditingNote?.id ?: 0,
                title = titleText,
                content = contentText,
                timestamp = System.currentTimeMillis(),
                colorHex = editorColorHex,
                isFavorite = editorIsFavorite,
                aiSummary = aiSummary,
                aiKeywords = aiKeywords,
                category = editorCategory
            )
            repository.insertNote(noteToSave)
            currentEditingNote = null
            currentScreen = AppScreen.HOME
        }
    }

    fun toggleFavorite(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(isFavorite = !note.isFavorite)
            repository.updateNote(updated)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            if (note.isInTrash) {
                repository.deleteNote(note)
            } else {
                repository.moveToTrash(note.id)
            }
            if (currentEditingNote?.id == note.id) {
                currentEditingNote = null
                currentScreen = AppScreen.HOME
            }
        }
    }

    fun restoreFromTrash(note: Note) {
        viewModelScope.launch {
            repository.restoreFromTrash(note.id)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    fun deleteAllNotes() {
        viewModelScope.launch {
            repository.deleteAllNotes()
        }
    }

    fun changeEditorColor(hex: String) {
        editorColorHex = hex
    }

    // AI Integrations

    fun generateAISummary() {
        if (editorContent.isBlank()) {
            aiSummary = "يرجى كتابة بعض المحتوى أولاً لتلخيصه بالذكاء الاصطناعي."
            return
        }
        viewModelScope.launch {
            isGeneratingSummary = true
            aiSummary = "جاري التلخيص بالذكاء الاصطناعي..."
            val result = GeminiClient.summarizeNote(editorTitle, editorContent)
            aiSummary = result
            isGeneratingSummary = false
            
            // Auto extract keywords too
            if (aiKeywords.isNullOrBlank()) {
                generateAIKeywords()
            }
        }
    }

    fun generateAIKeywords() {
        if (editorContent.isBlank()) return
        viewModelScope.launch {
            isGeneratingKeywords = true
            val result = GeminiClient.extractKeywords(editorTitle, editorContent)
            aiKeywords = result
            isGeneratingKeywords = false
        }
    }

    fun sendChatMessage() {
        val msgText = chatInputText.trim()
        if (msgText.isBlank()) return

        val userMsg = ChatMessage(text = msgText, isUser = true)
        chatMessages.value = chatMessages.value + userMsg
        chatInputText = ""
        isChatSending = true

        viewModelScope.launch {
            // Get chat history excluding system instructions, format as Pairs
            val history = chatMessages.value.dropLast(1).map { it.text to it.isUser }
            val replyText = GeminiClient.chatAboutNote(
                noteTitle = editorTitle,
                noteContent = editorContent,
                chatHistory = history,
                newMessage = msgText
            )
            val modelMsg = ChatMessage(text = replyText, isUser = false)
            chatMessages.value = chatMessages.value + modelMsg
            isChatSending = false
        }
    }

    // Format Timestamp for display (e.g. "June, 7:50 PM 12")
    fun formatNoteTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM. h:mm a d", Locale.ENGLISH)
        return sdf.format(Date(timestamp))
    }

    // Populate database on first launch to match the screenshot notes exactly!
    private suspend fun prepopulateDatabase() {
        val sampleNotes = listOf(
            Note(
                title = "قائمة المشتريات",
                content = "المعذرة، لا أستطيع المساعدة في ذلك. لكن يمكنك إضافة: حليب، بيض، خبز، فواكه وخضروات طازجة للوجبات الأسبوعية.",
                timestamp = parseDateToTimestamp("June. 2:42 AM 18"),
                colorHex = "#143A24", // Emerald Green
                isFavorite = false,
                aiSummary = "- قائمة الطلبات الأساسية للمطبخ\n- تم تدوين خيارات الفواكه والألبان",
                aiKeywords = "تسوق, مشتريات, منزل",
                category = "شخصي"
            ),
            Note(
                title = "فكرة تطبيق جديد",
                content = "تطبيق لإدارة الوقت يعتمد على الذكاء الاصطناعي... يقوم بتحليل جدول المستخدم تلقائياً واقتراح فترات الراحة والتركيز المناسبة.",
                timestamp = parseDateToTimestamp("June. 2:43 AM 18"),
                colorHex = "#3C102C", // Burgundy/Wine red
                isFavorite = false,
                aiSummary = "- ابتكار تطبيق لتنظيم الوقت بالذكاء الاصطناعي\n- تحليل تلقائي للإنتاجية وتقسيم مهام اليوم",
                aiKeywords = "تطوير, ذكاء اصطناعي, إنتاجية",
                category = "أفكار"
            ),
            Note(
                title = "أفكار للمشروع الـ...",
                content = "بحث عن تقنيات الذكاء الاصطناعي الحديثة وتطوير خوارزميات التعلم الآلي لدمجها في تطبيقات الجوال والويب الذكية.",
                timestamp = parseDateToTimestamp("June. 7:50 PM 12"),
                colorHex = "#112E51", // Blue
                isFavorite = true,
                aiSummary = "- دراسة تقنيات تعلم الآلة للموبايل\n- بحث سبل دمج نماذج اللغة الكبيرة محلياً",
                aiKeywords = "برمجة, عمل, تقنية",
                category = "عمل"
            ),
            Note(
                title = "مراجعة كتاب التـ...",
                content = "الفصل الأول: النظامان يشرح الكاتب كيف يعمل التفكير البشري السريع والتفكير البطيء العقلاني، وكيفية اتخاذ القرارات اليومية.",
                timestamp = parseDateToTimestamp("June. 7:50 PM 12"),
                colorHex = "#38310E", // Mustard/Ochre
                isFavorite = true,
                aiSummary = "- مقارنة بين نظام التفكير السريع ونظام التفكير الواعي\n- شرح التحيزات الإدراكية وكيفية تجنب القرارات المتسرعة",
                aiKeywords = "كتب, مراجعة, ثقافة",
                category = "مراجعة"
            ),
            Note(
                title = "ملخص اجتماع الـ...",
                content = "تمت مناقشة خطة العمل للربع القادم وتحديد الأهداف الرئيسية وتوزيع المهام على فريق التطوير والتسويق لضمان الإطلاق الناجح.",
                timestamp = parseDateToTimestamp("June. 7:50 PM 12"),
                colorHex = "#241738", // Dark Violet/Purple
                isFavorite = false,
                aiSummary = "- تحديد خارطة الطريق للربع السنوي القادم\n- إسناد المهام للمطورين وفريق التسويق لضمان نجاح التوزيع",
                aiKeywords = "اجتماع, عمل, تخطيط",
                category = "عمل"
            )
        )
        sampleNotes.forEach { repository.insertNote(it) }
    }

    private fun parseDateToTimestamp(dateStr: String): Long {
        // Just return a modern timestamp based on the current time and day offsets
        // to make it look realistic, or try parsing.
        return try {
            val sdf = SimpleDateFormat("MMM. h:mm a d", Locale.ENGLISH)
            val date = sdf.parse(dateStr)
            date?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}

class NoteViewModelFactory(
    private val application: Application,
    private val repository: NoteRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
