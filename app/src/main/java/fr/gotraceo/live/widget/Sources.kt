package fr.gotraceo.live.widget

import android.content.Context
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.NearbyLine
import fr.gotraceo.live.data.PassageSnapshot
import fr.gotraceo.live.data.PassageSource
import fr.gotraceo.live.data.Prefs

/**
 * Sources de passages de l'application : la clé, son quota du jour et le repli sur Tracéo, et le
 * `nextAt` de Tracéo retenu pour caler les actualisations suivantes.
 */
object Sources {

    fun passages(context: Context): PassageSource {
        val prefs = Prefs(context)
        val base = DataSources.passageSource(
            prefs.apiKey, prefs.primFormats, QuotaAlerts.listener(context), prefs.primPaused,
        )
        return PassageSource { stopId, lineId -> base.passages(stopId, lineId).also { note(prefs, it) } }
    }

    fun proximity(context: Context): (NearbyLine) -> Result<PassageSnapshot> {
        val prefs = Prefs(context)
        val base = DataSources.proximitySource(
            prefs.apiKey, prefs.primFormats, QuotaAlerts.listener(context), prefs.primPaused,
        )
        return { nearby -> base(nearby).also { result -> result.getOrNull()?.let { note(prefs, it) } } }
    }

    /** Retient l'heure du prochain relevé de Tracéo ; sans annonce (collecte suspendue), l'oublie. */
    private fun note(prefs: Prefs, snapshot: PassageSnapshot) {
        if (!snapshot.viaTraceo) return
        val next = snapshot.nextAt ?: 0L
        if (next != prefs.traceoNextAt) prefs.traceoNextAt = next
    }
}
