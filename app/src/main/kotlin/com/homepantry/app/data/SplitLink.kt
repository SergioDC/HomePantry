package com.homepantry.app.data

import android.content.Context
import android.content.Intent
import java.net.URI
import java.net.URISyntaxException

/**
 * Dominio de Firebase Hosting que sirve los enlaces de SNcount y el `assetlinks.json` que
 * verifica que la app puede abrirlos (Android App Links). Tiene que coincidir con el
 * `intent-filter` de `AndroidManifest.xml` y con `firebase.json`.
 */
const val SPLIT_LINK_HOST = "home-pantry-bed42.web.app"

private const val SPLIT_LINK_PREFIX = "/sncount/"

/** Enlace que se comparte para abrir el grupo [monthId] en el móvil de otro miembro de la casa. */
fun splitShareUrl(monthId: String): String = "https://$SPLIT_LINK_HOST$SPLIT_LINK_PREFIX$monthId"

/**
 * Id del grupo de un enlace de SNcount, o null si [link] no lo es. Se analiza con [URI] y no con
 * `android.net.Uri` para poder probarlo en la JVM. Admite la barra final y los parámetros que
 * añaden algunas apps al compartir; rechaza http y otros dominios.
 */
fun splitMonthIdFromLink(link: String?): String? {
    if (link == null) return null
    val uri = try {
        URI(link.trim())
    } catch (e: URISyntaxException) {
        return null
    }
    if (uri.scheme != "https" || uri.host != SPLIT_LINK_HOST) return null
    val path = uri.path ?: return null
    if (!path.startsWith(SPLIT_LINK_PREFIX)) return null
    val id = path.removePrefix(SPLIT_LINK_PREFIX).removeSuffix("/")
    return id.takeIf { it.isNotEmpty() && '/' !in it }
}

/** Abre el menú de compartir de Android con [text] (el aviso y el enlace del grupo). */
fun shareSplitMonth(context: Context, text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
