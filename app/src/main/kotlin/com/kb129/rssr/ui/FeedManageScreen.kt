package com.kb129.rssr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedManageScreen(
    state: NewsUiState,
    onAdd: (String) -> String?,
    onRemove: (String) -> Unit,
    onTogglePreferred: (String) -> Unit,
    onBack: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val currentError = error
    val submit: () -> Unit = {
        error = onAdd(input)
        if (error == null) input = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("フィード管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = { Text("RSS / Atom の URL") },
                    placeholder = { Text("https://example.com/rss.xml") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (currentError != null) {
                        { Text(currentError) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = submit, enabled = input.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = "追加")
                }
            }
            Text(
                "★ を付けたフィードは優先ソースになり、同じ話題ではその記事が代表として表示されます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.feeds, key = { it }) { url ->
                    val status = state.feedStatus[url]
                    val preferred = url in state.preferredFeeds
                    ListItem(
                        leadingContent = {
                            IconButton(onClick = { onTogglePreferred(url) }) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = if (preferred) "優先を解除" else "優先ソースにする",
                                    tint = if (preferred) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                )
                            }
                        },
                        headlineContent = {
                            Text(
                                (status as? FeedStatus.Ok)?.title ?: url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                when (status) {
                                    is FeedStatus.Ok -> Text("${status.count} 件取得")
                                    is FeedStatus.Error -> Text(
                                        "取得失敗: ${status.message}",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    null -> Text(if (state.isRefreshing) "取得中…" else "未取得")
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemove(url) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "削除")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
