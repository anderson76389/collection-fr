package eu.kanade.tachiyomi.multisrc.pam

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerPageDataTest {
    @Test fun oldInertiaAttribute() {
        assertEquals("""{"component":"library"}""", serverPageData(Jsoup.parse("""<div id="app" data-page="{&quot;component&quot;:&quot;library&quot;}"></div>""")))
    }

    @Test fun newInertiaScript() {
        assertEquals("""{"component":"library"}""", serverPageData(Jsoup.parse("""<script type="application/json" data-page="app">{"component":"library"}</script>""")))
    }

    @Test fun challengeIsNotCatalogueData() {
        assertNull(serverPageData(Jsoup.parse("<html><title>Just a moment...</title></html>")))
        assertNull(serverPageData(Jsoup.parse("""<div id="app" data-page=""></div>""")))
    }
}
