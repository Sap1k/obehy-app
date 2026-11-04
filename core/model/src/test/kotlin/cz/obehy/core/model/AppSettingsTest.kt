package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSettingsTest {
    private val own = "https://motis.example.org/"

    @Test
    fun `each mode plans on its own server`() {
        assertEquals("https://motis.obehy.cz/", AppSettings(AppMode.Obehy).motisUrl)
        assertEquals("https://api.transitous.org/", AppSettings(AppMode.TransitousEnriched).motisUrl)
        assertEquals("https://api.transitous.org/", AppSettings(AppMode.MotisOnly).motisUrl)
    }

    @Test
    fun `your own server counts only in the MOTIS mode`() {
        assertEquals(own, AppSettings(AppMode.MotisOnly, own).motisUrl)
        assertEquals("https://motis.obehy.cz/", AppSettings(AppMode.Obehy, own).motisUrl)
        assertEquals("https://api.transitous.org/", AppSettings(AppMode.TransitousEnriched, own).motisUrl)
    }

    @Test
    fun `the two Transitous modes share stops and history, your own server has its own`() {
        assertEquals(
            AppSettings(AppMode.TransitousEnriched).routingServer,
            AppSettings(AppMode.MotisOnly).routingServer,
        )
        assertEquals(own, AppSettings(AppMode.MotisOnly, own).routingServer)
    }

    @Test
    fun `an address gets what requests need`() {
        assertEquals(own, motisUrlOf("  https://motis.example.org  "))
        assertEquals(own, motisUrlOf("motis.example.org/"))
        assertEquals("http://192.168.1.5:8080/", motisUrlOf("http://192.168.1.5:8080"))
        assertEquals("https://example.org/motis/", motisUrlOf("https://example.org/motis"))
    }

    @Test
    fun `what cannot be a server is refused`() {
        assertNull(motisUrlOf("not a url"))
        assertNull(motisUrlOf("ftp://example.org"))
        assertNull(motisUrlOf("https://"))
        assertNull(motisUrlOf("https://example.org/?a=b"))
    }
}
