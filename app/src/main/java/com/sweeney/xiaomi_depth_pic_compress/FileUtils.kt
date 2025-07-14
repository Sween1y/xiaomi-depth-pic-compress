package com.sweeney.xiaomi_depth_pic_compress

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件工具类
 * 提供文件操作相关的工具方法
 */
object FileUtils {
    
    private const val TAG = Constants.LogTags.FILE_UTILS
    
    /**
     * 从URI获取文件对象
     */
    fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            when (uri.scheme) {
                "file" -> File(uri.path ?: "")
                "content" -> getFileFromContentUri(context, uri)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法从URI获取文件路径: $uri", e)
            null
        }
    }
    
    /**
     * 从Content URI获取文件路径
     */
    private fun getFileFromContentUri(context: Context, uri: Uri): File? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DATA),
            null,
            null,
            null
        )
        
        return cursor?.use {
            if (it.moveToFirst()) {
                val pathIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                if (pathIndex >= 0) {
                    val path = it.getString(pathIndex)
                    File(path)
                } else null
            } else null
        }
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)}MB"
            else -> String.format("%.2fGB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    /**
     * 生成安全的文件名
     */
    fun generateSafeFileName(originalName: String, suffix: String, timestamp: String): String {
        val safeOriginalName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "${safeOriginalName}${suffix}_${timestamp}.jpg"
    }
    
    /**
     * 确保目录存在
     */
    fun ensureDirectoryExists(directory: File): Boolean {
        return try {
            if (!directory.exists()) {
                directory.mkdirs()
            }
            directory.exists() && directory.isDirectory
        } catch (e: Exception) {
            Log.e(TAG, "创建目录失败: ${directory.absolutePath}", e)
            false
        }
    }
    
    /**
     * 删除文件（安全删除）
     */
    fun deleteFileSafely(file: File): Boolean {
        return try {
            if (file.exists() && file.isFile) {
                file.delete()
            } else {
                true // 文件不存在，认为删除成功
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除文件失败: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * 获取文件扩展名
     */
    fun getFileExtension(file: File): String {
        val name = file.name
        val lastDotIndex = name.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            name.substring(lastDotIndex + 1).lowercase()
        } else {
            ""
        }
    }
    
    /**
     * 检查文件是否为图片
     */
    fun isImageFile(file: File): Boolean {
        val extension = getFileExtension(file)
        return extension in Constants.File.SUPPORTED_IMAGE_EXTENSIONS.split(",")
    }
    
    /**
     * 获取文件大小（格式化）
     */
    fun getFileSizeFormatted(file: File): String {
        return if (file.exists() && file.isFile) {
            formatFileSize(file.length())
        } else {
            "0B"
        }
    }
} 