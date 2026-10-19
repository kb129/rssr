package com.kb129.rssr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Screen { News, Feeds }

@Composable
fun RssrApp(viewModel: NewsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.News) }

    when (screen) {
        Screen.News -> ClusterListScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onThresholdChange = viewModel::setThreshold,
            onShowSinglesChange = viewModel::setShowSingles,
            onOneArticlePerTopicChange = viewModel::setOneArticlePerTopic,
            onOpenFeeds = { screen = Screen.Feeds },
        )
        Screen.Feeds -> {
            BackHandler { screen = Screen.News }
            FeedManageScreen(
                state = state,
                onAdd = viewModel::addFeed,
                onRemove = viewModel::removeFeed,
                onTogglePreferred = viewModel::togglePreferred,
                onBack = { screen = Screen.News },
            )
        }
    }
}
