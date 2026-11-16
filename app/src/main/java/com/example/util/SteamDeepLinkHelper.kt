package com.example.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object SteamDeepLinkHelper {
    const val STEAM_PACKAGE_NAME = "com.valvesoftware.android.steam.community"

    fun buildSteamAppUri(publishedFileId: String): Uri =
        Uri.parse("steam://url/CommunityFilePage/${publishedFileId.trim()}")

    fun buildWebFallbackUri(publishedFileId: String, itemUrl: String = ""): Uri {
        val cleanUrl = itemUrl.trim().takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        } ?: "https://steamcommunity.com/sharedfiles/filedetails/?id=${publishedFileId.trim()}"
        return Uri.parse(cleanUrl)
    }

    fun isSteamAppInstalled(packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(STEAM_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun buildSteamDeepLinkIntent(
        context: Context,
        publishedFileId: String
    ): Intent {
        return Intent(Intent.ACTION_VIEW, buildSteamAppUri(publishedFileId)).apply {
            if (isSteamAppInstalled(context.packageManager)) {
                setPackage(STEAM_PACKAGE_NAME)
            }
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun buildWebFallbackIntent(
        context: Context,
        publishedFileId: String,
        itemUrl: String = ""
    ): Intent {
        return Intent(Intent.ACTION_VIEW, buildWebFallbackUri(publishedFileId, itemUrl)).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun openWorkshopModInSteam(
        context: Context,
        publishedFileId: String,
        itemUrl: String = ""
    ): Boolean {
        val cleanId = publishedFileId.trim()
        if (cleanId.isNotEmpty()) {
            try {
                context.startActivity(buildSteamDeepLinkIntent(context, cleanId))
                return true
            } catch (_: ActivityNotFoundException) {
                // Fall through to browser URL
            } catch (_: Exception) {
                // Fall through to browser URL
            }
        }

        return try {
            context.startActivity(buildWebFallbackIntent(context, cleanId, itemUrl))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun buildSteamStoreAppUri(appId: Int): Uri =
        Uri.parse("steam://store/$appId")

    fun buildStoreWebFallbackUri(appId: Int): Uri =
        Uri.parse("https://store.steampowered.com/app/$appId/")

    fun buildSteamStoreDeepLinkIntent(
        context: Context,
        appId: Int
    ): Intent {
        return Intent(Intent.ACTION_VIEW, buildSteamStoreAppUri(appId)).apply {
            if (isSteamAppInstalled(context.packageManager)) {
                setPackage(STEAM_PACKAGE_NAME)
            }
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun openStoreGameInSteam(
        context: Context,
        appId: Int
    ): Boolean {
        if (appId <= 0) return false
        try {
            context.startActivity(buildSteamStoreDeepLinkIntent(context, appId))
            return true
        } catch (_: ActivityNotFoundException) {
            // Fall through to Steam Store web page
        } catch (_: Exception) {
            // Fall through to Steam Store web page
        }

        return try {
            val webIntent = Intent(Intent.ACTION_VIEW, buildStoreWebFallbackUri(appId)).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(webIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
