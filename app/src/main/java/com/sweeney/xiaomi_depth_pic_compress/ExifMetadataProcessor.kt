package com.sweeney.xiaomi_depth_pic_compress

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
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
 * EXIF元数据处理器
 * 负责保留照片的EXIF信息，删除XMP信息
 */
class ExifMetadataProcessor {
    
    companion object {
        private const val TAG = Constants.LogTags.EXIF_METADATA_PROCESSOR
    }
    
    /**
     * 处理照片元数据：保留EXIF，删除XMP
     * @param sourceFile 原始照片文件
     * @param compressedBitmap 压缩后的位图
     * @param outputFile 输出文件
     * @return 是否成功
     */
    fun processMetadata(sourceFile: File, compressedBitmap: Bitmap, outputFile: File): Boolean {
        return try {
            Log.d(TAG, "开始EXIF元数据处理: ${sourceFile.name}")
            
            // 1. 调试：打印原始EXIF信息
            debugOriginalExif(sourceFile)
            
            // 2. 读取原始照片的EXIF数据
            val originalExif = ExifInterface(sourceFile.absolutePath)
            
            // 3. 保存压缩后的图片（保留EXIF）
            saveCompressedImageWithExif(compressedBitmap, originalExif, outputFile)
            
            // 4. 验证EXIF数据是否正确保存
            val verificationResult = verifyExifPreservation(sourceFile, outputFile)
            
            Log.d(TAG, "EXIF元数据处理完成: ${outputFile.name}, 验证结果: ${if (verificationResult) "成功" else "失败"}")
            
            verificationResult
            
        } catch (e: Exception) {
            Log.e(TAG, "EXIF元数据处理失败", e)
            // 如果处理失败，至少保存压缩后的图片
            saveCompressedImage(compressedBitmap, outputFile)
            false
        }
    }
    
    /**
     * 保存压缩后的图片并保留EXIF数据
     */
    private fun saveCompressedImageWithExif(bitmap: Bitmap, originalExif: ExifInterface, outputFile: File) {
        try {
            // 1. 先将位图保存到临时字节数组
            val imageBytes = bitmapToBytes(bitmap)
            
            // 2. 将字节数组写入文件
            writeBytesToFile(imageBytes, outputFile)
            
            // 3. 立即将EXIF数据写入文件
            val targetExif = ExifInterface(outputFile.absolutePath)
            copyExifData(originalExif, targetExif)
            
            Log.d(TAG, "图片保存完成，EXIF数据已写入")
            
        } catch (e: Exception) {
            Log.e(TAG, "保存图片和EXIF数据失败", e)
            throw e
        }
    }
    
    /**
     * 将位图转换为字节数组
     */
    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        return ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, Constants.Compression.JPEG_QUALITY, outputStream)
            outputStream.toByteArray()
        }
    }
    
    /**
     * 将字节数组写入文件
     */
    private fun writeBytesToFile(bytes: ByteArray, file: File) {
        FileOutputStream(file).use { outputStream ->
            outputStream.write(bytes)
        }
    }
    
    /**
     * 保存压缩后的图片（基础方法）
     */
    private fun saveCompressedImage(bitmap: Bitmap, outputFile: File) {
        val imageBytes = bitmapToBytes(bitmap)
        writeBytesToFile(imageBytes, outputFile)
    }
    
    /**
     * 复制EXIF数据
     */
    private fun copyExifData(sourceExif: ExifInterface, targetExif: ExifInterface) {
        try {
            val exifTags = getExifTagsToCopy()
            val copyResult = copyExifTags(sourceExif, targetExif, exifTags)
            
            // 保存EXIF数据到文件
            targetExif.saveAttributes()
            
            Log.d(TAG, "EXIF复制完成: 成功复制 ${copyResult.copiedCount} 个标签, 跳过 ${copyResult.skippedCount} 个空标签")
            
        } catch (e: Exception) {
            Log.e(TAG, "复制EXIF数据失败", e)
            throw e
        }
    }
    
    /**
     * 获取需要复制的EXIF标签列表
     */
    private fun getExifTagsToCopy(): Array<String> {
        return arrayOf(
            // 基础信息（最重要）
            ExifInterface.TAG_DATETIME_ORIGINAL,  // 原始拍摄时间
            ExifInterface.TAG_DATETIME,           // 修改时间
            ExifInterface.TAG_DATETIME_DIGITIZED, // 数字化时间
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, // 原始拍摄时间的毫秒部分
            ExifInterface.TAG_SUBSEC_TIME,        // 修改时间的毫秒部分
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, // 数字化时间的毫秒部分
            
            // 相机信息
            ExifInterface.TAG_MAKE,               // 相机品牌
            ExifInterface.TAG_MODEL,              // 相机型号
            ExifInterface.TAG_SOFTWARE,           // 软件信息
            ExifInterface.TAG_ARTIST,             // 艺术家信息
            ExifInterface.TAG_COPYRIGHT,          // 版权信息
            
            // 拍摄参数
            ExifInterface.TAG_EXPOSURE_TIME,      // 曝光时间
            ExifInterface.TAG_F_NUMBER,           // 光圈值
            ExifInterface.TAG_EXPOSURE_PROGRAM,   // 曝光程序
            ExifInterface.TAG_ISO_SPEED_RATINGS,  // ISO感光度
            ExifInterface.TAG_SENSITIVITY_TYPE,   // 感光度类型
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE, // 曝光补偿
            ExifInterface.TAG_MAX_APERTURE_VALUE, // 最大光圈
            ExifInterface.TAG_METERING_MODE,      // 测光模式
            ExifInterface.TAG_LIGHT_SOURCE,       // 光源
            ExifInterface.TAG_FLASH,              // 闪光灯
            ExifInterface.TAG_FLASH_ENERGY,       // 闪光灯能量
            
            // 镜头信息
            ExifInterface.TAG_FOCAL_LENGTH,       // 焦距
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, // 35mm等效焦距
            ExifInterface.TAG_LENS_MAKE,          // 镜头品牌
            ExifInterface.TAG_LENS_MODEL,         // 镜头型号
            ExifInterface.TAG_LENS_SPECIFICATION, // 镜头规格
            ExifInterface.TAG_LENS_SERIAL_NUMBER, // 镜头序列号
            
            // 图像信息
            ExifInterface.TAG_PIXEL_X_DIMENSION,  // 图像宽度
            ExifInterface.TAG_PIXEL_Y_DIMENSION,  // 图像高度
            ExifInterface.TAG_COLOR_SPACE,        // 色彩空间
            ExifInterface.TAG_ORIENTATION,        // 图像方向
            ExifInterface.TAG_X_RESOLUTION,       // X分辨率
            ExifInterface.TAG_Y_RESOLUTION,       // Y分辨率
            ExifInterface.TAG_RESOLUTION_UNIT,    // 分辨率单位
            
            // 高级拍摄参数
            ExifInterface.TAG_WHITE_BALANCE,      // 白平衡
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO, // 数码变焦比例
            ExifInterface.TAG_SCENE_CAPTURE_TYPE, // 场景拍摄类型
            ExifInterface.TAG_GAIN_CONTROL,       // 增益控制
            ExifInterface.TAG_CONTRAST,           // 对比度
            ExifInterface.TAG_SATURATION,         // 饱和度
            ExifInterface.TAG_SHARPNESS,          // 锐度
            ExifInterface.TAG_SUBJECT_DISTANCE_RANGE, // 主体距离范围
            
            // 其他技术参数
            ExifInterface.TAG_EXIF_VERSION,       // EXIF版本
            ExifInterface.TAG_FLASHPIX_VERSION,   // FlashPix版本
            ExifInterface.TAG_SUBJECT_AREA,       // 主体区域
            ExifInterface.TAG_SUBJECT_LOCATION,   // 主体位置
            ExifInterface.TAG_EXPOSURE_INDEX,     // 曝光指数
            ExifInterface.TAG_SENSING_METHOD,     // 感测方法
            ExifInterface.TAG_FILE_SOURCE,        // 文件来源
            ExifInterface.TAG_SCENE_TYPE,         // 场景类型
            ExifInterface.TAG_CFA_PATTERN,        // CFA模式
            ExifInterface.TAG_CUSTOM_RENDERED,    // 自定义渲染
            ExifInterface.TAG_EXPOSURE_MODE,      // 曝光模式
            ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, // 焦平面X分辨率
            ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, // 焦平面Y分辨率
            ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, // 焦平面分辨率单位
            ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE, // 空间频率响应
            ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION, // 设备设置描述
            ExifInterface.TAG_IMAGE_UNIQUE_ID,    // 图像唯一ID
            ExifInterface.TAG_CAMERA_OWNER_NAME,  // 相机所有者姓名
            ExifInterface.TAG_BODY_SERIAL_NUMBER, // 机身序列号
            
            // 用户注释
            ExifInterface.TAG_USER_COMMENT,       // 用户注释
            ExifInterface.TAG_MAKER_NOTE,         // 制造商注释
            ExifInterface.TAG_RELATED_SOUND_FILE, // 相关声音文件
            
            // GPS信息
            ExifInterface.TAG_GPS_LATITUDE,       // GPS纬度
            ExifInterface.TAG_GPS_LONGITUDE,      // GPS经度
            ExifInterface.TAG_GPS_LATITUDE_REF,   // GPS纬度参考
            ExifInterface.TAG_GPS_LONGITUDE_REF,  // GPS经度参考
            ExifInterface.TAG_GPS_ALTITUDE,       // GPS海拔
            ExifInterface.TAG_GPS_ALTITUDE_REF,   // GPS海拔参考
            ExifInterface.TAG_GPS_TIMESTAMP,      // GPS时间戳
            ExifInterface.TAG_GPS_DATESTAMP,      // GPS日期戳
            ExifInterface.TAG_GPS_PROCESSING_METHOD, // GPS处理方法
            ExifInterface.TAG_GPS_AREA_INFORMATION, // GPS区域信息
            ExifInterface.TAG_GPS_DIFFERENTIAL,   // GPS差分
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR // GPS水平定位误差
        )
    }
    
    /**
     * 复制EXIF标签
     */
    private fun copyExifTags(sourceExif: ExifInterface, targetExif: ExifInterface, tags: Array<String>): CopyResult {
        var copiedCount = 0
        var skippedCount = 0
        
        tags.forEach { tag ->
            try {
                val value = sourceExif.getAttribute(tag)
                if (value != null && value.isNotEmpty()) {
                    targetExif.setAttribute(tag, value)
                    copiedCount++
                    Log.v(TAG, "复制EXIF标签: $tag = $value")
                } else {
                    skippedCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "复制EXIF标签失败: $tag", e)
            }
        }
        
        return CopyResult(copiedCount, skippedCount)
    }
    
    /**
     * 验证EXIF数据是否正确保存
     */
    fun verifyExifPreservation(originalFile: File, processedFile: File): Boolean {
        return try {
            val originalExif = ExifInterface(originalFile.absolutePath)
            val processedExif = ExifInterface(processedFile.absolutePath)
            
            // 检查关键的时间标签
            val originalDateTime = originalExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            val processedDateTime = processedExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            
            val originalMake = originalExif.getAttribute(ExifInterface.TAG_MAKE)
            val processedMake = processedExif.getAttribute(ExifInterface.TAG_MAKE)
            
            val originalModel = originalExif.getAttribute(ExifInterface.TAG_MODEL)
            val processedModel = processedExif.getAttribute(ExifInterface.TAG_MODEL)
            
            Log.d(TAG, "EXIF验证结果:")
            Log.d(TAG, "  原始拍摄时间: $originalDateTime")
            Log.d(TAG, "  处理后拍摄时间: $processedDateTime")
            Log.d(TAG, "  原始相机品牌: $originalMake")
            Log.d(TAG, "  处理后相机品牌: $processedMake")
            Log.d(TAG, "  原始相机型号: $originalModel")
            Log.d(TAG, "  处理后相机型号: $processedModel")
            
            // 检查关键信息是否保留
            val timePreserved = originalDateTime == processedDateTime
            val makePreserved = originalMake == processedMake
            val modelPreserved = originalModel == processedModel
            
            val isSuccess = timePreserved && makePreserved && modelPreserved
            
            Log.d(TAG, "EXIF保留状态: ${if (isSuccess) "成功" else "失败"}")
            if (!timePreserved) Log.w(TAG, "  - 拍摄时间未正确保留")
            if (!makePreserved) Log.w(TAG, "  - 相机品牌未正确保留")
            if (!modelPreserved) Log.w(TAG, "  - 相机型号未正确保留")
            
            isSuccess
            
        } catch (e: Exception) {
            Log.e(TAG, "EXIF验证失败", e)
            false
        }
    }
    
    /**
     * 调试：打印原始文件的EXIF信息
     */
    fun debugOriginalExif(sourceFile: File) {
        try {
            val exif = ExifInterface(sourceFile.absolutePath)
            
            Log.d(TAG, "=== 原始文件EXIF信息 ===")
            Log.d(TAG, "文件: ${sourceFile.name}")
            
            // 检查所有重要的时间标签
            val tags = arrayOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                ExifInterface.TAG_SUBSEC_TIME,
                ExifInterface.TAG_SUBSEC_TIME_DIGITIZED
            )
            
            tags.forEach { tag ->
                val value = exif.getAttribute(tag)
                Log.d(TAG, "$tag: $value")
            }
            
            // 检查相机信息
            Log.d(TAG, "相机品牌: ${exif.getAttribute(ExifInterface.TAG_MAKE)}")
            Log.d(TAG, "相机型号: ${exif.getAttribute(ExifInterface.TAG_MODEL)}")
            
        } catch (e: Exception) {
            Log.e(TAG, "调试EXIF信息失败", e)
        }
    }
    
    /**
     * 简化的元数据处理方法（备用方案）
     */
    fun processMetadataSimple(sourceFile: File, compressedBitmap: Bitmap, outputFile: File): Boolean {
        return try {
            Log.d(TAG, "使用简化方法处理元数据: ${sourceFile.name}")
            
            // 直接保存压缩后的位图
            saveCompressedImage(compressedBitmap, outputFile)
            
            // 记录元数据处理信息
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
     * EXIF复制结果数据类
     */
    private data class CopyResult(
        val copiedCount: Int,
        val skippedCount: Int
    )
} 