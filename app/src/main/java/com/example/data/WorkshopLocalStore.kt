package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.CurrencyMode
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamWorkshopSyncProfile
import com.example.model.WatchlistItem
import com.example.model.WorkshopFollowSource
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject

data class BackupRestoreResult(
    val success: Boolean,
    val restoredGamesCount: Int = 0,
    val restoredWatchlistCount: Int = 0,
    val restoredPreferences: Boolean = false,
    val errorMessage: String? = null
)

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
        val userWatchlist = loadWatchlist(newUserId)
        val guestWatchlist = loadWatchlist("guest")
        if (userWatchlist.isEmpty() && guestWatchlist.isNotEmpty()) {
            saveWatchlist(newUserId, guestWatchlist.map { it.copy(userId = newUserId) })
        }
        copyBoolPrefIfMissing(newUserId, "guest", "notify_new_mods_enabled")
        copyBoolPrefIfMissing(newUserId, "guest", "notify_mod_updates_enabled")
        copyBoolPrefIfMissing(newUserId, "guest", "quiet_hours_enabled")
        copyIntPrefIfMissing(newUserId, "guest", "quiet_hours_start")
        copyIntPrefIfMissing(newUserId, "guest", "quiet_hours_end")
        copyBoolPrefIfMissing(newUserId, "guest", "always_open_mods_in_steam_app")
        val currencyKey = "${keyPrefix(newUserId)}preferred_currency"
        if (!prefs.contains(currencyKey)) {
            val guestCurrency = prefs.getString("${keyPrefix("guest")}preferred_currency", null)
            if (!guestCurrency.isNullOrBlank()) {
                prefs.edit().putString(currencyKey, guestCurrency).apply()
            }
        }
    }

    private fun copyBoolPrefIfMissing(targetUid: String, sourceUid: String, suffix: String) {
        val targetKey = "${keyPrefix(targetUid)}$suffix"
        val sourceKey = "${keyPrefix(sourceUid)}$suffix"
        if (!prefs.contains(targetKey) && prefs.contains(sourceKey)) {
            prefs.edit().putBoolean(targetKey, prefs.getBoolean(sourceKey, false)).apply()
        }
    }

    private fun copyIntPrefIfMissing(targetUid: String, sourceUid: String, suffix: String) {
        val targetKey = "${keyPrefix(targetUid)}$suffix"
        val sourceKey = "${keyPrefix(sourceUid)}$suffix"
        if (!prefs.contains(targetKey) && prefs.contains(sourceKey)) {
            prefs.edit().putInt(targetKey, prefs.getInt(sourceKey, 0)).apply()
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

    fun isNotifyNewModsEnabled(userId: String): Boolean {
        return prefs.getBoolean("${keyPrefix(userId)}notify_new_mods_enabled", true)
    }

    fun setNotifyNewModsEnabled(userId: String, enabled: Boolean) {
        prefs.edit()
            .putBoolean("${keyPrefix(userId)}notify_new_mods_enabled", enabled)
            .apply()
    }

    fun isNotifyModUpdatesEnabled(userId: String): Boolean {
        return prefs.getBoolean("${keyPrefix(userId)}notify_mod_updates_enabled", true)
    }

    fun setNotifyModUpdatesEnabled(userId: String, enabled: Boolean) {
        prefs.edit()
            .putBoolean("${keyPrefix(userId)}notify_mod_updates_enabled", enabled)
            .apply()
    }

    fun isQuietHoursEnabled(userId: String): Boolean {
        return prefs.getBoolean("${keyPrefix(userId)}quiet_hours_enabled", false)
    }

    fun setQuietHoursEnabled(userId: String, enabled: Boolean) {
        prefs.edit()
            .putBoolean("${keyPrefix(userId)}quiet_hours_enabled", enabled)
            .apply()
    }

    fun getQuietHoursStart(userId: String): Int {
        return prefs.getInt("${keyPrefix(userId)}quiet_hours_start", DEFAULT_QUIET_START_HOUR)
            .coerceIn(0, 23)
    }

    fun setQuietHoursStart(userId: String, hour: Int) {
        prefs.edit()
            .putInt("${keyPrefix(userId)}quiet_hours_start", hour.coerceIn(0, 23))
            .apply()
    }

    fun getQuietHoursEnd(userId: String): Int {
        return prefs.getInt("${keyPrefix(userId)}quiet_hours_end", DEFAULT_QUIET_END_HOUR)
            .coerceIn(0, 23)
    }

    fun setQuietHoursEnd(userId: String, hour: Int) {
        prefs.edit()
            .putInt("${keyPrefix(userId)}quiet_hours_end", hour.coerceIn(0, 23))
            .apply()
    }

    fun getPreferredCurrency(userId: String): CurrencyMode {
        val code = prefs.getString("${keyPrefix(userId)}preferred_currency", CurrencyMode.SEK.code)
        return CurrencyMode.entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: CurrencyMode.SEK
    }

    fun setPreferredCurrency(userId: String, currency: CurrencyMode) {
        prefs.edit()
            .putString("${keyPrefix(userId)}preferred_currency", currency.code)
            .apply()
    }

    fun isAlwaysOpenModsInSteamApp(userId: String): Boolean {
        return prefs.getBoolean("${keyPrefix(userId)}always_open_mods_in_steam_app", true)
    }

    fun setAlwaysOpenModsInSteamApp(userId: String, enabled: Boolean) {
        prefs.edit()
            .putBoolean("${keyPrefix(userId)}always_open_mods_in_steam_app", enabled)
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

    fun loadWatchlist(userId: String): List<WatchlistItem> {
        val raw = prefs.getString("${keyPrefix(userId)}watchlist_items", null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "").trim()
                    if (id.isBlank()) continue
                    val updatedSec = obj.optLong("updatedSec", 0L).takeIf { it > 0L }
                    val ts = updatedSec?.let { Timestamp(it, 0) }
                    add(
                        WatchlistItem(
                            id = id,
                            userId = userId,
                            gameTitle = obj.optString("gameTitle", ""),
                            targetPriceEur = obj.optDouble("targetPriceEur", 0.0),
                            preferredCurrency = obj.optString("preferredCurrency", "SEK"),
                            createdAt = ts,
                            updatedAt = ts
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveWatchlist(userId: String, items: List<WatchlistItem>) {
        val arr = JSONArray()
        items.distinctBy { it.id }.forEach { item ->
            if (item.id.isBlank()) return@forEach
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("gameTitle", item.gameTitle)
                    put("targetPriceEur", item.targetPriceEur)
                    put("preferredCurrency", item.preferredCurrency)
                    put("updatedSec", item.updatedAt?.seconds ?: (System.currentTimeMillis() / 1000L))
                }
            )
        }
        prefs.edit().putString("${keyPrefix(userId)}watchlist_items", arr.toString()).apply()
    }

    fun upsertWatchlistItemLocal(userId: String, item: WatchlistItem): List<WatchlistItem> {
        val current = loadWatchlist(userId).filterNot { it.id == item.id }
        val updated = listOf(item.copy(userId = userId)) + current
        saveWatchlist(userId, updated)
        return updated
    }

    fun removeWatchlistItemLocal(userId: String, gameId: String): List<WatchlistItem> {
        val updated = loadWatchlist(userId).filterNot { it.id == gameId }
        saveWatchlist(userId, updated)
        return updated
    }

    fun exportBackupJson(userId: String): String {
        val snapshot = loadSnapshot(userId)
        val watchlist = loadWatchlist(userId)

        val gamesArray = JSONArray()
        snapshot.followedGames.forEach { game ->
            gamesArray.put(
                JSONObject().apply {
                    put("appId", game.appId)
                    put("gameName", game.gameName)
                    put("developer", game.developer)
                    put("headerImageUrl", game.headerImageUrl)
                }
            )
        }

        val watchlistArray = JSONArray()
        watchlist.forEach { item ->
            watchlistArray.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("gameTitle", item.gameTitle)
                    put("targetPriceEur", item.targetPriceEur)
                    put("preferredCurrency", item.preferredCurrency)
                }
            )
        }

        val prefsObj = JSONObject().apply {
            put("newModNotificationsEnabled", isNewModNotificationsEnabled(userId))
            put("checkIntervalHours", getCheckIntervalHours(userId))
            put("notifyNewMods", isNotifyNewModsEnabled(userId))
            put("notifyModUpdates", isNotifyModUpdatesEnabled(userId))
            put("quietHoursEnabled", isQuietHoursEnabled(userId))
            put("quietHoursStart", getQuietHoursStart(userId))
            put("quietHoursEnd", getQuietHoursEnd(userId))
            put("preferredCurrency", getPreferredCurrency(userId).code)
            put("alwaysOpenModsInSteamApp", isAlwaysOpenModsInSteamApp(userId))
        }

        return JSONObject().apply {
            put("app", BACKUP_APP_MARKER)
            put("version", BACKUP_FORMAT_VERSION)
            put("exportedAtEpochSec", System.currentTimeMillis() / 1000L)
            put("followedGames", gamesArray)
            put("watchlistItems", watchlistArray)
            put("notificationPreferences", prefsObj)
        }.toString()
    }

    fun importBackupJson(userId: String, rawJson: String): BackupRestoreResult {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) {
            return BackupRestoreResult(
                success = false,
                errorMessage = "Urklipp är tomt."
            )
        }

        val root = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return BackupRestoreResult(
                success = false,
                errorMessage = "Ogiltigt JSON-format i urklipp."
            )
        }

        val hasKnownSection = root.has("followedGames") ||
            root.has("watchlistItems") ||
            root.has("notificationPreferences")
        if (!hasKnownSection) {
            return BackupRestoreResult(
                success = false,
                errorMessage = "Kunde inte hitta giltig SteamPulse-backup i urklipp."
            )
        }

        val nowTs = Timestamp.now()
        var restoredGamesCount = 0
        if (root.has("followedGames")) {
            val arr = root.optJSONArray("followedGames")
                ?: return BackupRestoreResult(
                    success = false,
                    errorMessage = "Ogiltig lista för följda spel i backup."
                )
            val parsedGames = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val appId = obj.optInt("appId", 0)
                    if (appId <= 0) continue
                    add(
                        FollowedWorkshopGame(
                            id = appId.toString(),
                            userId = userId,
                            appId = appId,
                            gameName = obj.optString("gameName", "Steam App #$appId"),
                            developer = obj.optString("developer", ""),
                            headerImageUrl = obj.optString("headerImageUrl", ""),
                            createdAt = nowTs,
                            updatedAt = nowTs
                        )
                    )
                }
            }.distinctBy { it.appId }
            val existingGames = loadSnapshot(userId).followedGames
            val mergedGames = (parsedGames + existingGames).distinctBy { it.appId }
            saveFollowedGames(userId, mergedGames)
            restoredGamesCount = parsedGames.size
        }

        var restoredWatchlistCount = 0
        if (root.has("watchlistItems")) {
            val arr = root.optJSONArray("watchlistItems")
                ?: return BackupRestoreResult(
                    success = false,
                    errorMessage = "Ogiltig bevakningslista i backup."
                )
            val parsedItems = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "").trim()
                    if (id.isBlank()) continue
                    val targetPrice = obj.optDouble("targetPriceEur", 0.0)
                        .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                    val currency = obj.optString("preferredCurrency", "SEK")
                        .takeIf { it == "SEK" || it == "EUR" } ?: "SEK"
                    add(
                        WatchlistItem(
                            id = id,
                            userId = userId,
                            gameTitle = obj.optString("gameTitle", id),
                            targetPriceEur = targetPrice,
                            preferredCurrency = currency,
                            createdAt = nowTs,
                            updatedAt = nowTs
                        )
                    )
                }
            }.distinctBy { it.id }
            val existingWatchlist = loadWatchlist(userId)
            val mergedWatchlist = (parsedItems + existingWatchlist).distinctBy { it.id }
            saveWatchlist(userId, mergedWatchlist)
            restoredWatchlistCount = parsedItems.size
        }

        var restoredPreferences = false
        val prefsObj = root.optJSONObject("notificationPreferences")
        if (prefsObj != null) {
            if (prefsObj.has("newModNotificationsEnabled")) {
                setNewModNotificationsEnabled(userId, prefsObj.optBoolean("newModNotificationsEnabled", true))
            }
            if (prefsObj.has("checkIntervalHours")) {
                setCheckIntervalHours(userId, prefsObj.optLong("checkIntervalHours", DEFAULT_CHECK_INTERVAL_HOURS))
            }
            if (prefsObj.has("notifyNewMods")) {
                setNotifyNewModsEnabled(userId, prefsObj.optBoolean("notifyNewMods", true))
            }
            if (prefsObj.has("notifyModUpdates")) {
                setNotifyModUpdatesEnabled(userId, prefsObj.optBoolean("notifyModUpdates", true))
            }
            if (prefsObj.has("quietHoursEnabled")) {
                setQuietHoursEnabled(userId, prefsObj.optBoolean("quietHoursEnabled", false))
            }
            if (prefsObj.has("quietHoursStart")) {
                setQuietHoursStart(userId, prefsObj.optInt("quietHoursStart", DEFAULT_QUIET_START_HOUR))
            }
            if (prefsObj.has("quietHoursEnd")) {
                setQuietHoursEnd(userId, prefsObj.optInt("quietHoursEnd", DEFAULT_QUIET_END_HOUR))
            }
            if (prefsObj.has("preferredCurrency")) {
                val mode = CurrencyMode.entries.firstOrNull {
                    it.code.equals(prefsObj.optString("preferredCurrency"), ignoreCase = true)
                }
                if (mode != null) {
                    setPreferredCurrency(userId, mode)
                }
            }
            if (prefsObj.has("alwaysOpenModsInSteamApp")) {
                setAlwaysOpenModsInSteamApp(userId, prefsObj.optBoolean("alwaysOpenModsInSteamApp", true))
            }
            restoredPreferences = true
        }

        return BackupRestoreResult(
            success = true,
            restoredGamesCount = restoredGamesCount,
            restoredWatchlistCount = restoredWatchlistCount,
            restoredPreferences = restoredPreferences
        )
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
        const val DEFAULT_QUIET_START_HOUR = 22
        const val DEFAULT_QUIET_END_HOUR = 8
        private const val RECENT_UPDATE_WINDOW_SECONDS = 48L * 3600L
        private const val BACKUP_APP_MARKER = "SteamPulse"
        private const val BACKUP_FORMAT_VERSION = 1

        fun isHourInQuietWindow(currentHour: Int, startHour: Int, endHour: Int): Boolean {
            val h = currentHour.coerceIn(0, 23)
            val start = startHour.coerceIn(0, 23)
            val end = endHour.coerceIn(0, 23)
            return when {
                start == end -> false
                start < end -> h in start until end
                else -> h >= start || h < end
            }
        }
    }
}
