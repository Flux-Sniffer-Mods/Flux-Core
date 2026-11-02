package io.github.fluxsniffermods.core.update

import io.github.fluxsniffermods.core.FluxApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxUpdatesTest {
    private val refs = listOf(
        "refs/tags/flux/v1.0",
        "refs/tags/flux/v1.1-flux.202611021300",
        "refs/tags/flux/v1.1",
        "refs/tags/flux/v1.2-flux.202611031000",
        "refs/tags/dicts-1"
    )

    @Test
    fun aFullReleaseGetsFullReleasesOnly() {
        val release = FluxUpdates.newest(FluxApp.GAMING, refs, "1.0", dev = false)!!
        assertEquals("flux/v1.1", release.tag)
        assertEquals("https://github.com/Flux-Sniffer-Mods/Flux-Gaming/releases/download/flux/v1.1/flux-gaming-1.1.apk", release.apk)
    }

    @Test
    fun devBuildsToo() {
        assertEquals("flux/v1.2-flux.202611031000", FluxUpdates.newest(FluxApp.DESKTOP, refs, "1.1", dev = true)!!.tag)
    }

    @Test
    fun nothingNewer() {
        assertNull(FluxUpdates.newest(FluxApp.STREAMING, refs, "1.2-flux.202611031000", dev = true))
    }

    @Test
    fun aDevBuildComesBeforeItsRelease() {
        assertTrue(FluxUpdates.compareVersions("1.1-flux.202611021300", "1.1")!! < 0)
        assertTrue(FluxUpdates.compareVersions("1.1", "1.0-flux.202611021300")!! > 0)
        assertNull(FluxUpdates.compareVersions("nightly", "1.0"))
    }
}
