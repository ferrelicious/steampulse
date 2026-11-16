package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamWorkshopSyncProfile
import com.example.model.WorkshopFollowSource
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject

data class WorkshopLocalSnapshot(
    val followedGames: List<FollowedWorkshopGame> = emptyList(),
    val followedMods: List<FollowedWorkshopMod> = emptyList(),
    val syncProfile: SteamWorkshopSyncProfile? = null,
    val steamSessionCookie: String = "",
    val steamSessionSteamId64: String = ""
)

class WorkshopLocalStore(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun loadSnapshot(userId: String): WorkshopLocalSnapshot {
        val prefix = keyPrefix(userId)
        val games = parseFollowedGames(prefs.getString("${prefix}games", null).orEmpty())
        val mods = parseFollowedMods(prefs.getString("${prefix}mods", null).orEmpty())
        val syncProfile = parseSyncProfile(prefs.getString("${prefix}sync_profile", null).orEmpty(), userId)
        val sessionCookie = prefs.getString("${prefix}steam_session_cookie", null).orEmpty()
        val sessionSteamId64 = prefs.getString("${prefix}steam_session_steamid64", null).orEmpty()
        return WorkshopLocalSnapshot(
            followedGames = games,
            followedMods = mods,
            syncProfile = syncProfile,
            steamSessionCookie = sessionCookie,
            steamSessionSteamId64 = sessionSteamId64
        )
    }

    fun saveFollowedGames(userId: String, games: List<FollowedWorkshopGame>) {
        val arr = JSONArray()
        games.forEach { game ->
            arr.put(
                JSONObject().apply {
                    put("appId", game.appId)
                    put("gameName", game.gameName)
                    put("developer", game.developer)
                    put("headerImageUrl", game.headerImageUrl)
                    put("updatedSec", game.updatedAt?.seconds ?: (System.currentTimeMillis() / 1000L))
                }
            )
        }
        prefs.edit().putString("${keyPrefix(userId)}games", arr.toString()).apply()
    }

    fun saveFollowedMods(userId: String, mods: List<FollowedWorkshopMod>) {
        val arr = JSONArray()
        mods.forEach { mod ->
            arr.put(
                JSONObject().apply {
                    put("publishedFileId", mod.publishedFileId)
                    put("appId", mod.appId)
                    put("gameName", mod.gameName)
                    put("title", mod.title)
                    put("author", mod.author)
                    put("previewUrl", mod.previewUrl)
                    put("itemUrl", mod.itemUrl)
                    put("source", mod.source)
                    put("createdEpochSec", mod.createdEpochSec)
                    put("updatedEpochSec", mod.updatedEpochSec)
                    put("subscriptions", mod.subscriptions)
                    put("updatedSec", mod.updatedAt?.seconds ?: (System.currentTimeMillis() / 1000L))
                }
            )
        }
        prefs.edit().putString("${keyPrefix(userId)}mods", arr.toString()).apply()
    }

    fun saveSyncProfile(userId: String, profile: SteamWorkshopSyncProfile?) {
        val key = "${keyPrefix(userId)}sync_profile"
        if (profile == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val obj = JSONObject().apply {
            put("steamId64", profile.steamId64)
            put("profileInput", profile.profileInput)
            put("syncedCount", profile.syncedCount)
            put("updatedSec", profile.updatedAt?.seconds ?: (System.currentTimeMillis() / 1000L))
        }
        prefs.edit().putString(key, obj.toString()).apply()
    }

    fun saveSteamSession(userId: String, steamId64: String, cookieHeader: String) {
        val prefix = keyPrefix(userId)
        prefs.edit()
            .putString("${prefix}steam_session_cookie", cookieHeader.trim())
            .putString("${prefix}steam_session_steamid64", steamId64.trim())
            .apply()
    }

    fun clearSteamSession(userId: String) {
        val prefix = keyPrefix(userId)
        prefs.edit()
            .remove("${prefix}steam_session_cookie")
            .remove("${prefix}steam_session_steamid64")
            .apply()
    }

    fun isGuestMode(): Boolean {
        return prefs.getBoolean(KEY_IS_GUEST_MODE, false)
    }

    fun setGuestMode(isGuest: Boolean) {
        prefs.edit().putBoolean(KEY_IS_GUEST_MODE, isGuest).apply()
    }

    fun migrateGuestDataIfEmpty(newUserId: String) {
        if (newUserId.isBlank() || newUserId == "guest") return
        val guestSnapshot = loadSnapshot("guest")
        val userSnapshot = loadSnapshot(newUserId)
        if (userSnapshot.followedGames.isEmpty() && guestSnapshot.followedGames.isNotEmpty()) {
            saveFollowedGames(newUserId, guestSnapshot.followedGames)
        }
        if (userSnapshot.followedMods.isEmpty() && guestSnapshot.followedMods.isNotEmpty()) {
            saveFollowedMods(newUserId, guestSnapshot.followedMods)
        }
        if (userSnapshot.syncProfile == null && guestSnapshot.syncProfile != null) {
            saveSyncProfile(newUserId, guestSnapshot.syncProfile)
        }
        if (userSnapshot.steamSessionCookie.isEmpty() && guestSnapshot.steamSessionCookie.isNotEmpty()) {
            saveSteamSession(newUserId, guestSnapshot.steamSessionSteamId64, guestSnapshot.steamSessionCookie)
        }
        if (getWorkshopLastVisitEpochSec(newUserId) == 0L) {
            val guestVisit = getWorkshopLastVisitEpochSec("guest")
            if (guestVisit > 0L) {
                saveWorkshopLastVisitEpochSec(newUserId, guestVisit)
            }
        }
        val notifKey = "${keyPrefix(newUserId)}new_mod_notifications_enabled"
        if (!prefs.contains(notifKey)) {
            val guestNotifKey = "${keyPrefix("guest")}new_mod_notifications_enabled"
            if (prefs.contains(guestNotifKey)) {
                setNewModNotificationsEnabled(newUserId, prefs.getBoolean(guestNotifKey, true))
            }
        }
        val intervalKey = "${keyPrefix(newUserId)}check_interval_hours"
        if (!prefs.contains(intervalKey)) {
            val guestIntervalKey = "${keyPrefix("guest")}check_interval_hours"
            if (prefs.contains(guestIntervalKey)) {
                setCheckIntervalHours(newUserId, getCheckIntervalHours("guest"))
            }
        }
        val seenMapKey = "${keyPrefix(newUserId)}seen_mod_updates_map"
        if (!prefs.contains(seenMapKey)) {
            val guestSeen = prefs.getString("${keyPrefix("guest")}seen_mod_updates_map", null)
            if (!guestSeen.isNullOrBlank()) {
                prefs.edit().putString(seenMapKey, guestSeen).apply()
            }
        }
    }

    fun getActiveUserId(): String {
        return prefs.getString(KEY_ACTIVE_USER_ID, "guest")
            ?.trim()
            ?.ifBlank { "guest" }
            ?: "guest"
    }

    fun setActiveUserId(userId: String) {
        val clean = userId.trim().ifBlank { "guest" }
        prefs.edit().putString(KEY_ACTIVE_USER_ID, clean).apply()
    }

    fun isNewModNotificationsEnabled(userId: String): Boolean {
        return prefs.getBoolean("${keyPrefix(userId)}new_mod_notifications_enabled", true)
    }

    fun setNewModNotificationsEnabled(userId: String, enabled: Boolean) {
        prefs.edit()
            .putBoolean("${keyPrefix(userId)}new_mod_notifications_enabled", enabled)
            .apply()
    }

    fun getCheckIntervalHours(userId: String): Long {
        val raw = prefs.getLong("${keyPrefix(userId)}check_interval_hours", DEFAULT_CHECK_INTERVAL_HOURS)
        return if (raw in SUPPORTED_CHECK_INTERVAL_HOURS) raw else DEFAULT_CHECK_INTERVAL_HOURS
    }

    fun setCheckIntervalHours(userId: String, hours: Long) {
        val sanitized = if (hours in SUPPORTED_CHECK_INTERVAL_HOURS) hours else DEFAULT_CHECK_INTERVAL_HOURS
        prefs.edit()
            .putLong("${keyPrefix(userId)}check_interval_hours", sanitized)
            .apply()
    }

    fun loadSeenModUpdateMap(userId: String): Map<String, Long> {
        return loadLongMap("${keyPrefix(userId)}seen_mod_updates_map")
    }

    fun saveModUpdateSeen(userId: String, publishedFileId: String, updatedEpochSec: Long) {
        val id = publishedFileId.trim()
        if (id.isBlank() || updatedEpochSec <= 0L) return
        val current = loadSeenModUpdateMap(userId).toMutableMap()
        val prev = current[id] ?: 0L
        if (updatedEpochSec > prev) {
            current[id] = updatedEpochSec
            saveLongMap("${keyPrefix(userId)}seen_mod_updates_map", current)
        }
    }

    fun removeSeenModUpdate(userId: String, publishedFileId: String) {
        val id = publishedFileId.trim()
        if (id.isBlank()) return
        val current = loadSeenModUpdateMap(userId).toMutableMap()
        if (current.remove(id) != null) {
            saveLongMap("${keyPrefix(userId)}seen_mod_updates_map", current)
        }
    }

    fun initializeModUpdateBaselinesIfMissing(
        userId: String,
        mods: List<FollowedWorkshopMod>,
        previousLocalById: Map<String, FollowedWorkshopMod> = emptyMap(),
        nowEpochSec: Long = System.currentTimeMillis() / 1000L
    ): Map<String, Long> {
        val current = loadSeenModUpdateMap(userId).toMutableMap()
        var changed = false
        mods.forEach { mod ->
            val id = mod.publishedFileId
            if (id.isBlank()) return@forEach
            val existingSeen = current[id] ?: 0L
            val prevLocalUpdated = previousLocalById[id]?.updatedEpochSec ?: 0L
            if (existingSeen == 0L) {
                if (prevLocalUpdated > 0L && mod.updatedEpochSec > prevLocalUpdated) {
                    // Previously stored mod received a newer Steam update -> preserve old timestamp as seen so badge highlights it.
                    current[id] = prevLocalUpdated
                    changed = true
                } else if (mod.updatedEpochSec > 0L && (nowEpochSec - mod.updatedEpochSec) > RECENT_UPDATE_WINDOW_SECONDS) {
                    // Old mod (>48h) synced for the first time -> record baseline so only future updates highlight.
                    current[id] = mod.updatedEpochSec
                    changed = true
                }
            }
        }
        if (changed) {
            saveLongMap("${keyPrefix(userId)}seen_mod_updates_map", current)
        }
        return current
    }

    fun loadLastNotifiedModUpdatedMap(userId: String): Map<String, Long> {
        return loadLongMap("${keyPrefix(userId)}last_notified_mod_updates_map")
    }

    fun saveLastNotifiedModUpdatedMap(userId: String, map: Map<String, Long>) {
        saveLongMap("${keyPrefix(userId)}last_notified_mod_updates_map", map)
    }

    private fun loadLongMap(key: String): Map<String, Long> {
        val raw = prefs.getString(key, null).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optLong(k, 0L)
                    if (k.isNotBlank() && v > 0L) put(k, v)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveLongMap(key: String, map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            if (k.isNotBlank() && v > 0L) {
                obj.put(k, v)
            }
        }
        prefs.edit().putString(key, obj.toString()).apply()
    }

    fun getLastNotifiedEpochSec(userId: String, appId: Int): Long {
        if (appId <= 0) return 0L
        return prefs.getLong("${keyPrefix(userId)}last_notified_sec_$appId", 0L).coerceAtLeast(0L)
    }

    fun saveLastNotifiedEpochSec(userId: String, appId: Int, epochSec: Long) {
        if (appId <= 0 || epochSec <= 0L) return
        prefs.edit().putLong("${keyPrefix(userId)}last_notified_sec_$appId", epochSec).apply()
    }

    fun getLastNotifiedModIds(userId: String, appId: Int): Set<String> {
        if (appId <= 0) return emptySet()
        return prefs.getStringSet("${keyPrefix(userId)}last_notified_ids_$appId", emptySet())
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }

    fun saveLastNotifiedModIds(userId: String, appId: Int, modIds: Set<String>) {
        if (appId <= 0) return
        val capped = modIds.filter { it.isNotBlank() }.take(MAX_STORED_NOTIFIED_IDS).toSet()
        prefs.edit().putStringSet("${keyPrefix(userId)}last_notified_ids_$appId", capped).apply()
    }

    fun clearLastNotifiedForGame(userId: String, appId: Int) {
        if (appId <= 0) return
        val prefix = keyPrefix(userId)
        prefs.edit()
            .remove("${prefix}last_notified_sec_$appId")
            .remove("${prefix}last_notified_ids_$appId")
            .apply()
    }

    fun getWorkshopLastVisitEpochSec(userId: String): Long {
        return prefs.getLong("${keyPrefix(userId)}last_workshop_visit_sec", 0L).coerceAtLeast(0L)
    }

    fun saveWorkshopLastVisitEpochSec(userId: String, epochSec: Long) {
        if (epochSec <= 0L) return
        prefs.edit().putLong("${keyPrefix(userId)}last_workshop_visit_sec", epochSec).apply()
    }

    fun getGameLastVisitEpochSec(userId: String, appId: Int): Long {
        if (appId <= 0) return 0L
        return prefs.getLong("${keyPrefix(userId)}last_game_visit_$appId", 0L).coerceAtLeast(0L)
    }

    fun saveGameLastVisitEpochSec(userId: String, appId: Int, epochSec: Long) {
        if (appId <= 0 || epochSec <= 0L) return
        prefs.edit().putLong("${keyPrefix(userId)}last_game_visit_$appId", epochSec).apply()
    }

    fun loadGameLastVisitMap(userId: String, appIds: List<Int>): Map<Int, Long> {
        val overall = getWorkshopLastVisitEpochSec(userId)
        return appIds.distinct().associateWith { appId ->
            getGameLastVisitEpochSec(userId, appId).takeIf { it > 0L } ?: overall
        }
    }

    private fun parseFollowedGames(raw: String): List<FollowedWorkshopGame> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val appId = obj.optInt("appId", 0)
                    if (appId <= 0) continue
                    val sec = obj.optLong("updatedSec", 0L).takeIf { it > 0L }
                    val ts = sec?.let { Timestamp(it, 0) }
                    add(
                        FollowedWorkshopGame(
                            id = appId.toString(),
                            appId = appId,
                            gameName = obj.optString("gameName", "Steam App #$appId"),
                            developer = obj.optString("developer", ""),
                            headerImageUrl = obj.optString("headerImageUrl", ""),
                            createdAt = ts,
                            updatedAt = ts
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseFollowedMods(raw: String): List<FollowedWorkshopMod> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("publishedFileId", "")
                    if (id.isBlank()) continue
                    val sec = obj.optLong("updatedSec", 0L).takeIf { it > 0L }
                    val ts = sec?.let { Timestamp(it, 0) }
                    add(
                        FollowedWorkshopMod(
                            id = id,
                            publishedFileId = id,
                            appId = obj.optInt("appId", 0),
                            gameName = obj.optString("gameName", ""),
                            title = obj.optString("title", "Workshop Item #$id"),
                            author = obj.optString("author", ""),
                            previewUrl = obj.optString("previewUrl", ""),
                            itemUrl = obj.optString("itemUrl", "https://steamcommunity.com/sharedfiles/filedetails/?id=$id"),
                            source = obj.optString("source", WorkshopFollowSource.BOOKMARK),
                            createdEpochSec = obj.optLong("createdEpochSec", 0L),
                            updatedEpochSec = obj.optLong("updatedEpochSec", 0L),
                            subscriptions = obj.optLong("subscriptions", 0L),
                            createdAt = ts,
                            updatedAt = ts
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseSyncProfile(raw: String, userId: String): SteamWorkshopSyncProfile? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            val steamId64 = obj.optString("steamId64", "")
            if (steamId64.isBlank()) return null
            val sec = obj.optLong("updatedSec", 0L).takeIf { it > 0L }
            val ts = sec?.let { Timestamp(it, 0) }
            SteamWorkshopSyncProfile(
                id = WorkshopFollowRepository.SYNC_DOC_ID,
                userId = userId,
                steamId64 = steamId64,
                profileInput = obj.optString("profileInput", steamId64),
                syncedCount = obj.optInt("syncedCount", 0),
                createdAt = ts,
                updatedAt = ts
            )
        }.getOrNull()
    }

    private fun keyPrefix(userId: String): String {
        val safeUid = userId.trim().ifBlank { "default" }
        return "workshop_${safeUid}_"
    }

    companion object {
        private const val PREFS_NAME = "steam_pulse_workshop_local_store"
        private const val KEY_IS_GUEST_MODE = "app_is_guest_mode"
        private const val KEY_ACTIVE_USER_ID = "app_active_user_id"
        private const val MAX_STORED_NOTIFIED_IDS = 80
        const val DEFAULT_CHECK_INTERVAL_HOURS = 2L
        val SUPPORTED_CHECK_INTERVAL_HOURS = listOf(1L, 2L, 6L, 12L)
        private const val RECENT_UPDATE_WINDOW_SECONDS = 48L * 3600L
    }
}
