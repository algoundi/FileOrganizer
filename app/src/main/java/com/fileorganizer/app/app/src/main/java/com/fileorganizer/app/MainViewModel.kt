package com.fileorganizer.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("folders", 0)

    val folders = mutableStateListOf<Uri>()
    var running by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var result by mutableStateOf<OrganizeResult?>(null); private set

    init {
        prefs.getStringSet("uris", emptySet())?.forEach { folders += Uri.parse(it) }
    }

    fun displayName(uri: Uri): String =
        DocumentFile.fromTreeUri(getApplication(), uri)?.name ?: uri.lastPathSegment ?: uri.toString()

    fun addFolder(uri: Uri) {
        if (uri in folders) return
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        folders += uri
        save()
    }

    fun removeFolder(uri: Uri) {
        folders -= uri
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        save()
    }

    private fun save() {
        prefs.edit().putStringSet("uris", folders.map { it.toString() }.toSet()).apply()
    }

    fun start() {
        if (running || folders.isEmpty()) return
        running = true
        result = null
        viewModelScope.launch {
            result = Organizer.organize(getApplication(), folders.toList()) { status = it }
            status = ""
            running = false
        }
    }
}
