package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: String, // hex color string for Glassmorphic background
    val isFavorite: Boolean = false,
    val aiSummary: String? = null,
    val aiKeywords: String? = null,
    val category: String = "All",
    val isInTrash: Boolean = false
) : Serializable
