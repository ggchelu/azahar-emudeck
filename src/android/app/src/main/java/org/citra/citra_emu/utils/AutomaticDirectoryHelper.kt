// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Helper for automatic directory creation and setup for Azahar
 */
object AutomaticDirectoryHelper {
    private const val AZAHAR_FOLDER_NAME = "Azahar"
    private const val TAG = "AutomaticDirectoryHelper"
    
    /**
     * Creates Azahar directory automatically at root storage level
     * @param context Application context
     * @return Pair of success status and URI of created directory
     */
    fun createAzaharDirectoryAutomatically(context: Context): Pair<Boolean, Uri?> {
        try {
            // Get external storage root directory (same level as Alarms, Android, DCIM)
            val externalStorageDir = Environment.getExternalStorageDirectory()
            if (externalStorageDir == null || !externalStorageDir.exists()) {
                android.util.Log.e(TAG, "External storage root not available")
                return Pair(false, null)
            }
            
            android.util.Log.d(TAG, "External storage root: ${externalStorageDir.absolutePath}")
            
            val azaharDir = File(externalStorageDir, AZAHAR_FOLDER_NAME)
            android.util.Log.d(TAG, "Target Azahar directory: ${azaharDir.absolutePath}")
            
            if (!azaharDir.exists()) {
                val created = azaharDir.mkdirs()
                if (!created) {
                    android.util.Log.e(TAG, "Failed to create Azahar directory at: ${azaharDir.absolutePath}")
                    return Pair(false, null)
                }
                android.util.Log.i(TAG, "Successfully created Azahar directory at: ${azaharDir.absolutePath}")
            } else {
                android.util.Log.i(TAG, "Azahar directory already exists at: ${azaharDir.absolutePath}")
            }
            
            // Convert to URI for compatibility with existing code
            val uri = Uri.fromFile(azaharDir)
            android.util.Log.d(TAG, "Created URI: $uri")
            return Pair(true, uri)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception creating Azahar directory: ${e.message}", e)
            return Pair(false, null)
        }
    }
    
    /**
     * Creates an Azahar subdirectory for games if it doesn't exist
     * @param context Application context
     * @param azaharUri URI of the main Azahar directory
     * @return Uri of the games directory or null if creation failed
     */
    fun createGamesDirectory(context: Context, azaharUri: Uri): Uri? {
        try {
            val azaharPath = azaharUri.path
            if (azaharPath == null) {
                android.util.Log.e(TAG, "Invalid Azahar URI")
                return null
            }
            
            val azaharDir = File(azaharPath)
            if (!azaharDir.exists()) {
                android.util.Log.e(TAG, "Azahar directory doesn't exist")
                return null
            }
            
            val gamesDir = File(azaharDir, "Games")
            if (!gamesDir.exists()) {
                val created = gamesDir.mkdirs()
                if (!created) {
                    android.util.Log.e(TAG, "Failed to create Games directory")
                    return null
                }
                android.util.Log.i(TAG, "Created Games directory")
            }
            
            return Uri.fromFile(gamesDir)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception creating games directory: ${e.message}")
            return null
        }
    }
    
    /**
     * Checks if automatic directory setup is possible
     * @param context Application context
     * @return true if automatic setup is feasible
     */
    fun canCreateAutomatically(context: Context): Boolean {
        try {
            val externalStorageDir = Environment.getExternalStorageDirectory()
            return externalStorageDir != null && externalStorageDir.exists() && externalStorageDir.canWrite()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception checking automatic creation feasibility: ${e.message}")
            return false
        }
    }
} 