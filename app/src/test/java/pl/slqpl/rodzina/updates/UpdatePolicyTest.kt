package pl.slqpl.rodzina.updates

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val url = "https://github.com/slq/family-locator/releases/download/v0.2.1/rodzina.apk"
    private val release = AppRelease(3, "0.2.1", url, "a".repeat(64), 1234)

    @Test fun `only release assets from our exact repository are trusted`() {
        assertTrue(UpdatePolicy.trustedAsset(url))
        for (untrusted in listOf(
            url.replace("https:", "http:"),
            url.replace("github.com/", "github.com.evil.example/"),
            url.replace("slq/family-locator/", "someone/family-locator/"),
            url.replace("github.com/", "attacker@github.com/"),
            "https://github.com/slq/family-locator/releases/download/../other.apk",
            "file:///data/local/tmp/rodzina.apk"
        )) assertFalse(untrusted, UpdatePolicy.trustedAsset(untrusted))
    }

    @Test fun `incomplete or oversized release metadata is rejected`() {
        assertTrue(UpdatePolicy.valid(release))
        for (invalid in listOf(release.copy(versionCode = 0), release.copy(versionName = ""),
            release.copy(sha256 = "bad"), release.copy(size = 0),
            release.copy(size = UpdatePolicy.MAX_APK_BYTES + 1))) assertFalse(UpdatePolicy.valid(invalid))
    }

    @Test fun `a genuine update preserves the installed identity`() {
        assertTrue(compatible())
        assertFalse(compatible(candidatePackage = "another.package"))
        assertFalse(compatible(candidateSigners = setOf("another-certificate")))
        assertFalse(compatible(candidateSigners = emptySet()))
    }

    @Test fun `downgrades same versions and a version different from metadata are blocked`() {
        assertFalse(compatible(candidateVersion = 1, expectedVersion = 1))
        assertFalse(compatible(candidateVersion = 2, expectedVersion = 2))
        assertFalse(compatible(candidateVersion = 4))
    }

    @Test fun `an unreadable installed signature cannot authorize an update`() {
        assertFalse(UpdatePolicy.compatible("pl.slqpl.rodzina", 2, emptySet(), "pl.slqpl.rodzina", 3, emptySet(), 3))
    }

    private fun compatible(candidatePackage: String = "pl.slqpl.rodzina", candidateVersion: Long = 3,
                           candidateSigners: Set<String> = setOf("trusted-certificate"), expectedVersion: Long = 3) =
        UpdatePolicy.compatible("pl.slqpl.rodzina", 2, setOf("trusted-certificate"),
            candidatePackage, candidateVersion, candidateSigners, expectedVersion)
}
