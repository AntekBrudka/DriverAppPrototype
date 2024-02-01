package com.example.driverAppPrototype

import android.content.Context
import org.pytorch.LiteModuleLoader
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import org.pytorch.Module

object FileLoader {
    @Throws(IOException::class)
    fun assetFilePath(appContext: Context, assetName: String): String {
        val file = File(appContext.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        appContext.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
        }
    }
    fun loadModel(appContext: Context, modelFileName: String): Module? {
        return LiteModuleLoader.load(this.assetFilePath(appContext, modelFileName))
    }
    fun loadClasses(appContext: Context, classFileName: String): MutableList<String> {
        val br = BufferedReader(InputStreamReader(appContext.assets.open(classFileName)))
        val iterator = br.lineSequence().iterator()
        val classes: MutableList<String> = ArrayList()
        while(iterator.hasNext())
        {
            classes.add(iterator.next())
        }
        PrePostProcessor.assignClasses(classes.toTypedArray())
        br.close()
        return classes
    }
}