# RSSR — LLM を使わないオンデバイス RSS トピッククラスタリング

複数の RSS / Atom フィードを取得し、**同じ話題の記事を自動でまとめて** Jetpack Compose で表示する Android アプリです。
LLM や外部 API は使わず、**TF-IDF + コサイン類似度 + Jaccard 係数** による軽量な類似度計算だけで、完全に端末内・無料で動きます。

## 構成

```
core/  … Android 非依存の純 Kotlin モジュール (JVM でユニットテスト可能)
  model/Models.kt              Article / Feed / NewsCluster
  parser/FeedParser.kt         RSS 2.0 / RSS 1.0 (RDF) / Atom パーサー (XmlPullParser)
  parser/DateParser.kt         RFC 822 / ISO 8601 の日付 (JST などの略称も対応)
  text/TextNormalizer.kt       HTML 除去・NFKC 正規化
  text/Tokenizer.kt            辞書不要の文字種分割 + 漢字 bigram トークナイザー
  cluster/TfIdfVectorizer.kt   TF-IDF ベクトル化
  cluster/SparseVector.kt      疎ベクトルと内積
  cluster/NewsClusterer.kt     類似度計算とグルーピング (本体)
app/   … Android アプリ (Compose)
  data/FeedFetcher.kt          OkHttp + コルーチンでフィードを並列取得
  data/FeedStore.kt            登録 URL の保存 (SharedPreferences)
  ui/NewsViewModel.kt          取得 → クラスタリング → UI 状態
  ui/ClusterListScreen.kt      トピック一覧 (カード / 関連記事の展開 / しきい値スライダー)
  ui/FeedManageScreen.kt       フィードの追加・削除・取得状況
```

## アルゴリズム

### 1. トークナイズ (`CharTypeTokenizer`)

形態素解析器の辞書 (数十 MB) を持たずに日本語を扱うため、次の規則で特徴語を作ります。

| 文字種 | 扱い | 例 |
| --- | --- | --- |
| 漢字の連続 | 文字 bigram に分解 (2 文字ならそのまま、1 文字は捨てる) | 日本銀行総裁 → 日本 / 本銀 / 銀行 / 行総 / 総裁 |
| カタカナ・英字 | 1 語として扱う (NFKC で全角・半角を統一、小文字化) | アップル, iphone |
| 数字 | 2 桁以上を 1 語として扱う | 18, 2026 |
| ひらがな | 助詞・助動詞がほとんどなので捨てる | |

bigram にすることで「日銀総裁」「日本銀行の総裁」のような表記・区切りの揺れでも共通語が残ります。
「速報」「動画」など見出しの定型語はストップワードで除外します。

### 2. ベクトル化 (`TfIdfVectorizer`)

- タイトルは 2 倍の重み、概要は先頭 200 文字
- `tf = 1 + ln(count)`、`idf = ln((N+1)/(df+1)) + 1`、L2 正規化
- 記事の過半に出る語は捨てる (記事数 20 以上のとき)

### 3. 類似度 (`NewsClusterer.similarity`)

```
sim(a, b) = 0.75 · cos(tfidf_a, tfidf_b) + 0.25 · Jaccard(タイトル語_a, タイトル語_b)
          × 時間減衰 (公開時刻の差が 48 時間を超えたら exp(-(Δ-48h)/48h))
```

全ペアを計算せず、**転置インデックスで語を共有するペアだけ**を計算するので、数百〜千件でも軽量です。

### 4. グルーピング

1. `sim ≥ threshold` (既定 0.20) のペアを類似度の高い順に並べる
2. 両端の記事が属するクラスタの **平均連結類似度** (全メンバー間の類似度の平均) が `threshold × 0.75` 以上なら併合
   - 単純な連結成分 (Union-Find) だと「A≈B, B≈C だが A と C は無関係」でも繋がる鎖状効果が起きるため、それを抑えています
3. クラスタごとに
   - **代表記事**: 他メンバーとの類似度の合計が最大の記事 (メドイド)
   - **キーワード**: クラスタ内の多くのタイトルに現れ、全体では珍しい表層語
   - 並び順: 報じている媒体数 → 記事数 → 新しさ

同じリンク・同じ ID・同じ正規化タイトルの記事は事前に 1 つにまとめます。

UI のスライダーで `threshold` を変えると、再取得せずに即座に再クラスタリングされます。

## ビルド

Android Studio (Ladybug 以降) でプロジェクトを開いて実行するか:

```bash
./gradlew :app:assembleDebug      # APK
./gradlew :core:test              # クラスタリング・パーサーのユニットテスト
```

- minSdk 26 / targetSdk 35、Kotlin 2.1、Compose BOM 2024.12
- 依存は AndroidX / Compose / OkHttp / kotlinx.coroutines のみ (すべて無料・OSS)

## 拡張のヒント

### 形態素解析を使う

`Tokenizer` インターフェースを実装すれば差し替えられます。例: [Kuromoji](https://github.com/atilika/kuromoji) (IPADIC 同梱で約 12MB、Android で動作)。

```kotlin
class KuromojiTokenizer : Tokenizer {
    private val tokenizer = com.atilika.kuromoji.ipadic.Tokenizer()
    override fun tokenize(text: String) = tokenizer.tokenize(TextNormalizer.normalize(text))
        .filter { it.partOfSpeechLevel1 == "名詞" && it.surface.length >= 2 }
        .map { it.baseForm.takeIf { b -> b != "*" } ?: it.surface }
    override fun surfaceWords(text: String) = tokenize(text)
}

NewsClusterer(tokenizer = KuromojiTokenizer())
```

### ローカル埋め込みモデル (ONNX Runtime) を併用する

精度をさらに上げたい場合は、`intfloat/multilingual-e5-small` などを量子化した ONNX (〜100MB) を
`com.microsoft.onnxruntime:onnxruntime-android` で動かし、文ベクトルのコサイン類似度を TF-IDF 類似度と混ぜられます
(トークナイザーは `ai.djl.huggingface:tokenizers` など)。CPU のみ・オフラインで動作しますが、APK サイズと
1 記事あたり数十 ms の推論時間が増えるため、本アプリの既定では TF-IDF 方式を採用しています。
