package io.github.itlearner0.tvremote.data

import io.github.itlearner0.tvremote.i18n.tr

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde locale des réglages (TV, adresses IP et MAC, infrarouge,
 * touches personnalisées, applications), sans Google.
 *
 * Le fichier est écrit dans Téléchargements/OmniZap : il survit à
 * la désinstallation et se restaure en le choisissant après réinstallation.
 * La clé d'appairage du téléphone n'y figure pas (elle ne doit pas se
 * trouver dans un dossier partagé) : l'appairage est à refaire.
 */
object LocalBackup {
    const val FILE_NAME = "omnizap-sauvegarde.json"
    private const val FOLDER = "Download/OmniZap/"
    // Identifiant interne inchangé : les sauvegardes faites avant le changement de nom restent lisibles.
    private const val FORMAT = "telecommande-tv"

    /** Contenu des réglages, au format JSON. */
    fun export(prefs: SharedPreferences): String {
        val values = JSONObject()
        for ((key, value) in prefs.all) {
            val entry = when (value) {
                is String -> JSONObject().put("t", "s").put("v", value)
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.filterIsInstance<String>()))
                else -> null
            }
            entry?.let { values.put(key, it) }
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("savedAt", System.currentTimeMillis())
            .put("prefs", values)
            .toString(1)
    }

    /** Remplace les réglages par ceux de la sauvegarde. Renvoie le nombre de TV restaurées. */
    fun import(prefs: SharedPreferences, json: String): Int {
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { tr("Ce fichier n'est pas une sauvegarde d'OmniZap") }
        val values = root.getJSONObject("prefs")
        val editor = prefs.edit().clear()
        for (key in values.keys()) {
            val entry = values.getJSONObject(key)
            when (entry.getString("t")) {
                "s" -> editor.putString(key, entry.getString("v"))
                "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                "i" -> editor.putInt(key, entry.getInt("v"))
                "l" -> editor.putLong(key, entry.getLong("v"))
                "set" -> {
                    val array = entry.getJSONArray("v")
                    editor.putStringSet(key, List(array.length()) { array.getString(it) }.toSet())
                }
            }
        }
        // L'appareil n'est plus autorisé en débogage sur la TV après réinstallation.
        values.keys().asSequence().filter { it.startsWith("adb_ok_") }.forEach { editor.remove(it) }
        editor.commit()
        return runCatching { JSONArray(prefs.getString("tvs", "[]")).length() }.getOrDefault(0)
    }

    /**
     * Écrit la sauvegarde dans Téléchargements/OmniZap (Android 10 et plus),
     * en remplaçant celle que cette installation a déjà écrite.
     */
    fun writeAutomatic(context: Context, json: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            tr("{1} = ? AND {2} = ?", MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.RELATIVE_PATH),
            arrayOf(FILE_NAME, FOLDER),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) Uri.withAppendedPath(collection, cursor.getLong(0).toString()) else null
        }
        val uri = existing ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
            },
        ) ?: return
        resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
    }

    fun writeTo(context: Context, uri: Uri, json: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
            ?: error(tr("Impossible d'écrire le fichier"))
    }

    fun readFrom(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: error(tr("Impossible de lire le fichier"))
}
