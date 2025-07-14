package com.sweeney.xiaomi_depth_pic_compress

import android.graphics.Bitmap
import android.util.Log
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.icc.IccDirectory
import com.drew.metadata.xmp.XmpDirectory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 元数据处理器
 * 负责处理照片的元数据信息（EXIF、ICC、XMP）
 * 注意：当前实现为简化版本，主要用于备用方案
 */
class MetadataProcessor {
    
    companion object {
        private const val TAG = Constants.LogTags.METADATA_PROCESSOR
    }
    
    /**
     * 简化的元数据处理方法
     * 由于完整的元数据处理比较复杂，这里提供一个简化版本作为备用方案
     * @param sourceFile 原始照片文件
     * @param compressedBitmap 压缩后的位图
     * @param outputFile 输出文件
     * @return 是否成功
     */
    fun processMetadataSimple(sourceFile: File, compressedBitmap: Bitmap, outputFile: File): Boolean {
        return try {
            Log.d(TAG, "使用简化方法处理元数据: ${sourceFile.name}")
            
            // 1. 直接保存压缩后的位图
            saveCompressedImage(compressedBitmap, outputFile)
            
            // 2. 记录元数据处理信息
            val originalMetadata = ImageMetadataReader.readMetadata(sourceFile)
            logMetadataInfo(originalMetadata)
            
            Log.d(TAG, "简化元数据处理完成: ${outputFile.name}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "简化元数据处理失败", e)
            false
        }
    }
    
    /**
     * 保存压缩后的图片
     */
    private fun saveCompressedImage(bitmap: Bitmap, outputFile: File) {
        ByteArrayOutputStream().use { byteArrayOutputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, Constants.Compression.JPEG_QUALITY, byteArrayOutputStream)
            val compressedBytes = byteArrayOutputStream.toByteArray()
            
            FileOutputStream(outputFile).use { outputStream ->
                outputStream.write(compressedBytes)
            }
        }
    }
    
    /**
     * 记录元数据信息
     */
    private fun logMetadataInfo(metadata: Metadata) {
        val exifCount = metadata.getDirectoriesOfType(ExifDirectoryBase::class.java).size
        val hasIcc = metadata.getFirstDirectoryOfType(IccDirectory::class.java) != null
        val hasXmp = metadata.getFirstDirectoryOfType(XmpDirectory::class.java) != null
        
        Log.d(TAG, "元数据信息:")
        Log.d(TAG, "  - EXIF目录数量: $exifCount")
        Log.d(TAG, "  - 包含ICC信息: $hasIcc")
        Log.d(TAG, "  - 包含XMP信息: $hasXmp (将被删除)")
    }
    
    /**
     * 完整的元数据处理方法（预留接口）
     * 注意：此方法当前未实现，因为需要复杂的JPEG段处理
     * 建议使用 ExifMetadataProcessor 进行EXIF处理
     */
    @Deprecated("使用 ExifMetadataProcessor 替代", ReplaceWith("ExifMetadataProcessor().processMetadata()"))
    fun processMetadata(sourceFile: File, compressedBitmap: Bitmap, outputFile: File): Boolean {
        Log.w(TAG, "完整元数据处理方法未实现，使用简化方法")
        return processMetadataSimple(sourceFile, compressedBitmap, outputFile)
    }
} 