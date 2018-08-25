package fredboat.agent

import com.fredboat.sentinel.entities.GuildUnsubscribeRequest
import fredboat.audio.player.PlayerRegistry
import fredboat.sentinel.GuildCache
import fredboat.sentinel.InternalGuild
import lavalink.client.io.Link
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import java.util.concurrent.TimeUnit

@Controller
class GuildCacheInvalidationAgent(
        private val guildCache: GuildCache,
        private val playerRegistry: PlayerRegistry
) : FredBoatAgent("cache-invalidator", 10, TimeUnit.MINUTES) {

    companion object {
        private const val TIMEOUT_MILLIS: Long = 30 * 60 * 1000 // 30 minutes
        private val log: Logger = LoggerFactory.getLogger(GuildCacheInvalidationAgent::class.java)
    }

    override fun doRun() {
        val keysToRemove = mutableListOf<InternalGuild>()
        guildCache.cache.forEach { _, guild ->
            if (!guild.shouldInvalidate()) return@forEach
            keysToRemove.add(guild)
        }
        keysToRemove.forEach {
            try {
                it.beforeInvalidation()
                guildCache.cache.remove(it.id)
            } catch (e: Exception) {
                log.error("Exception while invalidating guild $it")
            }
        }
    }

    private fun InternalGuild.shouldInvalidate(): Boolean {
        // Has this guild been used recently?
        if (lastUsed + TIMEOUT_MILLIS > System.currentTimeMillis()) return false

        // Are we connected to voice?
        if (link.state == Link.State.CONNECTED) return false

        // Are we playing music?
        if (this.guildPlayer?.isPlaying == true) return false

        // If not then invalidate
        return true
    }

    private fun InternalGuild.beforeInvalidation() {
        playerRegistry.destroyPlayer(this)
        sentinel.sendAndForget(routingKey, GuildUnsubscribeRequest(id))
    }

}