package com.kb129.rssr.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** フィード取得と OGP 画像の取得で共有する HTTP クライアント。 */
object Http {
    const val USER_AGENT = "rssr/1.0 (Android; +https://github.com/kb129/rssr)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
