package com.example.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val favoriteNotes: Flow<List<Note>> = noteDao.getFavoriteNotes()
    val trashNotes: Flow<List<Note>> = noteDao.getTrashNotes()

    suspend fun getNoteById(id: Int): Note? {
        return noteDao.getNoteById(id)
    }

    suspend fun insertNote(note: Note): Long {
        return noteDao.insertNote(note)
    }

    suspend fun updateNote(note: Note) {
        noteDao.updateNote(note)
    }

    suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note)
    }

    suspend fun deleteNoteById(id: Int) {
        noteDao.deleteNoteById(id)
    }

    suspend fun deleteAllNotes() {
        noteDao.deleteAllNotes()
    }

    suspend fun moveToTrash(id: Int) {
        noteDao.moveToTrash(id)
    }

    suspend fun restoreFromTrash(id: Int) {
        noteDao.restoreFromTrash(id)
    }

    suspend fun emptyTrash() {
        noteDao.emptyTrash()
    }
}
