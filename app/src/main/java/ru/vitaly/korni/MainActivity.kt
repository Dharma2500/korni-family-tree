package ru.vitaly.korni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.min

private val Paper = Color(0xFFF7F6F0)
private val Ink = Color(0xFF30372D)
private val Leaf = Color(0xFF526A43)
private val Muted = Color(0xFF73796D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.lightColorScheme(
                    primary = Leaf,
                    onPrimary = Color.White,
                    secondary = Color(0xFF9A7B68),
                    background = Paper,
                    surface = Color(0xFFFFFEFB),
                    onSurface = Ink
                )
            ) {
                KorniApp()
            }
        }
    }
}

private enum class AppPage { HOME, TREE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KorniApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var family by remember { mutableStateOf(FamilyStorage.load(context)) }
    var page by remember { mutableStateOf(AppPage.HOME) }
    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var focusPersonId by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }
    var relativeTo by remember { mutableStateOf<Person?>(null) }
    var draftPhotoUri by remember { mutableStateOf("") }
    var fitRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(family) {
        withContext(Dispatchers.IO) { FamilyStorage.save(context, family) }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val snapshot = family
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { PdfExporter.exportA4(context, uri, snapshot) }
                }
                snackbar.showSnackbar(
                    if (result.isSuccess) "PDF сохранён. Листы формата А4 можно распечатать и соединить."
                    else "Не удалось создать PDF: " + (result.exceptionOrNull()?.message ?: "ошибка")
                )
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(FamilyStorage.toJson(family))
                } ?: error("Не удалось открыть файл")
            }
            scope.launch {
                snackbar.showSnackbar(if (result.isSuccess) "Резервная копия сохранена." else "Ошибка создания копии.")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Не удалось прочитать файл")
                        FamilyStorage.fromJson(raw)
                    }
                }
                result.onSuccess {
                    family = it
                    selectedPersonId = null
                    snackbar.showSnackbar("Резервная копия восстановлена.")
                }.onFailure {
                    snackbar.showSnackbar("Не удалось прочитать копию. Проверь файл.")
                }
            }
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            draftPhotoUri = uri.toString()
        }
    }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Корни", fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            if (page == AppPage.HOME) "История вашей семьи" else "Фамильное древо",
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                },
                navigationIcon = {
                    if (page == AppPage.TREE) {
                        TextButton(onClick = { page = AppPage.HOME; showDetails = false }) {
                            Text("‹ Назад", color = Leaf, fontSize = 16.sp)
                        }
                    } else {
                        Spacer(Modifier.size(12.dp))
                    }
                },
                actions = {
                    if (page == AppPage.TREE) {
                        TextButton(onClick = { showSearch = true }) {
                            Text("Поиск", color = Leaf)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper)
            )
        }
    ) { innerPadding ->
        if (page == AppPage.HOME) {
            HomeScreen(
                family = family,
                modifier = Modifier.padding(innerPadding),
                onOpenTree = { page = AppPage.TREE },
                onAddPerson = {
                    editingPerson = null
                    relativeTo = null
                    draftPhotoUri = ""
                    showEditor = true
                },
                onSearch = { showSearch = true },
                onExport = {
                    if (family.people.isEmpty()) scope.launch { snackbar.showSnackbar("Сначала добавь хотя бы одного человека.") }
                    else pdfLauncher.launch("Корни-фамильное-древо.pdf")
                },
                onBackup = { backupLauncher.launch("Корни-резервная-копия.json") },
                onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/*")) }
            )
        } else {
            TreeScreen(
                family = family,
                selectedPersonId = selectedPersonId,
                fitRequest = fitRequest,
                focusPersonId = focusPersonId,
                modifier = Modifier.padding(innerPadding),
                onAddPerson = {
                    editingPerson = null
                    relativeTo = selectedPersonId?.let { id -> family.people.firstOrNull { it.id == id } }
                    draftPhotoUri = ""
                    showEditor = true
                },
                onSelectPerson = { id ->
                    selectedPersonId = id
                    showDetails = true
                },
                onFit = { fitRequest++ }
            )
        }
    }

    if (showEditor) {
        PersonEditorDialog(
            existing = editingPerson,
            anchor = relativeTo,
            photoUri = draftPhotoUri,
            onPickPhoto = { photoLauncher.launch(arrayOf("image/*")) },
            onDismiss = { showEditor = false },
            onSave = { person, relation ->
                val old = editingPerson
                if (old != null) {
                    family = family.copy(people = family.people.map { if (it.id == old.id) person else it })
                } else {
                    val newLinks = if (relativeTo != null && relation != null) {
                        val anchor = relativeTo!!
                        val link = when (relation) {
                            RelativeChoice.SPOUSE -> FamilyLink(fromId = anchor.id, toId = person.id, type = LinkType.SPOUSE)
                            RelativeChoice.PARENT -> FamilyLink(fromId = person.id, toId = anchor.id, type = LinkType.PARENT)
                            RelativeChoice.CHILD -> FamilyLink(fromId = anchor.id, toId = person.id, type = LinkType.PARENT)
                            RelativeChoice.SIBLING -> FamilyLink(fromId = anchor.id, toId = person.id, type = LinkType.SIBLING)
                        }
                        listOf(link)
                    } else emptyList()
                    family = family.copy(
                        people = family.people + person,
                        links = family.links + newLinks
                    )
                    selectedPersonId = person.id
                }
                showEditor = false
                page = AppPage.TREE
            }
        )
    }

    val selectedPerson = family.people.firstOrNull { it.id == selectedPersonId }
    if (showDetails && selectedPerson != null) {
        PersonDetailsDialog(
            person = selectedPerson,
            family = family,
            onDismiss = { showDetails = false },
            onAddRelative = {
                relativeTo = selectedPerson
                editingPerson = null
                draftPhotoUri = ""
                showDetails = false
                showEditor = true
            },
            onEdit = {
                editingPerson = selectedPerson
                relativeTo = null
                draftPhotoUri = selectedPerson.photoUri
                showDetails = false
                showEditor = true
            },
            onDelete = { showDeleteConfirm = true }
        )
    }

    if (showDeleteConfirm && selectedPerson != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить родственника?") },
            text = { Text("Карточка «" + selectedPerson.fullName() + "» и её связи будут удалены. Остальные родственники останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    family = family.copy(
                        people = family.people.filterNot { it.id == selectedPerson.id },
                        links = family.links.filterNot { it.fromId == selectedPerson.id || it.toId == selectedPerson.id }
                    )
                    selectedPersonId = null
                    showDeleteConfirm = false
                    showDetails = false
                }) { Text("Удалить", color = Color(0xFFB34343)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            }
        )
    }

    if (showSearch) {
        SearchDialog(
            people = family.people,
            onDismiss = { showSearch = false },
            onSelect = { person ->
                selectedPersonId = person.id
                focusPersonId = person.id
                page = AppPage.TREE
                showSearch = false
                showDetails = true
            }
        )
    }
}

@Composable
private fun HomeScreen(
    family: FamilyData,
    modifier: Modifier = Modifier,
    onOpenTree: () -> Unit,
    onAddPerson: () -> Unit,
    onSearch: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFE4)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Семейная история начинается здесь", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    "Добавляй родных, соединяй поколения и сохраняй память о близких.",
                    fontSize = 15.sp,
                    color = Muted,
                    lineHeight = 21.sp
                )
                Text(
                    "В дереве сейчас: " + family.people.size + " " + personWord(family.people.size),
                    color = Leaf,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        BigAction("Открыть фамильное древо", "Посмотреть родственников и связи", onOpenTree, primary = true)
        BigAction("Добавить родственника", "Записать имя, даты и родственные связи", onAddPerson)
        BigAction("Найти человека", "Быстро перейти к нужной карточке", onSearch)

        Divider(color = Color(0xFFE4E2D9))
        Text("Сохранение и печать", color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        BigAction("Сохранить в PDF", "Подготовить дерево для печати на листах А4", onExport)
        BigAction("Создать резервную копию", "Сохранить все записи в отдельный файл", onBackup)
        BigAction("Восстановить из копии", "Вернуть ранее сохранённое семейное древо", onRestore)

        Text(
            "Личные данные остаются на этом устройстве. Приложение не отправляет семейные сведения в интернет.",
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
        )
    }
}

@Composable
private fun BigAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    val background = if (primary) Leaf else Color(0xFFFFFEFB)
    val titleColor = if (primary) Color.White else Ink
    val subtitleColor = if (primary) Color(0xFFE7EBDD) else Muted
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = background),
        border = if (primary) null else BorderStroke(1.dp, Color(0xFFE5E3DA)),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            Text(subtitle, fontSize = 13.sp, color = subtitleColor)
        }
    }
}

@Composable
private fun TreeScreen(
    family: FamilyData,
    selectedPersonId: String?,
    fitRequest: Int,
    focusPersonId: String?,
    modifier: Modifier = Modifier,
    onAddPerson: () -> Unit,
    onSelectPerson: (String) -> Unit,
    onFit: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddPerson,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (selectedPersonId == null) "Добавить человека" else "Добавить родственника", textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = onFit,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Всё древо")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (family.people.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🌳", fontSize = 48.sp)
                    Text("Здесь пока пусто", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Добавь первого человека, а затем его родных.", color = Muted, textAlign = TextAlign.Center)
                    Button(onClick = onAddPerson, colors = ButtonDefaults.buttonColors(containerColor = Leaf)) {
                        Text("Добавить первого человека")
                    }
                }
            }
        } else {
            FamilyTreeCanvas(
                family = family,
                selectedPersonId = selectedPersonId,
                fitRequest = fitRequest,
                focusPersonId = focusPersonId,
                onSelectPerson = onSelectPerson,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            Text(
                "Увеличивай двумя пальцами. Перемещай древо одним пальцем. Нажми на карточку, чтобы открыть её.",
                fontSize = 12.sp,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun FamilyTreeCanvas(
    family: FamilyData,
    selectedPersonId: String?,
    fitRequest: Int,
    focusPersonId: String?,
    onSelectPerson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodes = remember(family) { TreeLayout.build(family) }
    val density = LocalDensity.current.density
    var scale by remember { mutableFloatStateOf(0.65f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(fitRequest, viewport, family, focusPersonId) {
        if (viewport.width > 0 && viewport.height > 0 && nodes.isNotEmpty()) {
            val focused = focusPersonId?.let { id -> nodes.firstOrNull { it.person.id == id } }
            if (focused != null) {
                val newScale = scale.coerceIn(0.55f, 1.25f)
                scale = newScale
                val centerX = (focused.x + TreeLayout.CARD_WIDTH / 2f) * density * newScale
                val centerY = (focused.y + TreeLayout.CARD_HEIGHT / 2f) * density * newScale
                offset = Offset(viewport.width / 2f - centerX, viewport.height / 2f - centerY)
            } else {
                val treeWidth = TreeLayout.width(nodes) * density
                val treeHeight = TreeLayout.height(nodes) * density
                val fitX = (viewport.width - 36f) / treeWidth
                val fitY = (viewport.height - 36f) / treeHeight
                val newScale = min(fitX, fitY).coerceIn(0.12f, 1.35f)
                scale = newScale
                offset = Offset(
                    (viewport.width - treeWidth * newScale) / 2f,
                    (viewport.height - treeHeight * newScale) / 2f
                )
            }
        }
    }

    Box(modifier.background(Color(0xFFFFFEFB), RoundedCornerShape(18.dp))) {
        Canvas(
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.12f, 3.2f)
                        val factor = newScale / scale
                        offset = centroid - (centroid - offset) * factor + pan
                        scale = newScale
                    }
                }
                .pointerInput(nodes, scale, offset) {
                    detectTapGestures { tap ->
                        val x = (tap.x - offset.x) / (scale * density)
                        val y = (tap.y - offset.y) / (scale * density)
                        val hit = nodes.firstOrNull { node ->
                            x >= node.x && x <= node.x + TreeLayout.CARD_WIDTH &&
                                y >= node.y && y <= node.y + TreeLayout.CARD_HEIGHT
                        }
                        hit?.let { onSelectPerson(it.person.id) }
                    }
                }
        ) {
            val canvas = drawContext.canvas.nativeCanvas
            val save = canvas.save()
            canvas.translate(offset.x, offset.y)
            canvas.scale(scale, scale)
            canvas.scale(density, density)
            TreeRenderer.draw(canvas, family, nodes, selectedPersonId)
            canvas.restoreToCount(save)
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CanvasControl("＋") { scale = (scale * 1.25f).coerceAtMost(3.2f) }
            CanvasControl("−") { scale = (scale / 1.25f).coerceAtLeast(0.12f) }
        }
    }
}

@Composable
private fun CanvasControl(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(44.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFFF0F1E9),
        border = BorderStroke(1.dp, Color(0xFFDCE0D3)),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Leaf)
        }
    }
}

@Composable
private fun PersonEditorDialog(
    existing: Person?,
    anchor: Person?,
    photoUri: String,
    onPickPhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Person, RelativeChoice?) -> Unit
) {
    var firstName by remember(existing?.id) { mutableStateOf(existing?.firstName ?: "") }
    var lastName by remember(existing?.id) { mutableStateOf(existing?.lastName ?: "") }
    var middleName by remember(existing?.id) { mutableStateOf(existing?.middleName ?: "") }
    var birthDate by remember(existing?.id) { mutableStateOf(existing?.birthDate ?: "") }
    var deathDate by remember(existing?.id) { mutableStateOf(existing?.deathDate ?: "") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes ?: "") }
    var gender by remember(existing?.id) { mutableStateOf(existing?.gender ?: Gender.UNKNOWN) }
    var relation by remember(anchor?.id) { mutableStateOf(RelativeChoice.CHILD) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новый родственник" else "Изменить данные") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (anchor != null && existing == null) {
                    Text("Кому добавляем родственника: " + anchor.fullName(), color = Muted, fontSize = 13.sp)
                    Text("Родственная связь", fontWeight = FontWeight.SemiBold)
                    RelativeChoice.values().forEach { choice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = relation == choice, onClick = { relation = choice })
                            Text(choice.title, modifier = Modifier.clickable { relation = choice })
                        }
                    }
                }
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it; error = false },
                    label = { Text("Имя *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it; error = false },
                    label = { Text("Фамилия *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = middleName,
                    onValueChange = { middleName = it },
                    label = { Text("Отчество") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Пол", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Gender.values().forEach { value ->
                        val title = when (value) {
                            Gender.MALE -> "Мужской"
                            Gender.FEMALE -> "Женский"
                            Gender.UNKNOWN -> "Не указан"
                        }
                        val active = gender == value
                        OutlinedButton(
                            onClick = { gender = value },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (active) Leaf else Color(0xFFD9DCD2)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) Color(0xFFE8EDDF) else Color.Transparent
                            )
                        ) {
                            Text(title, fontSize = 11.sp, color = Ink)
                        }
                    }
                }
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("Дата рождения") },
                    placeholder = { Text("Например, 12.05.1940 или 1940") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deathDate,
                    onValueChange = { deathDate = it },
                    label = { Text("Дата смерти") },
                    placeholder = { Text("Можно оставить пустым") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) {
                    Text(if (photoUri.isBlank()) "Добавить фотографию" else "Фотография выбрана · заменить")
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Семейные заметки") },
                    placeholder = { Text("Истории, места, воспоминания…") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text("Заполни имя и фамилию.", color = Color(0xFFB34343), fontSize = 13.sp)
                }
                Text("* Обязательные поля", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (firstName.isBlank() || lastName.isBlank()) {
                    error = true
                } else {
                    onSave(
                        Person(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            middleName = middleName.trim(),
                            gender = gender,
                            birthDate = birthDate.trim(),
                            deathDate = deathDate.trim(),
                            photoUri = photoUri,
                            notes = notes.trim()
                        ),
                        if (existing == null && anchor != null) relation else null
                    )
                }
            }) { Text("Сохранить", color = Leaf, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Muted) }
        }
    )
}

@Composable
private fun PersonDetailsDialog(
    person: Person,
    family: FamilyData,
    onDismiss: () -> Unit,
    onAddRelative: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val linked = family.links.filter { it.fromId == person.id || it.toId == person.id }
    val relatives = linked.mapNotNull { link ->
        val otherId = if (link.fromId == person.id) link.toId else link.fromId
        val other = family.people.firstOrNull { it.id == otherId } ?: return@mapNotNull null
        val label = when (link.type) {
            LinkType.PARENT -> if (link.toId == person.id) "Родитель" else "Ребёнок"
            LinkType.SPOUSE -> "Муж / жена"
            LinkType.SIBLING -> "Брат / сестра"
        }
        label to other
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(person.fullName(), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    when (person.gender) {
                        Gender.MALE -> "Мужчина"
                        Gender.FEMALE -> "Женщина"
                        Gender.UNKNOWN -> "Пол не указан"
                    },
                    color = Muted
                )
                if (person.birthDate.isNotBlank()) Text("Родился(лась): " + person.birthDate)
                if (person.deathDate.isNotBlank()) Text("Дата смерти: " + person.deathDate)
                if (person.photoUri.isNotBlank()) Text("Фотография прикреплена", color = Leaf, fontSize = 13.sp)
                if (person.notes.isNotBlank()) {
                    Divider(color = Color(0xFFE4E2D9))
                    Text("Семейные заметки", fontWeight = FontWeight.SemiBold)
                    Text(person.notes)
                }
                if (relatives.isNotEmpty()) {
                    Divider(color = Color(0xFFE4E2D9))
                    Text("Родственные связи", fontWeight = FontWeight.SemiBold)
                    relatives.forEach { (label, relative) ->
                        Text(label + ": " + relative.fullName(), fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onAddRelative) { Text("Добавить родственника", color = Leaf) }
                TextButton(onClick = onEdit) { Text("Изменить данные", color = Leaf) }
                TextButton(onClick = onDelete) { Text("Удалить карточку", color = Color(0xFFB34343)) }
                TextButton(onClick = onDismiss) { Text("Закрыть", color = Muted) }
            }
        }
    )
}

@Composable
private fun SearchDialog(
    people: List<Person>,
    onDismiss: () -> Unit,
    onSelect: (Person) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = people.filter {
        (it.fullName() + " " + it.birthDate).contains(query.trim(), ignoreCase = true)
    }.sortedBy { it.fullName() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Найти родственника") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Имя или фамилия") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(if (query.isBlank()) "Пока нет добавленных родственников." else "Ничего не найдено.", color = Muted)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 330.dp)) {
                        items(filtered, key = { it.id }) { person ->
                            Column(
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(person) }.padding(vertical = 10.dp)
                            ) {
                                Text(person.fullName(), fontWeight = FontWeight.SemiBold, color = Ink)
                                val dates = listOf(person.birthDate, person.deathDate).filter { it.isNotBlank() }.joinToString(" — ")
                                if (dates.isNotBlank()) Text(dates, color = Muted, fontSize = 12.sp)
                            }
                            Divider(color = Color(0xFFE9E7DF))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

private fun personWord(count: Int): String {
    val lastTwo = count % 100
    val last = count % 10
    return when {
        lastTwo in 11..14 -> "человек"
        last == 1 -> "человек"
        last in 2..4 -> "человека"
        else -> "человек"
    }
}
