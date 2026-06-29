package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.NoteRepository
import com.example.ui.NoteViewModel
import com.example.ui.NoteViewModelFactory
import com.example.ui.NotesAppScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Database & Repository
        val database = AppDatabase.getDatabase(this)
        val repository = NoteRepository(database.noteDao())

        // Create ViewModel
        val viewModel = ViewModelProvider(
            this,
            NoteViewModelFactory(application, repository)
        )[NoteViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.ui.theme.DarkBg
                ) {
                    NotesAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}
