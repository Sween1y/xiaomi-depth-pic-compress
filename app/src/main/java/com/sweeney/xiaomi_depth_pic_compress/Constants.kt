package com.sweeney.xiaomi_depth_pic_compress

/**
 * 应用常量
 */
object Constants {
    
    /**
     * 压缩相关常量
     */
    object Compression {
        const val JPEG_QUALITY = 95
        const val COMPRESSED_FILE_SUFFIX = "_compressed"
        const val MIN_FILE_SIZE_MB = 5L // 最小文件大小（MB）
    }
    
    /**
     * 文件相关常量
     */
    object File {
        const val SUPPORTED_IMAGE_EXTENSIONS = "jpg,jpeg,png,gif,bmp,webp"
        const val OUTPUT_IMAGE_EXTENSION = "jpg"
    }
    
    /**
     * 扫描相关常量
     */
    object Scan {
        val PHOTO_DIRECTORIES = listOf("Pictures", "DCIM")
        const val XIAOMI_IMAGE_NAMESPACE = "http://ns.xiaomi.com/photos/1.0/camera/"
        const val XIAOMI_XMP_PROPERTY_NAME = "MiCamera:XMPMeta"
    }
    
    /**
     * 日志标签
     */
    object LogTags {
        const val PHOTO_COMPRESSOR = "PhotoCompressor"
        const val EXIF_METADATA_PROCESSOR = "ExifMetadataProcessor"
        const val PHOTO_VIEW_MODEL = "PhotoViewModel"
        const val FILE_UTILS = "FileUtils"
    }
    
    /**
     * 时间格式
     */
    object TimeFormat {
        const val EXIF_INPUT_FORMAT = "yyyy:MM:dd HH:mm:ss"
        const val FILE_NAME_FORMAT = "yyyyMMdd_HHmmss"
    }
} 