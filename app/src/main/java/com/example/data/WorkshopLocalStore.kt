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
    }
}
