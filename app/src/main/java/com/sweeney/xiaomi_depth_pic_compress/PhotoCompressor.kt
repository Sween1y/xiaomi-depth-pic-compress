package com.sweeney.xiaomi_depth_pic_compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 压缩结果数据类
 */
data class CompressionResult(
    val originalPath: String,
    val compressedPath: String,
    val originalSize: Long,
    val compressedSize: Long,
    val savedSpace: Long
)

/**
 * 照片压缩器
 * 负责照片的压缩、元数据处理和文件管理
 */
class PhotoCompressor(private val context: Context) {
    
    companion object {
        private const val TAG = Constants.LogTags.PHOTO_COMPRESSOR
    }
    
    private val metadataProcessor = MetadataProcessor()
    private val exifMetadataProcessor = ExifMetadataProcessor()
    
    /**
     * 压缩照片
     * @param sourceUri 原始照片URI
     * @return 压缩结果，包含文件路径和大小信息
     */
    suspend fun compressPhoto(sourceUri: Uri): CompressionResult? {
        return try {
            Log.d(TAG, "开始压缩照片: $sourceUri")

            // 1. 读取原始照片和元数据
            val (bitmap, originalMetadata) = readPhotoWithMetadata(sourceUri)
                ?: return null

            // 2. 获取原始文件信息
            val sourceFile = getFileFromUri(sourceUri)
            val originalSize = sourceFile?.length() ?: 0L

            // 3. 创建压缩后的照片
            val compressedFile = createCompressedPhoto(sourceUri, bitmap, originalMetadata)
            val compressedSize = compressedFile.length()
            
            val result = CompressionResult(
                originalPath = sourceFile?.absolutePath ?: "",
                compressedPath = compressedFile.absolutePath,
                originalSize = originalSize,
                compressedSize = compressedSize,
                savedSpace = originalSize - compressedSize
            )
            
            Log.d(TAG, "照片压缩完成: ${compressedFile.absolutePath}")
            Log.d(TAG, "原始大小: ${formatFileSize(originalSize)}, 压缩后: ${formatFileSize(compressedSize)}, 节省: ${formatFileSize(result.savedSpace)}")
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "压缩照片失败: $sourceUri", e)
            null
        }
    }
    
    /**
     * 读取照片和元数据
     */
    private suspend fun readPhotoWithMetadata(uri: Uri): Pair<Bitmap, Metadata>? {
        return try {
            val contentResolver = context.contentResolver

            // 读取元数据
            val metadata = contentResolver.openInputStream(uri)?.use { inputStream ->
                ImageMetadataReader.readMetadata(inputStream)
            } ?: throw Exception("无法读取照片元数据")

            // 读取图片
            val bitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: throw Exception("无法解码照片")
            
            Pair(bitmap, metadata)
        } catch (e: Exception) {
            Log.e(TAG, "读取照片和元数据失败", e)
            null
        }
    }
    
    /**
     * 创建压缩后的照片
     */
    private suspend fun createCompressedPhoto(sourceUri: Uri, bitmap: Bitmap, originalMetadata: Metadata): File {
        val sourceFile = getFileFromUri(sourceUri)
            ?: throw Exception("无法获取原始文件路径")
        
        if (!sourceFile.exists()) {
            throw Exception("原始文件不存在")
        }

        // 生成输出文件名
        val outputFile = generateOutputFile(sourceFile)
        
        // 尝试不同的压缩策略
        return when {
            // 策略1: EXIF元数据处理器（推荐）
            tryExifCompression(sourceFile, bitmap, outputFile) -> {
                Log.d(TAG, "使用EXIF元数据处理器完成压缩")
                outputFile
            }
//
//            // 策略2: 简单元数据处理器
//            trySimpleCompression(sourceFile, bitmap, outputFile) -> {
//                Log.d(TAG, "使用简单元数据处理器完成压缩")
//                outputFile
//            }
            
            // 策略3: 基础压缩（兜底方案）
            else -> {
                Log.d(TAG, "使用基础压缩方法")
                performBasicCompression(bitmap, outputFile)
                outputFile
            }
        }
    }
    
    /**
     * 尝试EXIF压缩
     */
    private fun tryExifCompression(sourceFile: File, bitmap: Bitmap, outputFile: File): Boolean {
        return try {
            exifMetadataProcessor.processMetadata(sourceFile, bitmap, outputFile)
        } catch (e: Exception) {
            Log.w(TAG, "EXIF压缩失败", e)
            false
        }
    }
    
    /**
     * 尝试简单压缩
     */
    private fun trySimpleCompression(sourceFile: File, bitmap: Bitmap, outputFile: File): Boolean {
        return try {
            metadataProcessor.processMetadataSimple(sourceFile, bitmap, outputFile)
        } catch (e: Exception) {
            Log.w(TAG, "简单压缩失败", e)
            false
        }
    }
    
    /**
     * 执行基础压缩
     */
    private fun performBasicCompression(bitmap: Bitmap, outputFile: File) {
        ByteArrayOutputStream().use { byteArrayOutputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, Constants.Compression.JPEG_QUALITY, byteArrayOutputStream)
            val compressedBytes = byteArrayOutputStream.toByteArray()
            
            FileOutputStream(outputFile).use { outputStream ->
                outputStream.write(compressedBytes)
            }
        }
        
        Log.d(TAG, "基础压缩完成: ${outputFile.absolutePath}")
    }
    
    /**
     * 生成输出文件名
     */
    private fun generateOutputFile(sourceFile: File): File {
        val originalDir = sourceFile.parentFile
        val originalName = sourceFile.nameWithoutExtension
        val originalDateTime = getOriginalDateTime(sourceFile)
        val fileName = "${originalName}${Constants.Compression.COMPRESSED_FILE_SUFFIX}_${originalDateTime}.jpg"
        
        return File(originalDir, fileName)
    }
    
    /**
     * 获取原始照片的拍摄时间
     */
    private fun getOriginalDateTime(sourceFile: File): String {
        return try {
            val exif = ExifInterface(sourceFile.absolutePath)
            val dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            
            if (!dateTimeOriginal.isNullOrEmpty()) {
                // 将EXIF时间格式转换为文件名格式
                val inputFormat = SimpleDateFormat(Constants.TimeFormat.EXIF_INPUT_FORMAT, Locale.getDefault())
                val outputFormat = SimpleDateFormat(Constants.TimeFormat.FILE_NAME_FORMAT, Locale.getDefault())
                
                try {
                    val date = inputFormat.parse(dateTimeOriginal)
                    outputFormat.format(date ?: Date())
                } catch (e: Exception) {
                    Log.w(TAG, "解析EXIF时间失败，使用当前时间", e)
                    SimpleDateFormat(Constants.TimeFormat.FILE_NAME_FORMAT, Locale.getDefault()).format(Date())
                }
            } else {
                // 如果没有EXIF时间，使用文件修改时间
                SimpleDateFormat(Constants.TimeFormat.FILE_NAME_FORMAT, Locale.getDefault()).format(Date(sourceFile.lastModified()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取原始时间失败，使用当前时间", e)
            SimpleDateFormat(Constants.TimeFormat.FILE_NAME_FORMAT, Locale.getDefault()).format(Date())
        }
    }
    
    /**
     * 从URI获取文件对象
     */
    private fun getFileFromUri(uri: Uri): File? {
        return FileUtils.getFileFromUri(context, uri)
    }
    
    /**
     * 获取压缩后的照片列表
     */
    fun getCompressedPhotos(): List<File> {
        val compressedFiles = mutableListOf<File>()
        val externalDir = Environment.getExternalStorageDirectory()
        
        // 扫描照片目录
        Constants.Scan.PHOTO_DIRECTORIES.forEach { dirName ->
            val dir = File(externalDir, dirName)
            if (dir.exists()) {
                scanDirectoryForCompressedFiles(dir, compressedFiles)
            }
        }
        
        return compressedFiles.sortedByDescending { it.lastModified() }
    }
    
    /**
     * 扫描目录中的压缩文件
     */
    private fun scanDirectoryForCompressedFiles(dir: File, compressedFiles: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return
        
        dir.listFiles()?.forEach { file ->
            when {
                file.isFile && isCompressedFile(file) -> compressedFiles.add(file)
                file.isDirectory -> scanDirectoryForCompressedFiles(file, compressedFiles)
            }
        }
    }
    
    /**
     * 判断是否为压缩文件
     */
    private fun isCompressedFile(file: File): Boolean {
        return file.name.contains(Constants.Compression.COMPRESSED_FILE_SUFFIX) && 
               file.extension.lowercase() == Constants.File.OUTPUT_IMAGE_EXTENSION
    }
    
    /**
     * 清理压缩后的照片
     */
    fun clearCompressedPhotos(): Int {
        val compressedFiles = getCompressedPhotos()
        var deletedCount = 0
        
        compressedFiles.forEach { file ->
            if (file.delete()) {
                deletedCount++
                Log.d(TAG, "删除压缩文件: ${file.name}")
            } else {
                Log.w(TAG, "删除压缩文件失败: ${file.name}")
            }
        }
        
        Log.d(TAG, "清理完成，删除了 $deletedCount 个压缩文件")
        return deletedCount
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        return FileUtils.formatFileSize(size)
    }
} 