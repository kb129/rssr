package com.kb129.rssr.ui

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kb129.rssr.core.model.Article
import com.kb129.rssr.core.model.NewsCluster
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterListScreen(
    state: NewsUiState,
    onRefresh: () -> Unit,
    onThresholdChange: (Float) -> Unit,
    onShowSinglesChange: (Boolean) -> Unit,
    onOpenFeeds: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("トピック") },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "更新") }
                    IconButton(onClick = onOpenFeeds) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "フィード管理")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "controls") {
                    ClusterControls(state, onThresholdChange, onShowSinglesChange)
                }
                val errors = state.feedStatus.filterValues { it is FeedStatus.Error }
                if (errors.isNotEmpty()) {
                    item(key = "errors") {
                        Text(
                            "${errors.size} 件のフィードを取得できませんでした (フィード管理で確認)",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable(onClick = onOpenFeeds),
                        )
                    }
                }
                if (state.visibleClusters.isEmpty() && !state.isRefreshing) {
                    item(key = "empty") {
                        Text(
                            if (state.articleCount == 0) "記事がありません。フィードを追加して更新してください。"
                            else "まとまったトピックがありません。しきい値を下げるか「単独記事も表示」をオンにしてください。",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                items(state.visibleClusters, key = { it.id }) { cluster ->
                    ClusterCard(cluster)
                }
            }
        }
    }
}

@Composable
private fun ClusterControls(
    state: NewsUiState,
    onThresholdChange: (Float) -> Unit,
    onShowSinglesChange: (Boolean) -> Unit,
) {
    Column {
        val topics = state.clusters.count { it.size > 1 }
        Text(
            "${state.articleCount} 記事 → $topics トピック" +
                (state.lastUpdated?.let { " ・ 更新 ${relativeTime(it)}" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("まとめ方", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = state.threshold,
                onValueChange = onThresholdChange,
                valueRange = 0.08f..0.6f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(
                String.format(Locale.US, "%.2f", state.threshold),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(36.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "← ゆるく  /  厳しく →",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = state.showSingles,
                onClick = { onShowSinglesChange(!state.showSingles) },
                label = { Text("単独記事も表示") },
            )
        }
        if (state.isClustering) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClusterCard(cluster: NewsCluster) {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val open = { url: String -> openInAppBrowser(context, url, toolbarColor) }
    var expanded by rememberSaveable(cluster.id) { mutableStateOf(false) }
    val rep = cluster.representative
    val others = cluster.articles.drop(1)
    val collapsedCount = 2

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            // キーワード + 件数バッジ
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    cluster.keywords.forEach { KeywordChip(it) }
                }
                if (cluster.size > 1) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            "${cluster.size}件 · ${cluster.sources.size}媒体",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))

            // 代表記事 (右にサムネイル。代表記事に画像が無ければ同じ話題の他記事の画像を使う)
            Row(Modifier.fillMaxWidth().clickable { open(rep.link) }) {
                Column(Modifier.weight(1f)) {
                    Text(
                        rep.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(4.dp))
                    SourceLine(rep)
                    if (rep.summary.isNotBlank()) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            rep.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Thumbnail(
                    feedImage = rep.imageUrl ?: cluster.articles.firstNotNullOfOrNull { it.imageUrl },
                    pageUrl = rep.link,
                    source = rep.source,
                    size = 88.dp,
                )
            }

            // 同じ話題の他の記事
            if (others.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                val shown = if (expanded) others else others.take(collapsedCount)
                shown.forEach { article ->
                    RelatedArticleRow(article, onClick = { open(article.link) })
                }
                if (others.size > collapsedCount) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                        )
                        Text(if (expanded) "閉じる" else "他 ${others.size - collapsedCount} 件を表示")
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordChip(word: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            word,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SourceLine(article: Article) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(sourceColor(article.source), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            article.source + (article.publishedAt?.let { " · ${relativeTime(it)}" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RelatedArticleRow(article: Article, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Thumbnail(feedImage = article.imageUrl, pageUrl = article.link, source = article.source, size = 52.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                article.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            SourceLine(article)
        }
    }
}

private fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
