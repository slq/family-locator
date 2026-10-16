package pl.slqpl.rodzina.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.slqpl.rodzina.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdates(private val context: Context) {
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = connect("https://api.github.com/repos/${UpdatePolicy.REPOSITORY}/releases/latest")
        val release = try {
            if (connection.responseCode == 404) return@withContext null
            requireSuccess(connection)
            JSONObject(connection.inputStream.use { readLimited(it, 512 * 1024).toString(Charsets.UTF_8) })
        } finally { connection.disconnect() }
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
        val assets = release.getJSONArray("assets")
        fun asset(name: String): JSONObject? = (0 until assets.length()).map { assets.getJSONObject(it) }
            .singleOrNull { it.optString("name") == name }
        // Releases without the updater manifest are diagnostic or legacy builds.
        val manifestUrl = asset("update.json")?.getString("browser_download_url") ?: return@withContext null
        check(UpdatePolicy.trustedAsset(manifestUrl)) { "Nieprawidłowe źródło aktualizacji." }
        val manifestConnection = connect(manifestUrl)
        val manifest = try {
            requireSuccess(manifestConnection)
            JSONObject(manifestConnection.inputStream.use { readLimited(it, 16 * 1024).toString(Charsets.UTF_8) })
        } finally { manifestConnection.disconnect() }
        check(manifest.getString("packageName") == context.packageName) { "Aktualizacja dotyczy innej aplikacji." }
        val apk = asset("rodzina.apk") ?: error("W wydaniu brakuje pliku APK.")
        val candidate = AppRelease(manifest.getLong("versionCode"), manifest.getString("versionName"),
            apk.getString("browser_download_url"), manifest.getString("sha256"), apk.getLong("size"))
        check(UpdatePolicy.valid(candidate)) { "Nieprawidłowe dane aktualizacji." }
        candidate.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    suspend fun download(release: AppRelease, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        check(UpdatePolicy.valid(release)) { "Nieprawidłowe dane aktualizacji." }
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "update.part")
        val target = File(directory, "update.apk")
        target.delete()
        val connection = connect(release.apkUrl)
        try {
            requireSuccess(connection)
            val declaredSize = connection.contentLengthLong
            check(declaredSize == -1L || declaredSize == release.size) { "Rozmiar aktualizacji jest niezgodny." }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastPercent = -1
            connection.inputStream.use { input -> partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count
                    check(received <= release.size && received <= UpdatePolicy.MAX_APK_BYTES) { "Plik aktualizacji jest za duży." }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    val percent = (received * 100 / release.size).toInt()
                    if (percent != lastPercent) { progress(percent); lastPercent = percent }
                }
            } }
            check(received == release.size && hex(digest.digest()).equals(release.sha256, ignoreCase = true)) {
                "Plik aktualizacji jest niekompletny lub uszkodzony. Pobierz go ponownie."
            }
            validateApk(partial, release.versionCode)
            check(partial.renameTo(target)) { "Nie udało się zapisać aktualizacji." }
            target
        } finally { connection.disconnect(); partial.delete() }
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File, expectedVersion: Long) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val downloaded = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Pobrany plik nie jest poprawnym APK.")
        check(UpdatePolicy.compatible(context.packageName, PackageInfoCompat.getLongVersionCode(installed), signers(installed),
            downloaded.packageName, PackageInfoCompat.getLongVersionCode(downloaded), signers(downloaded), expectedVersion)) {
            "APK ma inny podpis, pakiet lub numer wersji. Nie można zaktualizować tej instalacji."
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }

    fun installer(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun connect(address: String): HttpURLConnection {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "Rodzina/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        // No credentials are embedded in the APK. Public release assets are fetched over HTTPS.
        return connection
    }

    private fun requireSuccess(connection: HttpURLConnection) {
        when (connection.responseCode) {
            200 -> Unit
            403, 429 -> throw IOException("Limit sprawdzania aktualizacji. Spróbuj ponownie później.")
            else -> throw IOException("Nie udało się pobrać aktualizacji (HTTP ${connection.responseCode}).")
        }
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(output.size() + count <= limit) { "Odpowiedź serwera aktualizacji jest za duża." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
