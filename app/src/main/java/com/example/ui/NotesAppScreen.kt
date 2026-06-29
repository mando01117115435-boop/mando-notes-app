package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Note
import com.example.ui.theme.*

@Composable
fun NotesAppScreen(viewModel: NoteViewModel) {
    // Force RTL layout direction as shown in the Arabic screenshot
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val currentScreen = viewModel.currentScreen
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val notesList by viewModel.notes.collectAsState()
        val favoriteNotesList by viewModel.favoriteNotes.collectAsState()
        val selectedCategory by viewModel.selectedCategory.collectAsState()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                NotesDrawerContent(
                    notesCount = notesList.size,
                    favoritesCount = favoriteNotesList.size,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { viewModel.selectCategory(it) },
                    onSelectScreen = { viewModel.selectScreen(it) },
                    onDeleteAllNotes = { viewModel.deleteAllNotes() },
                    onClose = { scope.launch { drawerState.close() } }
                )
            },
            gesturesEnabled = currentScreen != AppScreen.EDITOR
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        if (currentScreen != AppScreen.EDITOR) {
                            GlassBottomNavigationBar(
                                selectedTab = currentScreen,
                                onTabSelected = { viewModel.selectScreen(it) }
                            )
                        }
                    },
                    floatingActionButton = {
                        if (currentScreen == AppScreen.HOME) {
                            FloatingActionButton(
                                onClick = { viewModel.startNewNote() },
                                containerColor = BluePrimary,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .testTag("add_note_fab")
                                    .padding(bottom = 16.dp, start = 16.dp) // Offset slightly above bottom bar
                                    .size(56.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "كتابة ملاحظة جديدة"
                                )
                            }
                        }
                    },
                    floatingActionButtonPosition = FabPosition.Start // Put FAB on the left as in screenshot!
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        when (currentScreen) {
                            AppScreen.HOME -> HomeScreen(
                                viewModel = viewModel,
                                  onMenuClick = { scope.launch { drawerState.open() } }
                            )
                            AppScreen.FAVORITES -> FavoritesScreen(viewModel)
                            AppScreen.SETTINGS -> SettingsScreen(viewModel)
                            AppScreen.EDITOR -> NoteEditorScreen(viewModel)
                            AppScreen.TRASH -> TrashScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

// --- Home Screen ---
@Composable
fun HomeScreen(viewModel: NoteViewModel, onMenuClick: () -> Unit) {
    val notes by viewModel.notes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val gridState = rememberLazyGridState()

    // Determine scroll progress for One UI collapsing header effect
    val showStickyTitle by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || (gridState.firstVisibleItemScrollOffset > 80)
        }
    }

    val stickyAlpha by animateFloatAsState(
        targetValue = if (showStickyTitle) 1f else 0f,
        label = "stickyTitleAlpha"
    )

    val headerBackground by animateColorAsState(
        targetValue = if (showStickyTitle) DarkSurface else Color.Transparent,
        label = "headerBackground"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Scrollable content containing Large Title, Search, Categories, and Grid
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 56.dp, bottom = 100.dp), // Padding for top sticky bar and bottom navigation bar
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Large Samsung-style Title & Subtitle
            item(span = { GridItemSpan(2) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "الملاحظات",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            notes.isEmpty() -> "لا توجد ملاحظات"
                            notes.size == 1 -> "ملاحظة واحدة"
                            notes.size == 2 -> "ملاحظتان"
                            notes.size in 3..10 -> "${notes.size} ملاحظات"
                            else -> "${notes.size} ملاحظة"
                        },
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2. Beautiful Samsung-style Search Bar (Pill shaped, elegant)
            item(span = { GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(50)) // Pill shape matching Samsung UI
                        .background(Color(0xFF2C2D31)) // Dark grey charcoal background
                        .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(BluePrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_field"),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "ابحث في الملاحظات أو اسأل Mando AI...",
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateSearchQuery("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "مسح",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Category Row (Filtered options)
            item(span = { GridItemSpan(2) }) {
                CategoryRow(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { viewModel.selectCategory(it) }
                )
            }

            // Spacer item to separate list items nicely
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 4. Notes grid list or empty state
            if (notes.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            title = "لا توجد ملاحظات",
                            subtitle = "اضغط على زر القلم للبدء في كتابة ملاحظتك الذكية الأولى مع Mando AI!"
                        )
                    }
                }
            } else {
                items(notes) { note ->
                    NoteCard(
                        note = note,
                        onNoteClick = { viewModel.openNoteForEditing(note) },
                        onFavoriteToggle = { viewModel.toggleFavorite(note) },
                        onDeleteClick = { viewModel.deleteNote(note) },
                        formatDate = { viewModel.formatNoteTimestamp(it) }
                    )
                }
            }
        }

        // 5. Sticky Top App Bar (Samsung One UI collapsing header overlay)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .background(headerBackground)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Hamburger icon
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.testTag("menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "القائمة",
                    tint = Color.White
                )
            }

            // Sticky Collapsible Title (only visible on scroll)
            Text(
                text = "الملاحظات",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = stickyAlpha),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .graphicsLayer(alpha = stickyAlpha),
                textAlign = TextAlign.Start
            )

            // More Options Dropdown
            var showSortMenu by remember { mutableStateOf(false) }
            val currentSortOrder by viewModel.sortBy.collectAsState()

            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "خيارات إضافية",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentSortOrder == "date_desc") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = BluePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                Text("الأحدث أولاً", color = Color.White)
                            }
                        },
                        onClick = {
                            viewModel.changeSortOrder("date_desc")
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentSortOrder == "date_asc") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = BluePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                Text("الأقدم أولاً", color = Color.White)
                            }
                        },
                        onClick = {
                            viewModel.changeSortOrder("date_asc")
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentSortOrder == "title_asc") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = BluePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                Text("أبجديًا (أ-ي)", color = Color.White)
                            }
                        },
                        onClick = {
                            viewModel.changeSortOrder("title_asc")
                            showSortMenu = false
                        }
                    )
                }
            }
        }
    }
}

// --- Favorites Screen ---
@Composable
fun FavoritesScreen(viewModel: NoteViewModel) {
    val favoriteNotes by viewModel.favoriteNotes.collectAsState()
    val gridState = rememberLazyGridState()

    // Determine scroll progress for One UI collapsing header effect
    val showStickyTitle by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || (gridState.firstVisibleItemScrollOffset > 60)
        }
    }

    val stickyAlpha by animateFloatAsState(
        targetValue = if (showStickyTitle) 1f else 0f,
        label = "favoritesStickyTitleAlpha"
    )

    val headerBackground by animateColorAsState(
        targetValue = if (showStickyTitle) DarkSurface else Color.Transparent,
        label = "favoritesHeaderBackground"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Scrollable content
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 56.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Large Title
            item(span = { GridItemSpan(2) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "المفضلة",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.testTag("favorites_header")
                        )
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            favoriteNotes.isEmpty() -> "لا توجد ملاحظات مفضلة"
                            favoriteNotes.size == 1 -> "ملاحظة مفضلة واحدة"
                            favoriteNotes.size == 2 -> "ملاحظتان مفضلتان"
                            favoriteNotes.size in 3..10 -> "${favoriteNotes.size} ملاحظات مفضلة"
                            else -> "${favoriteNotes.size} ملاحظة مفضلة"
                        },
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Space
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Grid items
            if (favoriteNotes.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            title = "المفضلة فارغة",
                            subtitle = "قم بتمييز الملاحظات الهامة بالنجمة لتظهر هنا بسهولة وسرعة."
                        )
                    }
                }
            } else {
                items(favoriteNotes) { note ->
                    NoteCard(
                        note = note,
                        onNoteClick = { viewModel.openNoteForEditing(note) },
                        onFavoriteToggle = { viewModel.toggleFavorite(note) },
                        onDeleteClick = { viewModel.deleteNote(note) },
                        formatDate = { viewModel.formatNoteTimestamp(it) }
                    )
                }
            }
        }

        // Sticky Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .background(headerBackground)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "المفضلة",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = stickyAlpha),
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer(alpha = stickyAlpha),
                textAlign = TextAlign.Start
            )
        }
    }
}

// --- Settings Screen ---
@Composable
fun SettingsScreen(viewModel: NoteViewModel) {
    val scrollState = rememberLazyGridState()

    // Determine scroll progress for One UI collapsing header effect
    val showStickyTitle by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0 || (scrollState.firstVisibleItemScrollOffset > 60)
        }
    }

    val stickyAlpha by animateFloatAsState(
        targetValue = if (showStickyTitle) 1f else 0f,
        label = "settingsStickyTitleAlpha"
    )

    val headerBackground by animateColorAsState(
        targetValue = if (showStickyTitle) DarkSurface else Color.Transparent,
        label = "settingsHeaderBackground"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Scrollable settings items
        LazyVerticalGrid(
            state = scrollState,
            columns = GridCells.Fixed(1),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 56.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. One UI Large Title
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "الضبط المتقدم",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "خصص مساعدك الذكي ومظهر مذكراتك الفريدة",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2. Mando AI Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "مساعد Mando AI الذكي",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "مفعل وجاهز للعمل مع نموذج Gemini Pro الفائق. يمكنك تلخيص ملاحظاتك، ترجمتها، صياغة أفكارك بشكل احترافي، ومناقشتها فورياً بالذكاء الاصطناعي.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // 3. Group: AI Settings
            item {
                SettingsGroupHeader(title = "خيارات الذكاء الاصطناعي")
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.AutoAwesome,
                    title = "التنسيق التلقائي بالذكاء الاصطناعي",
                    subtitle = "تحسين العناوين، تصحيح الأخطاء، وإبراز النقاط الهامة تلقائياً",
                    checked = viewModel.aiAutoFormatEnabled,
                    onCheckedChange = { viewModel.toggleAiAutoFormat() }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Notes,
                    title = "توليد ملخص تلقائي",
                    subtitle = "إنشاء كبسولة تلخيصية للملاحظات الطويلة في غضون ثوانٍ",
                    checked = viewModel.aiAutoSummarizeEnabled,
                    onCheckedChange = { viewModel.toggleAiAutoSummarize() }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Label,
                    title = "استخراج تصنيفات ذكية ومفاتيح",
                    subtitle = "استنتاج الكلمات الدلالية لتسهيل البحث والفلترة مستقبلاً",
                    checked = viewModel.aiAutoTagsEnabled,
                    onCheckedChange = { viewModel.toggleAiAutoTags() }
                )
            }

            // 4. Group: Personalization & Appearance
            item {
                SettingsGroupHeader(title = "المظهر والتخصيص")
            }

            item {
                SettingsColorPickerItem(
                    icon = Icons.Default.Palette,
                    title = "لون سمة التطبيق الأساسي",
                    subtitle = "اختر لونك المفضل ليتغير مظهر التطبيق بالكامل فورياً",
                    selectedColorHex = viewModel.accentColorHex,
                    onColorSelected = { viewModel.updateAccentColor(it) }
                )
            }

            item {
                SettingsFontSizePickerItem(
                    icon = Icons.Default.TextFormat,
                    title = "حجم الخط الافتراضي في المحرر",
                    currentSize = viewModel.editorFontSize,
                    onSizeSelected = { viewModel.updateEditorFontSize(it) }
                )
            }

            // 5. Group: Security
            item {
                SettingsGroupHeader(title = "الأمان والحماية")
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Lock,
                    title = "تفعيل قفل الخصوصية الآمن",
                    subtitle = "طلب كلمة المرور عند فتح الملاحظات الحساسة والمشفرة",
                    checked = viewModel.secureLockEnabled,
                    onCheckedChange = { viewModel.toggleSecureLock() }
                )
            }

            // 6. Group: About & Info
            item {
                SettingsGroupHeader(title = "حول التطبيق")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "إصدار التطبيق والترخيص",
                    subtitle = "Mando AI Notes Pro - إصدار v1.2.0 (مستقر) مبني بأحدث التقنيات."
                )
            }
        }

        // Sticky Collapsible Top Bar (Samsung style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .background(headerBackground)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الضبط والخيارات",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = stickyAlpha),
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer(alpha = stickyAlpha),
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        color = BluePrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp, start = 8.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(BluePrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BluePrimary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun SettingsColorPickerItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selectedColorHex: String,
    onColorSelected: (String) -> Unit
) {
    val colorsList = listOf(
        "#3B82F6" to "أزرق",
        "#8B5CF6" to "بنفسجي",
        "#EC4899" to "وردي",
        "#14B8A6" to "تركواز",
        "#F59E0B" to "برتقالي"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BluePrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            colorsList.forEach { (hex, name) ->
                val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                val color = Color(android.graphics.Color.parseColor(hex))
                
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(hex) }
                )
            }
        }
    }
}

@Composable
fun SettingsFontSizePickerItem(
    icon: ImageVector,
    title: String,
    currentSize: Float,
    onSizeSelected: (Float) -> Unit
) {
    val sizes = listOf(
        14f to "صغير",
        17f to "متوسط",
        22f to "كبير"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BluePrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sizes.forEach { (size, label) ->
                val isSelected = currentSize == size
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) BluePrimary else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                        .clickable { onSizeSelected(size) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = BluePrimary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

// --- Category Selectors Row ---
@Composable
fun CategoryRow(selectedCategory: String, onCategorySelected: (String) -> Unit) {
    val categories = listOf("الكل", "أفكار", "عمل", "شخصي", "مراجعة")

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) BluePrimary else Color.White.copy(alpha = 0.05f),
                label = "bgColor"
            )
            val textColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) BluePrimary else Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = category,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// --- Note Card Component ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    onNoteClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDeleteClick: () -> Unit,
    onRestoreClick: (() -> Unit)? = null,
    formatDate: (Long) -> String
) {
    // Solid background color of the note from database
    val cardColor = Color(android.graphics.Color.parseColor(note.colorHex))
    var showContextMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(cardColor)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(24.dp)
            )
            .combinedClickable(
                onClick = onNoteClick,
                onLongClick = { showContextMenu = true }
            )
            .padding(16.dp)
            .heightIn(min = 140.dp, max = 220.dp)
    ) {
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier.background(DarkSurface)
        ) {
            if (note.isInTrash) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("استعادة الملاحظة", color = Color.White)
                        }
                    },
                    onClick = {
                        showContextMenu = false
                        onRestoreClick?.invoke()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(18.dp)
                            )
                            Text("حذف نهائي", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                        }
                    },
                    onClick = {
                        showContextMenu = false
                        onDeleteClick()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { 
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(18.dp)
                            )
                            Text("حذف الملاحظة", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                        }
                    },
                    onClick = {
                        showContextMenu = false
                        onDeleteClick()
                    }
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Title and Star/Restore icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = note.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (note.isInTrash) {
                    Icon(
                        imageVector = Icons.Default.RestoreFromTrash,
                        contentDescription = "استعادة الملاحظة",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onRestoreClick?.invoke() }
                    )
                } else {
                    Icon(
                        imageVector = if (note.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "تفضيل الملاحظة",
                        tint = if (note.isFavorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onFavoriteToggle() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Short Content
            Text(
                text = note.content,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                maxLines = 4,
                lineHeight = 18.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Category tag (if not "All" / "عام")
            if (note.category != "الكل" && note.category != "عام") {
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${note.category}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Formatting Date & Time matching "June. 7:50 PM 12" style in photo
            Text(
                text = formatDate(note.timestamp),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

// --- Empty State View ---
@Composable
fun EmptyStateView(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Lightbulb,
            contentDescription = null,
            tint = BluePrimary.copy(alpha = 0.6f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// --- Note Editor Screen ---
@Composable
fun NoteEditorScreen(viewModel: NoteViewModel) {
    val editorTitle = viewModel.editorTitle
    val editorContent = viewModel.editorContent
    val editorColorHex = viewModel.editorColorHex
    val editorCategory = viewModel.editorCategory
    val editorIsFavorite = viewModel.editorIsFavorite
    val aiSummary = viewModel.aiSummary
    val aiKeywords = viewModel.aiKeywords

    var isChatOpen by remember { mutableStateOf(false) }

    // Fluid premium entry transitions (Framer Motion inspired)
    val contentTransition = remember { Animatable(0f) }
    val toolbarTransition = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        contentTransition.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(Unit) {
        delay(120) // staggered entry for that extra touch of polish
        toolbarTransition.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Animation for color change background
    val animatedBgColor by animateColorAsState(
        targetValue = Color(android.graphics.Color.parseColor(editorColorHex)).copy(alpha = 0.45f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "editorBgColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(animatedBgColor)
                .statusBarsPadding()
        ) {
            // Editor Custom Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.saveNote() },
                        modifier = Modifier.testTag("save_and_exit_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "حفظ ورجوع",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = if (viewModel.currentEditingNote == null) "ملاحظة جديدة" else "تعديل الملاحظة",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Category dropdown / selector
                    CategorySelectorDropdown(
                        selectedCategory = editorCategory,
                        onCategorySelected = { viewModel.editorCategory = it }
                    )

                    // Note Color palette theme selector
                    var colorMenuExpanded by remember { mutableStateOf(false) }
                    val colors = listOf(
                        "#143A24", // Emerald Green
                        "#3C102C", // Burgundy
                        "#112E51", // Sapphire Blue
                        "#38310E", // Ochre Yellow
                        "#241738", // Deep Purple
                        "#1F2937"  // Carbon Gray
                    )
                    Box {
                        IconButton(onClick = { colorMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "تغيير خلفية الملاحظة",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = colorMenuExpanded,
                            onDismissRequest = { colorMenuExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                colors.forEach { hex ->
                                    val isSelected = hex.lowercase() == editorColorHex.lowercase()
                                    val color = Color(android.graphics.Color.parseColor(hex))
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                viewModel.changeEditorColor(hex)
                                                colorMenuExpanded = false
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Favorite star
                    IconButton(onClick = { viewModel.editorIsFavorite = !editorIsFavorite }) {
                        Icon(
                            imageVector = if (editorIsFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "تمييز كالمفضلة",
                            tint = if (editorIsFavorite) Color(0xFFFFD700) else Color.White
                        )
                    }

                    // Delete Note (Trash icon) - only if existing note
                    if (viewModel.currentEditingNote != null) {
                        IconButton(onClick = { viewModel.deleteNote(viewModel.currentEditingNote!!) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف الملاحظة",
                                tint = Color(0xFFEF5350) // Beautiful Material Red
                            )
                        }
                    }

                    // Save Checkmark
                    IconButton(onClick = { viewModel.saveNote() }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "حفظ الملاحظة",
                            tint = BluePrimary
                        )
                    }
                }
            }

            // Scrollable Editor Contents
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        alpha = contentTransition.value
                        translationY = (1f - contentTransition.value) * 60f // Smooth slide-up animation
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Input
                item {
                    TextField(
                        value = editorTitle,
                        onValueChange = { viewModel.editorTitle = it },
                        placeholder = { Text("عنوان الملاحظة...", fontSize = 22.sp, color = Color.White.copy(alpha = 0.4f)) },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_title_input")
                    )
                }

                // AI Tags / Keywords display if available
                if (!aiKeywords.isNullOrBlank()) {
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            aiKeywords.split(",").forEach { tag ->
                                val cleanedTag = tag.trim()
                                if (cleanedTag.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "#$cleanedTag",
                                            color = AccentTeal,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Content Input
                item {
                    TextField(
                        value = editorContent,
                        onValueChange = { viewModel.editorContent = it },
                        placeholder = { Text("ابدأ الكتابة هنا...", fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f)) },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = viewModel.editorFontSize.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = (viewModel.editorFontSize + 8).sp
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        visualTransformation = remember { MarkdownVisualTransformation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                            .testTag("note_content_input")
                    )
                }

                // Render Summary card if available
                if (!aiSummary.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = AccentPurple,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "ملخص الذكاء الاصطناعي",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.aiSummary = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إغلاق التلخيص",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = aiSummary,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }

            // Glassmorphic Rich Text Toolbar (Samsung Notes style)
            GlassmorphicTextToolbar(
                editorContent = editorContent,
                onContentChange = { viewModel.editorContent = it },
                onChatToggle = { isChatOpen = !isChatOpen },
                modifier = Modifier.graphicsLayer {
                    alpha = toolbarTransition.value
                    translationY = (1f - toolbarTransition.value) * 80f // Staggered spring slide up
                }
            )
        }

        // --- Chat Assistant Bottom Panel / Collapsible Layer ---
        AnimatedVisibility(
            visible = isChatOpen,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            ChatAssistantPanel(
                viewModel = viewModel,
                onClose = { isChatOpen = false }
            )
        }
    }
}

// --- Category Selector Dropdown ---
@Composable
fun CategorySelectorDropdown(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = listOf("عام", "عمل", "شخصي", "أفكار", "مراجعة")

    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Default.Label, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = selectedCategory, color = Color.White, fontWeight = FontWeight.Bold)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkSurface)
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(text = category, color = Color.White) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

// --- AI Chat Assistant Collapsible Drawer ---
@Composable
fun ChatAssistantPanel(
    viewModel: NoteViewModel,
    onClose: () -> Unit
) {
    val messages by viewModel.chatMessages
    val chatInputText = viewModel.chatInputText
    val isSending = viewModel.isChatSending

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.65f) // Take 65% height
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(DarkSurface)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(16.dp)
    ) {
        // Chat Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "مساعد Mando AI الذكي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = "اسأل الذكاء الاصطناعي حول محتوى ملاحظتك الحالية", color = TextSecondary, fontSize = 11.sp)
                }
            }

            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "تصغير المحادثة", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Divider(color = Color.White.copy(alpha = 0.08f))

        // Chat History Scroll List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ابدأ النقاش مع Mando AI حول هذه الملاحظة!",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "أمثلة: \"اقترح أفكاراً إضافية\" أو \"لخص النقاط الأساسية\"",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                items(messages) { message ->
                    ChatMessageItem(message = message)
                }
                
                if (isSending) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = BluePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "جاري كتابة الرد من Mando...", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Chat input textfield and send button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = chatInputText,
                onValueChange = { viewModel.chatInputText = it },
                placeholder = { Text("اطرح سؤالك هنا...", color = TextSecondary, fontSize = 14.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.sendChatMessage() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input"),
                maxLines = 3
            )

            IconButton(
                onClick = { viewModel.sendChatMessage() },
                enabled = !isSending && chatInputText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "إرسال",
                    tint = if (chatInputText.isNotBlank()) BluePrimary else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- Chat Message Bubble ---
@Composable
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isUser) BluePrimary else Color.White.copy(alpha = 0.08f)
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(bgColor)
                .border(1.dp, Color.White.copy(alpha = 0.05f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

// --- Custom Flow Layout for tag display ---
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val layoutWidth = constraints.maxWidth

        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeables.forEach { placeable ->
            if (currentRowWidth + placeable.width > layoutWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + 12 // spacing
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        var totalHeight = 0
        rows.forEachIndexed { index, row ->
            val rowHeight = row.maxOf { it.height }
            totalHeight += rowHeight + if (index < rows.size - 1) 12 else 0
        }

        layout(layoutWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + 12
                }
                y += rowHeight + 12
            }
        }
    }
}

// --- Glass Bottom Navigation Bar ---
@Composable
fun GlassBottomNavigationBar(
    selectedTab: AppScreen,
    onTabSelected: (AppScreen) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars) // Safe navigation gesture bar padding!
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(50)) // Capsule shape matching the image!
            .background(DarkSurface) // Solid dark charcoal background!
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(50)
            )
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomTabItem(
            icon = Icons.Default.Description,
            label = "الملاحظات",
            isSelected = selectedTab == AppScreen.HOME,
            onClick = { onTabSelected(AppScreen.HOME) },
            tag = "notes_tab"
        )

        BottomTabItem(
            icon = Icons.Default.Star,
            label = "المفضلة",
            isSelected = selectedTab == AppScreen.FAVORITES,
            onClick = { onTabSelected(AppScreen.FAVORITES) },
            tag = "favorites_tab"
        )

        BottomTabItem(
            icon = Icons.Default.Settings,
            label = "الإعدادات",
            isSelected = selectedTab == AppScreen.SETTINGS,
            onClick = { onTabSelected(AppScreen.SETTINGS) },
            tag = "settings_tab"
        )
    }
}

@Composable
fun BottomTabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val scale by animateFloatAsState(if (isSelected) 1.05f else 1f, label = "tabScale")
    
    val containerColor = if (isSelected) Color(0xFF383A3D) else Color.Transparent
    val contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f)

    Column(
        modifier = Modifier
            .testTag(tag)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(50)) // Fully rounded pill shape for each tab item!
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// --- Premium Glassmorphic Text Formatting Toolbar (Samsung Notes style) ---
@Composable
fun GlassmorphicTextToolbar(
    editorContent: String,
    onContentChange: (String) -> Unit,
    onChatToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Formatting & active states for UI highlight (neon glow)
    var isMicActive by remember { mutableStateOf(false) }
    var selectedAlignment by remember { mutableStateOf("Right") } // "Left", "Center", "Right" (Default RTL)
    var selectedListType by remember { mutableStateOf("None") } // "None", "Numbered", "Bullet"
    var selectedTypography by remember { mutableStateOf("None") } // "None", "H1", "H2"
    var isBoldActive by remember { mutableStateOf(false) }
    var isItalicActive by remember { mutableStateOf(false) }
    var isUnderlineActive by remember { mutableStateOf(false) }
    var isStrikethroughActive by remember { mutableStateOf(false) }

    // Glass container styled based on prompt requirements
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x991A1C1E)) // rgba(26, 28, 30, 0.6) - high transparency
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f), // elegant borders matching reflective real glass
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mando Assistant button at the start of the bar
            Button(
                onClick = onChatToggle,
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .wrapContentWidth()
                    .height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "مساعد Mando",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Vertical divider separating Chat Toggle and formatting scrollable tools
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Scrollable text formatting tools (Samsung Notes horizontal scrolling bar)
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            // 1. Microphone (Voice-to-text / Transcription Simulation)
            item {
                ToolbarIconButton(
                    icon = Icons.Default.Mic,
                    contentDescription = "تسجيل الصوت",
                    isActive = isMicActive,
                    activeGlowColor = AccentTeal,
                    onClick = {
                        isMicActive = !isMicActive
                        if (isMicActive) {
                            val voiceSim = if (editorContent.isNotBlank()) "\n" else ""
                            onContentChange(editorContent + voiceSim + "🎤 [نص مسجل بالصوت: \"أفكار وخطط ذكية لتطوير التطبيق الجديد بمظهر زجاجي شفاف\"]")
                        }
                    }
                )
            }



            // 3. Dash / Line (Inserts horizontal divider)
            item {
                ToolbarIconButton(
                    icon = Icons.Default.HorizontalRule,
                    contentDescription = "إدراج خط فاصل",
                    isActive = false,
                    onClick = {
                        val space = if (editorContent.isNotBlank() && !editorContent.endsWith("\n")) "\n" else ""
                        onContentChange(editorContent + space + "━━━━━━━━━━━━━━━━━━━━\n")
                    }
                )
            }

            // Divider
            item {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }

            // 4. Align Center
            item {
                ToolbarIconButton(
                    icon = Icons.Default.FormatAlignCenter,
                    contentDescription = "محاذاة للوسط",
                    isActive = selectedAlignment == "Center",
                    onClick = {
                        selectedAlignment = if (selectedAlignment == "Center") "None" else "Center"
                    }
                )
            }

            // 5. Align Right (Active by default for Arabic)
            item {
                ToolbarIconButton(
                    icon = Icons.Default.FormatAlignRight,
                    contentDescription = "محاذاة لليمين",
                    isActive = selectedAlignment == "Right",
                    onClick = {
                        selectedAlignment = if (selectedAlignment == "Right") "None" else "Right"
                    }
                )
            }

            // 6. Align Left
            item {
                ToolbarIconButton(
                    icon = Icons.Default.FormatAlignLeft,
                    contentDescription = "محاذاة لليسار",
                    isActive = selectedAlignment == "Left",
                    onClick = {
                        selectedAlignment = if (selectedAlignment == "Left") "None" else "Left"
                    }
                )
            }

            // Divider
            item {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }

            // 7. Numbered List
            item {
                ToolbarIconButton(
                    icon = Icons.Default.FormatListNumbered,
                    contentDescription = "قائمة مرقمة",
                    isActive = selectedListType == "Numbered",
                    onClick = {
                        selectedListType = if (selectedListType == "Numbered") "None" else "Numbered"
                        if (selectedListType == "Numbered") {
                            val prefix = if (editorContent.isNotBlank() && !editorContent.endsWith("\n")) "\n" else ""
                            onContentChange(editorContent + prefix + "1. ")
                        }
                    }
                )
            }

            // 8. Bullet List
            item {
                ToolbarIconButton(
                    icon = Icons.Default.FormatListBulleted,
                    contentDescription = "قائمة منقطة",
                    isActive = selectedListType == "Bullet",
                    onClick = {
                        selectedListType = if (selectedListType == "Bullet") "None" else "Bullet"
                        if (selectedListType == "Bullet") {
                            val prefix = if (editorContent.isNotBlank() && !editorContent.endsWith("\n")) "\n" else ""
                            onContentChange(editorContent + prefix + "• ")
                        }
                    }
                )
            }

            // Divider
            item {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }

            // 9 & 10. Typography sizes (H2 & H1)
            item {
                ToolbarTextBadge(
                    text = "H1",
                    contentDescription = "عنوان رئيسي كبير",
                    isActive = selectedTypography == "H1",
                    onClick = {
                        selectedTypography = if (selectedTypography == "H1") "None" else "H1"
                        if (selectedTypography == "H1") {
                            val prefix = if (editorContent.isNotBlank() && !editorContent.endsWith("\n")) "\n" else ""
                            onContentChange(editorContent + prefix + "# عنوان رئيسي: ")
                        }
                    }
                )
            }

            item {
                ToolbarTextBadge(
                    text = "H2",
                    contentDescription = "عنوان فرعي متوسط",
                    isActive = selectedTypography == "H2",
                    onClick = {
                        selectedTypography = if (selectedTypography == "H2") "None" else "H2"
                        if (selectedTypography == "H2") {
                            val prefix = if (editorContent.isNotBlank() && !editorContent.endsWith("\n")) "\n" else ""
                            onContentChange(editorContent + prefix + "## عنوان فرعي: ")
                        }
                    }
                )
            }

            // Divider
            item {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }

            // 11. Strikethrough (S)
            item {
                ToolbarIconButton(
                    icon = Icons.Default.FormatStrikethrough,
                    contentDescription = "شطب النص",
                    isActive = isStrikethroughActive,
                    onClick = {
                        isStrikethroughActive = !isStrikethroughActive
                        onContentChange(editorContent + " ~~نص مشطوب~~")
                    }
                )
            }

            // 12. Underline (U)
            item {
                ToolbarIconButton(
                    icon = Icons.Default.FormatUnderlined,
                    contentDescription = "خط تحت النص",
                    isActive = isUnderlineActive,
                    onClick = {
                        isUnderlineActive = !isUnderlineActive
                        onContentChange(editorContent + " <u>نص مسطر</u>")
                    }
                )
            }

            // 13. Italic (I)
            item {
                ToolbarIconButton(
                    icon = Icons.Default.FormatItalic,
                    contentDescription = "خط مائل",
                    isActive = isItalicActive,
                    onClick = {
                        isItalicActive = !isItalicActive
                        onContentChange(editorContent + " *نص مائل*")
                    }
                )
            }

            // 14. Bold (B)
            item {
                ToolbarIconButton(
                    icon = Icons.Default.FormatBold,
                    contentDescription = "خط عريض مميز",
                    isActive = isBoldActive,
                    onClick = {
                        isBoldActive = !isBoldActive
                        onContentChange(editorContent + " **نص عريض**")
                    }
                )
            }
        }
    }
}
}

// --- Helper Composable for Toolbar Icon Button with Neon Glow State ---
@Composable
fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeGlowColor: Color = BluePrimary
) {
    val alphaAnim by animateFloatAsState(if (isActive) 1f else 0.6f, label = "iconAlpha")
    val scaleAnim by animateFloatAsState(if (isActive) 1.1f else 1f, label = "iconScale")

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) activeGlowColor.copy(alpha = 0.25f) else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isActive) activeGlowColor.copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) activeGlowColor else Color.White,
            modifier = Modifier
                .size(20.dp)
                .drawBehind {
                    if (isActive) {
                        // Soft elegant neon glow effect matching reflective glass
                        drawCircle(
                            color = activeGlowColor.copy(alpha = 0.3f),
                            radius = size.width * 0.7f
                        )
                    }
                }
        )
    }
}

// --- Helper Composable for Typography text badge with Glass Glow State ---
@Composable
fun ToolbarTextBadge(
    text: String,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGlowColor = AccentPink

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) activeGlowColor.copy(alpha = 0.25f) else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isActive) activeGlowColor.copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) activeGlowColor else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.drawBehind {
                if (isActive) {
                    drawCircle(
                        color = activeGlowColor.copy(alpha = 0.3f),
                        radius = size.width * 0.7f
                    )
                }
            }
        )
    }
}

// --- Custom real-time Markdown / formatting parser for rich visual effects in the editor ---
class MarkdownVisualTransformation(
    val boldColor: Color = Color(0xFF64B5F6), // light blue
    val italicColor: Color = Color(0xFFFFB74D), // light orange
    val underlineColor: Color = Color(0xFF81C784), // light green
    val strikethroughColor: Color = Color(0xFFE57373), // light red
    val headingColor: Color = Color(0xFF4DD0E1), // teal
    val listColor: Color = Color(0xFFBA68C8) // purple
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val annotatedString = buildAnnotatedString {
            val raw = text.text
            var i = 0
            while (i < raw.length) {
                // Check bold **...**
                if (raw.startsWith("**", i)) {
                    val end = raw.indexOf("**", i + 2)
                    if (end != -1) {
                        append("**")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                            append(raw.substring(i + 2, end))
                        }
                        append("**")
                        i = end + 2
                        continue
                    }
                }
                // Check italic *...*
                if (raw.startsWith("*", i) && !raw.startsWith("**", i)) {
                    val end = raw.indexOf("*", i + 1)
                    if (end != -1) {
                        append("*")
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic, color = italicColor)) {
                            append(raw.substring(i + 1, end))
                        }
                        append("*")
                        i = end + 1
                        continue
                    }
                }
                // Check underline <u>...</u>
                if (raw.startsWith("<u>", i)) {
                    val end = raw.indexOf("</u>", i + 3)
                    if (end != -1) {
                        append("<u>")
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline, color = underlineColor)) {
                            append(raw.substring(i + 3, end))
                        }
                        append("</u>")
                        i = end + 4
                        continue
                    }
                }
                // Check strikethrough ~~...~~
                if (raw.startsWith("~~", i)) {
                    val end = raw.indexOf("~~", i + 2)
                    if (end != -1) {
                        append("~~")
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough, color = strikethroughColor)) {
                            append(raw.substring(i + 2, end))
                        }
                        append("~~")
                        i = end + 2
                        continue
                    }
                }
                
                // Line-by-line formatting if i is start of line
                if (i == 0 || raw[i - 1] == '\n') {
                    // Headings: # 
                    if (raw.startsWith("## ", i)) {
                        val endOfLine = raw.indexOf('\n', i)
                        val end = if (endOfLine == -1) raw.length else endOfLine
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = headingColor)) {
                            append(raw.substring(i, end))
                        }
                        i = end
                        continue
                    } else if (raw.startsWith("# ", i)) {
                        val endOfLine = raw.indexOf('\n', i)
                        val end = if (endOfLine == -1) raw.length else endOfLine
                        withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = headingColor)) {
                            append(raw.substring(i, end))
                        }
                        i = end
                        continue
                    }
                    
                    // Bullet list item •
                    if (raw.startsWith("• ", i)) {
                        val endOfLine = raw.indexOf('\n', i)
                        val end = if (endOfLine == -1) raw.length else endOfLine
                        withStyle(style = SpanStyle(color = listColor, fontWeight = FontWeight.Bold)) {
                            append("• ")
                        }
                        append(raw.substring(i + 2, end))
                        i = end
                        continue
                    }
                    
                    // Numbered list item like "1. ", "2. ", "3. "
                    val numMatch = Regex("^([0-9]+\\.\\s)").find(raw.substring(i))
                    if (numMatch != null) {
                        val prefix = numMatch.value
                        val endOfLine = raw.indexOf('\n', i)
                        val end = if (endOfLine == -1) raw.length else endOfLine
                        withStyle(style = SpanStyle(color = listColor, fontWeight = FontWeight.Bold)) {
                            append(prefix)
                        }
                        append(raw.substring(i + prefix.length, end))
                        i = end
                        continue
                    }
                    
                    // Divider row ━━━━━━━━━━━━━━━━━━━━
                    if (raw.startsWith("━━━━━━━━━━━━━━", i)) {
                        val endOfLine = raw.indexOf('\n', i)
                        val end = if (endOfLine == -1) raw.length else endOfLine
                        withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)) {
                            append(raw.substring(i, end))
                        }
                        i = end
                        continue
                    }
                }
                
                // Audio highlight 🎤 [...]
                if (raw.startsWith("🎤 [", i)) {
                    val end = raw.indexOf("]", i + 3)
                    if (end != -1) {
                        withStyle(style = SpanStyle(color = Color(0xFF26A69A), fontWeight = FontWeight.SemiBold)) {
                            append(raw.substring(i, end + 1))
                        }
                        i = end + 1
                        continue
                    }
                }

                // Default char
                append(raw[i])
                i++
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}

// --- Navigation Drawer Content ---
@Composable
fun NotesDrawerContent(
    notesCount: Int,
    favoritesCount: Int,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onSelectScreen: (AppScreen) -> Unit,
    onDeleteAllNotes: () -> Unit,
    onClose: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "حذف جميع الملاحظات",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف كافة الملاحظات نهائياً؟ لا يمكن التراجع عن هذا الإجراء.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllNotes()
                        showConfirmDialog = false
                        onClose()
                    }
                ) {
                    Text("حذف الكل", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("إلغاء", color = Color.White)
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
        )
    }

    ModalDrawerSheet(
        drawerContainerColor = DarkSurface,
        drawerContentColor = Color.White,
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                ),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header with App logo and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BluePrimary.copy(alpha = 0.2f))
                        .border(1.dp, BluePrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "مساعد Mando AI",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "ملاحظات ذكية وعصرية",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 16.dp))

            // Section: Statistics / الإحصائيات
            Text(
                text = "الإحصائيات السريعة",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total notes card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = notesCount.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                        Text(text = "ملاحظة", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                // Favorite notes card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = favoritesCount.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        Text(text = "مفضلة", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 16.dp))

            // Section: Quick Filter / الفئات
            Text(
                text = "تصفية حسب الفئة",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val categories = listOf("الكل", "شخصي", "عمل", "مراجعة")
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                NavigationDrawerItem(
                    label = { Text(text = cat, fontSize = 14.sp) },
                    selected = isSelected,
                    onClick = {
                        onCategorySelected(cat)
                        onSelectScreen(AppScreen.HOME)
                        onClose()
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = BluePrimary.copy(alpha = 0.15f),
                        unselectedContainerColor = Color.Transparent,
                        selectedTextColor = BluePrimary,
                        unselectedTextColor = Color.White,
                        selectedIconColor = BluePrimary,
                        unselectedIconColor = Color.White.copy(alpha = 0.6f)
                    ),
                    icon = {
                        val icon = when (cat) {
                            "الكل" -> Icons.Default.Notes
                            "شخصي" -> Icons.Default.Person
                            "عمل" -> Icons.Default.Work
                            else -> Icons.Default.Assignment
                        }
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.height(44.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // سلة المحذوفات Navigation Item
            NavigationDrawerItem(
                label = { Text(text = "سلة المحذوفات", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                selected = false,
                onClick = {
                    onSelectScreen(AppScreen.TRASH)
                    onClose()
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = Color(0xFFEF5350).copy(alpha = 0.15f),
                    unselectedContainerColor = Color(0xFFEF5350).copy(alpha = 0.05f),
                    selectedTextColor = Color(0xFFEF5350),
                    unselectedTextColor = Color(0xFFEF5350),
                    selectedIconColor = Color(0xFFEF5350),
                    unselectedIconColor = Color(0xFFEF5350)
                ),
                icon = {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "سلة المحذوفات", modifier = Modifier.size(18.dp))
                },
                modifier = Modifier
                    .height(44.dp)
                    .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.15f), RoundedCornerShape(50))
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 16.dp))

            // Delete All Button
            NavigationDrawerItem(
                label = { Text(text = "حذف كافة الملاحظات", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                selected = false,
                onClick = { showConfirmDialog = true },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color(0xFFEF5350).copy(alpha = 0.08f),
                    unselectedTextColor = Color(0xFFEF5350),
                    unselectedIconColor = Color(0xFFEF5350)
                ),
                icon = {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف الكل", modifier = Modifier.size(18.dp))
                },
                modifier = Modifier
                    .height(44.dp)
                    .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.2f), RoundedCornerShape(50))
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 16.dp))

            // Footer Developer Info
            Text(
                text = "الإصدار v1.1.0",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mando AI Notes",
                fontSize = 11.sp,
                color = BluePrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

// --- Trash Screen ---
@Composable
fun TrashScreen(viewModel: NoteViewModel) {
    val trashNotes by viewModel.trashNotes.collectAsState()
    var showEmptyConfirm by remember { mutableStateOf(false) }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = {
                Text(
                    text = "إفراغ سلة المحذوفات",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في إفراغ سلة المحذوفات وحذف كافة الملاحظات نهائياً؟ لا يمكن استعادة هذه الملاحظات لاحقاً.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyConfirm = false
                    }
                ) {
                    Text("إفراغ الكل", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("إلغاء", color = Color.White)
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Trash Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back to home button
            IconButton(
                onClick = { viewModel.selectScreen(AppScreen.HOME) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "العودة للرئيسية",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "سلة المحذوفات",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .testTag("trash_header")
            )

            if (trashNotes.isNotEmpty()) {
                Button(
                    onClick = { showEmptyConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "إفراغ السلة",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إفراغ السلة", color = Color(0xFFEF5350), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (trashNotes.isEmpty()) {
            EmptyStateView(
                title = "سلة المحذوفات فارغة",
                subtitle = "الملاحظات التي تقوم بحذفها من القائمة الرئيسية ستظهر هنا مؤقتاً لحمايتها، حيث يمكنك استعادتها أو حذفها نهائياً."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trashNotes) { note ->
                    NoteCard(
                        note = note,
                        onNoteClick = { /* Do nothing in trash */ },
                        onFavoriteToggle = { /* Do nothing in trash */ },
                        onDeleteClick = { viewModel.deleteNote(note) },
                        onRestoreClick = { viewModel.restoreFromTrash(note) },
                        formatDate = { viewModel.formatNoteTimestamp(it) }
                    )
                }
            }
        }
    }
}

