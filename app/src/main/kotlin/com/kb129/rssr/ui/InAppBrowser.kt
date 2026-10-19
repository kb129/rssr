package com.kb129.rssr.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/**
 * 記事を Custom Tabs (アプリ内ブラウザ) で開く。
 * 閉じるとそのままアプリに戻る。Custom Tabs 非対応の環境では通常のブラウザで開く。
 */
fun openInAppBrowser(context: Context, url: String, toolbarColor: Int) {
    if (url.isBlank()) return
    val uri = Uri.parse(url)
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        .setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build(),
        )
        .build()
    try {
        intent.launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "リンクを開けるアプリがありません", Toast.LENGTH_SHORT).show()
        }
    }
}
