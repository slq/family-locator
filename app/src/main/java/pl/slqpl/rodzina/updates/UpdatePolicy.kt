package pl.slqpl.rodzina.updates

import java.net.URI

data class AppRelease(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String, val size: Long)

object UpdatePolicy {
    const val REPOSITORY = "slq/family-locator"
    const val MAX_APK_BYTES = 150L * 1024 * 1024

    fun trustedAsset(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.port == -1 && uri.path.startsWith("/$REPOSITORY/releases/download/") &&
            !uri.path.contains("/../")
    }.getOrDefault(false)

    fun valid(release: AppRelease): Boolean = release.versionCode > 0 &&
        release.versionName.isNotBlank() && release.versionName.length <= 40 &&
        trustedAsset(release.apkUrl) && release.sha256.matches(Regex("[a-fA-F0-9]{64}")) &&
        release.size in 1..MAX_APK_BYTES

    fun compatible(installedPackage: String, installedVersion: Long, installedSigners: Set<String>,
                   candidatePackage: String, candidateVersion: Long, candidateSigners: Set<String>,
                   expectedVersion: Long): Boolean = installedPackage == candidatePackage &&
        candidateVersion == expectedVersion && candidateVersion > installedVersion &&
        installedSigners.isNotEmpty() && installedSigners == candidateSigners
}
