package com.sweeney.xiaomi_depth_pic_compress

import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender

import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.xmp.XmpDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File

sealed class PhotoUiState {
    data object Idle : PhotoUiState() // 初始空闲状态
    data object Scanning : PhotoUiState() // 扫描中
    data class ScanProgress(val current: Int, val total: Int) : PhotoUiState() // 扫描进度
    data class ScanComplete(val photos: List<PhotoItem>) : PhotoUiState() // 扫描完成，显示符合条件的照片
    data object Processing : PhotoUiState() // 处理中 (压缩中)
    data class ProcessProgress(val current: Int, val total: Int, val currentFileName: String = "") : PhotoUiState() // 处理进度
    data class Success(val message: String) : PhotoUiState() // 操作成功 (例如压缩完成提示)
    data class Error(val message: String) : PhotoUiState() // 错误
}

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PhotoUiState>(PhotoUiState.Idle)
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    // 存储所有符合条件的照片，用于扫描完成后显示和筛选
    private val _scannedPhotos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val scannedPhotos: StateFlow<List<PhotoItem>> = _scannedPhotos.asStateFlow()

    // 存储用户选中的照片（用于处理的最终列表）
    private val _selectedPhotos = MutableStateFlow<Set<Uri>>(emptySet())
    val selectedPhotos: StateFlow<Set<Uri>> = _selectedPhotos.asStateFlow()

    // 存储用户暂时移除的照片 URI，这些照片不会出现在 UI 中，也不会被处理
    private val _removedPhotos = MutableStateFlow<Set<Uri>>(emptySet())
    val removedPhotos: StateFlow<Set<Uri>> = _removedPhotos.asStateFlow()

    // 存储压缩结果信息
    private val _compressionResults = MutableStateFlow<List<CompressionResult>>(emptyList())
    val compressionResults: StateFlow<List<CompressionResult>> = _compressionResults.asStateFlow()
    
    // 存储总节省空间
    private val _totalSavedSpace = MutableStateFlow(0L)
    val totalSavedSpace: StateFlow<Long> = _totalSavedSpace.asStateFlow()

    // 写入权限请求 (用于 RecoverableSecurityException，通常是修改文件时需要)
    val pendingWriteRequest = MutableStateFlow<IntentSender?>(null)

    // 扫描任务
    private var scanJob: Job? = null
    
    // 压缩器
    private val photoCompressor = PhotoCompressor(application)

    // 开始扫描照片
    fun startScan() {
        if (scanJob?.isActive == true) return
        
        scanJob = viewModelScope.launch {
            try {
                _uiState.value = PhotoUiState.Scanning
                val photos = scanPhotosWithDepthInfo()
                _scannedPhotos.value = photos
                _selectedPhotos.value = photos.map { it.uri }.toSet()
                _removedPhotos.value = emptySet()
                _uiState.value = PhotoUiState.ScanComplete(photos)
            } catch (e: Exception) {
                Log.e("PhotoViewModel", "扫描失败", e)
                _uiState.value = PhotoUiState.Error("扫描失败: ${e.message}")
            }
        }
    }

    // 移除照片
    fun removePhoto(uri: Uri) {
        _removedPhotos.update { it + uri }
        _selectedPhotos.update { it - uri }
    }

    // 恢复照片
    fun restorePhoto(uri: Uri) {
        _removedPhotos.update { it - uri }
        _selectedPhotos.update { it + uri }
    }

    // 开始处理照片
    fun startProcessing() {
        val photosToProcess = _scannedPhotos.value.filter { photo ->
            photo.uri in _selectedPhotos.value && photo.uri !in _removedPhotos.value
        }
        
        if (photosToProcess.isEmpty()) {
            _uiState.value = PhotoUiState.Error("没有选中的照片需要处理")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = PhotoUiState.Processing
                
                val compressionResults = mutableListOf<CompressionResult>()
                var totalSavedSpace = 0L
                
                for (i in photosToProcess.indices) {
                    val photo = photosToProcess[i]
                    
                    // 更新进度状态，显示当前处理的文件名
                    _uiState.value = PhotoUiState.ProcessProgress(i + 1, photosToProcess.size, "正在压缩: ${photo.name}")
                    
                    // 压缩照片
                    val result = photoCompressor.compressPhoto(photo.uri)
                    if (result != null) {
                        compressionResults.add(result)
                        totalSavedSpace += result.savedSpace
                        Log.d("PhotoViewModel", "成功压缩照片: ${photo.name}, 节省空间: ${formatFileSize(result.savedSpace)}")
                    } else {
                        Log.e("PhotoViewModel", "压缩照片失败: ${photo.name}")
                    }
                }
                
                // 更新压缩结果
                _compressionResults.value = compressionResults
                _totalSavedSpace.value = totalSavedSpace
                
                val successCount = compressionResults.size
                val totalCount = photosToProcess.size
                val savedSpaceGB = String.format("%.2f", totalSavedSpace / (1024.0 * 1024.0 * 1024.0))
                
                if (successCount == totalCount) {
                    _uiState.value = PhotoUiState.Success("成功压缩了 $successCount 张照片，总共节省了 ${savedSpaceGB}GB 空间")
                } else {
                    _uiState.value = PhotoUiState.Success("成功压缩了 $successCount/$totalCount 张照片，${totalCount - successCount} 张失败，总共节省了 ${savedSpaceGB}GB 空间")
                }
                
            } catch (e: Exception) {
                Log.e("PhotoViewModel", "处理失败", e)
                _uiState.value = PhotoUiState.Error("处理失败: ${e.message}")
            }
        }
    }

    // 扫描照片并检查深度信息
    private suspend fun scanPhotosWithDepthInfo(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val contentResolver = getApplication<Application>().contentResolver
        val photos = mutableListOf<PhotoItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE
        )

        // 只查询JPEG格式的照片
        val selection = "${MediaStore.Images.Media.MIME_TYPE} IN (?, ?) AND ${MediaStore.Images.Media.SIZE} > ?"
        val selectionArgs = arrayOf("image/jpeg", "image/jpg", (Constants.Compression.MIN_FILE_SIZE_MB * 1024 * 1024).toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { queryResult ->
            val idColumn = queryResult.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = queryResult.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = queryResult.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeTypeColumn = queryResult.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            var processedCount = 0
            val totalCount = queryResult.count

            Log.d("PhotoViewModel", "开始扫描，符合条件的照片总数: $totalCount")

            while (queryResult.moveToNext()) {
                processedCount++
                if (processedCount % 10 == 0) { // 减少进度更新频率，提高效率
                    _uiState.value = PhotoUiState.ScanProgress(processedCount, totalCount)
                }

                val id = queryResult.getLong(idColumn)
                val name = queryResult.getString(nameColumn)
                val size = queryResult.getLong(sizeColumn)
                val mimeType = queryResult.getString(mimeTypeColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                // 检查照片是否包含深度信息
                if (containsDepthInfo(contentResolver, contentUri)) {
                    photos.add(PhotoItem(id, contentUri, name, size))
                    Log.d("PhotoViewModel", "找到深度照片: $name (${formatFileSize(size)})")
                }
            }
        }

        Log.d("PhotoViewModel", "扫描完成，找到 ${photos.size} 张深度照片")
        photos
    }



    // 检查照片是否包含深度信息
    private fun containsDepthInfo(contentResolver: ContentResolver, uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bufferedInputStream = BufferedInputStream(inputStream)
                val metadata = ImageMetadataReader.readMetadata(bufferedInputStream)
                containsSpecificXmpInfo(metadata)
            } ?: false
        } catch (e: Exception) {
            Log.w("PhotoViewModel", "检查照片深度信息失败: $uri", e)
            false
        }
    }

    // 是否属于Xiaomi的深度信息
    private fun containsSpecificXmpInfo(metadata: Metadata): Boolean {
        val xmpDirectory = metadata.getFirstDirectoryOfType(XmpDirectory::class.java) ?: return false
        val xmpMeta = xmpDirectory.xmpMeta ?: return false

        val xiaomiImageNamespace = "http://ns.xiaomi.com/photos/1.0/camera/"
        val xiaomiXmpPropertyName = "MiCamera:XMPMeta"

        if (!xmpMeta.doesPropertyExist(xiaomiImageNamespace, xiaomiXmpPropertyName)) {
            return false
        }

        val propertyValue = xmpMeta.getPropertyString(xiaomiImageNamespace, xiaomiXmpPropertyName)
        if (propertyValue.contains("depthmap", ignoreCase = true)) {
            Log.d("PhotoViewModel", "Found Xiaomi Depth Effect via XMP property: $propertyValue")
            return true
        }
        return false
    }

    // 格式化文件大小
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)}MB"
            else -> String.format("%.2fGB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    // 清理压缩结果
    fun clearCompressionResults() {
        _compressionResults.value = emptyList()
        _totalSavedSpace.value = 0L
    }

    // 撤销压缩，删除所有压缩后的图片并清空压缩结果
    fun undoCompression() {
        val results = _compressionResults.value
        var deletedCount = 0
        for (result in results) {
            val file = File(result.compressedPath)
            if (FileUtils.deleteFileSafely(file)) {
                deletedCount++
            } else {
                Log.w(Constants.LogTags.PHOTO_VIEW_MODEL, "删除压缩文件失败: ${file.absolutePath}")
            }
        }
        _compressionResults.value = emptyList()
        _totalSavedSpace.value = 0L
        Log.d(Constants.LogTags.PHOTO_VIEW_MODEL, "撤销压缩，删除了 $deletedCount 个压缩文件")
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}