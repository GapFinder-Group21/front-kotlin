package co.gapfinder.mobile.foundation

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Abre una URL en el navegador externo (equivalente a launchUrl con externalApplication). */
object LinkOpener {
    fun openExternally(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (ignored: Exception) {
        false
    }
}
