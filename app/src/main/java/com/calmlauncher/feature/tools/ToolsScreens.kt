package com.calmlauncher.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmPage
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.EmptyState
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.data.db.NoteEntity
import com.calmlauncher.data.db.TodoEntity
import com.calmlauncher.data.tools.ToolsRepository
import com.calmlauncher.ui.ConfirmDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(private val repo: ToolsRepository) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> = repo.notes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val todos: StateFlow<List<TodoEntity>> = repo.todos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveNote(id: Long, text: String) = viewModelScope.launch { repo.saveNote(id, text) }
    fun deleteNote(id: Long) = viewModelScope.launch { repo.deleteNote(id) }
    fun addTodo(text: String) = viewModelScope.launch { repo.addTodo(text) }
    fun toggleTodo(todo: TodoEntity) = viewModelScope.launch { repo.setTodoDone(todo, !todo.done) }
    fun deleteTodo(id: Long) = viewModelScope.launch { repo.deleteTodo(id) }
    fun clearDone() = viewModelScope.launch { repo.clearDone() }
}

@Composable
fun NotesScreen(onBack: () -> Unit, vm: ToolsViewModel = hiltViewModel()) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

    CalmPage(
        title = stringResource(R.string.notes_title),
        onBack = onBack,
        actions = { CalmTextButton(stringResource(R.string.notes_new), onClick = { editing = 0L }) },
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            if (notes.isEmpty()) item { EmptyState(stringResource(R.string.notes_empty)) }
            items(notes, key = { it.id }) { note ->
                Text(
                    note.text,
                    style = CalmTheme.type.body,
                    color = CalmTheme.colors.text,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.touchTarget)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.notes_edit)) { editing = note.id }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }

    editing?.let { id ->
        val note = notes.firstOrNull { it.id == id }
        NoteEditorDialog(
            title = stringResource(if (id == 0L) R.string.notes_new else R.string.notes_edit),
            initial = note?.text.orEmpty(),
            canDelete = note != null,
            onDismiss = { editing = null },
            onSave = {
                vm.saveNote(id, it)
                editing = null
            },
            onDelete = {
                editing = null
                deleting = id
            },
        )
    }
    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.notes_delete_title),
            message = stringResource(R.string.notes_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onDismiss = { deleting = null },
            onConfirm = {
                vm.deleteNote(id)
                deleting = null
            },
        )
    }
}

@Composable
private fun NoteEditorDialog(
    title: String,
    initial: String,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onSave(text) },
        title = { Text(title, style = CalmTheme.type.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(10_000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
        },
        confirmButton = { CalmTextButton(stringResource(R.string.action_save), emphasized = true, onClick = { onSave(text) }) },
        dismissButton = {
            Row {
                if (canDelete) CalmTextButton(stringResource(R.string.action_delete), onClick = onDelete)
                CalmTextButton(stringResource(R.string.action_cancel), onClick = onDismiss)
            }
        },
        containerColor = CalmTheme.colors.surface,
    )
}

@Composable
fun TodosScreen(onBack: () -> Unit, vm: ToolsViewModel = hiltViewModel()) {
    val todos by vm.todos.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }

    CalmPage(
        title = stringResource(R.string.todos_title),
        onBack = onBack,
        actions = {
            if (todos.any { it.done }) CalmTextButton(stringResource(R.string.todos_clear_done), onClick = { vm.clearDone() })
        },
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(200) },
            placeholder = { Text(stringResource(R.string.todos_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                vm.addTodo(draft)
                draft = ""
            }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            if (todos.isEmpty()) item { EmptyState(stringResource(R.string.todos_empty)) }
            items(todos, key = { it.id }) { todo ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.touchTarget)
                        .toggleable(value = todo.done, role = Role.Checkbox) { vm.toggleTodo(todo) }
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = todo.done, onCheckedChange = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        todo.text,
                        style = CalmTheme.type.body,
                        color = if (todo.done) CalmTheme.colors.textSecondary else CalmTheme.colors.text,
                        textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    CalmTextButton(stringResource(R.string.action_delete), onClick = { vm.deleteTodo(todo.id) })
                }
            }
        }
    }
}
