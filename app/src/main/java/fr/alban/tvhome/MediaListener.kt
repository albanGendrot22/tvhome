package fr.alban.tvhome

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Suit les lectures que les applis publient au système (MediaSession) pour la rangée « Reprendre la lecture ».
 * Android ne donne cet accès qu'à un « écouteur de notifications » autorisé : c'est le seul rôle de ce service,
 * il ne lit aucune notification et ne commande rien (plus aucun automatisme de lumières).
 */
class MediaListener : NotificationListenerService() {
    private var sessions: MediaSessionManager? = null
    private val watched = HashMap<MediaController, MediaController.Callback>()

    private val onSessions = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }

    override fun onListenerConnected() {
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessions = msm
        val me = ComponentName(this, MediaListener::class.java)
        runCatching {
            msm.addOnActiveSessionsChangedListener(onSessions, me)
            watch(msm.getActiveSessions(me))
        }.onFailure { Log.w(TAG, "Sessions média inaccessibles", it) }
    }

    override fun onListenerDisconnected() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(onSessions) }
        watched.forEach { (c, cb) -> c.unregisterCallback(cb) }
        watched.clear()
    }

    private fun watch(list: List<MediaController>) {
        watched.forEach { (c, cb) -> c.unregisterCallback(cb) }
        watched.clear()
        list.forEach { c ->
            Log.i(TAG, "Session média : ${c.packageName} état=${c.playbackState?.state}")
            ContinueStore.record(this, c.packageName, c.metadata, c.playbackState)
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    ContinueStore.record(this@MediaListener, c.packageName, c.metadata, state)
                }
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    ContinueStore.record(this@MediaListener, c.packageName, metadata, c.playbackState)
                }
            }
            c.registerCallback(cb)
            watched[c] = cb
        }
    }

    companion object {
        private const val TAG = "TvHomeMedia"
    }
}
