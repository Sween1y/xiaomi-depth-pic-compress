package com.sweeney.xiaomi_depth_pic_compress

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sweeney.xiaomi_depth_pic_compress.ui.theme.XiaomidepthpiccompressTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoViewModel by viewModels()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("MainActivity", "权限请求结果: $isGranted")
        // 权限请求完成后，重新检查权限状态
        checkAndRequestNextPermission()
    }
    
    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "管理存储权限请求结果: ${result.resultCode}")
    }
    
    val deletePhotosLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("MainActivity", "照片删除成功")
            viewModel.onPhotosDeleted()
        } else {
            Log.d("MainActivity", "照片删除被取消或失败")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiaomidepthpiccompressTheme {
                PhotoApp(
                    viewModel = viewModel,
                    onRequestPermission = { permission ->
                        requestPermissionLauncher.launch(permission)
                    },
                    onRequestManageStorage = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = Uri.parse("package:$packageName")
                                requestManageStorageLauncher.launch(intent)
                            } catch (e: Exception) {
                                Log.w("MainActivity", "无法跳转到应用特定权限页面，尝试跳转到通用权限页面", e)
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                requestManageStorageLauncher.launch(intent)
                            }
                        }
                    },
                    onCheckAndRequestPermissions = {
                        checkAndRequestNextPermission()
                    }
                )
            }
        }
    }

    // 检查并请求下一个需要的权限
    private fun checkAndRequestNextPermission() {
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val mediaLocationPermission = Manifest.permission.ACCESS_MEDIA_LOCATION
        
        when {
            ContextCompat.checkSelfPermission(this, readPermission) != PackageManager.PERMISSION_GRANTED -> {
                Log.d("MainActivity", "请求读取权限: $readPermission")
                requestPermissionLauncher.launch(readPermission)
            }
            ContextCompat.checkSelfPermission(this, writePermission) != PackageManager.PERMISSION_GRANTED && 
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> {
                Log.d("MainActivity", "请求写入权限: $writePermission")
                requestPermissionLauncher.launch(writePermission)
            }
            ContextCompat.checkSelfPermission(this, mediaLocationPermission) != PackageManager.PERMISSION_GRANTED -> {
                Log.d("MainActivity", "请求媒体位置权限: $mediaLocationPermission")
                requestPermissionLauncher.launch(mediaLocationPermission)
            }
            else -> {
                Log.d("MainActivity", "所有权限已授予")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun PhotoApp(
    viewModel: PhotoViewModel,
    onRequestPermission: (String) -> Unit,
    onRequestManageStorage: () -> Unit,
    onCheckAndRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    val mediaLocationPermission = Manifest.permission.ACCESS_MEDIA_LOCATION
    
    var hasReadPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasWritePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, writePermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasMediaLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaLocationPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val hasAllPermissions = hasReadPermission && hasMediaLocationPermission && (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q || hasWritePermission)
    
    // 监听权限变化
    LaunchedEffect(Unit) {
        onCheckAndRequestPermissions()
    }
    
    // 定期检查权限状态
    LaunchedEffect(Unit) {
        while (true) {
            val currentReadPermission = ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED
            val currentWritePermission = ContextCompat.checkSelfPermission(context, writePermission) == PackageManager.PERMISSION_GRANTED
            val currentMediaLocationPermission = ContextCompat.checkSelfPermission(context, mediaLocationPermission) == PackageManager.PERMISSION_GRANTED
            
            if (currentReadPermission != hasReadPermission) {
                Log.d("PhotoApp", "读取权限状态变化: $hasReadPermission -> $currentReadPermission")
                hasReadPermission = currentReadPermission
            }
            if (currentWritePermission != hasWritePermission) {
                Log.d("PhotoApp", "写入权限状态变化: $hasWritePermission -> $currentWritePermission")
                hasWritePermission = currentWritePermission
            }
            if (currentMediaLocationPermission != hasMediaLocationPermission) {
                Log.d("PhotoApp", "媒体位置权限状态变化: $hasMediaLocationPermission -> $currentMediaLocationPermission")
                hasMediaLocationPermission = currentMediaLocationPermission
            }
            kotlinx.coroutines.delay(1000) // 每1秒检查一次
        }
    }
    
    if (hasAllPermissions) {
        PhotoScreen(
            viewModel = viewModel,
            onDeletePhotos = { intentSender ->
                (context as? MainActivity)?.deletePhotosLauncher?.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
        )
    } else {
        PermissionScreen(
            onRequestPermission = { 
                onCheckAndRequestPermissions()
            }
        )
    }
}

@Composable
fun PermissionScreen(
    onRequestPermission: () -> Unit
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "需要访问相册的权限才能工作",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "此应用需要访问您的相册来扫描包含深度信息的照片，并保存压缩后的照片",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRequestPermission) {
                    Text("授予权限")
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScreen(
    viewModel: PhotoViewModel,
    onDeletePhotos: (IntentSender) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scannedPhotos by viewModel.scannedPhotos.collectAsStateWithLifecycle()
    val selectedPhotos by viewModel.selectedPhotos.collectAsStateWithLifecycle()
    val removedPhotos by viewModel.removedPhotos.collectAsStateWithLifecycle()
    val compressionResults by viewModel.compressionResults.collectAsStateWithLifecycle()
    val totalSavedSpace by viewModel.totalSavedSpace.collectAsStateWithLifecycle()
    
    // 删除确认对话框状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小米人像照片压缩") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 控制按钮区域
            ControlButtons(
                uiState = uiState,
                onScanClick = { viewModel.startScan() },
                onProcessClick = { viewModel.startProcessing() },
                onSelectAllClick = { viewModel.selectAllPhotos() },
                onInvertSelectionClick = { viewModel.invertPhotoSelection() },
                onDeleteClick = { showDeleteDialog = true },
                selectedCount = selectedPhotos.size,
                totalCount = scannedPhotos.size,
                compressedCount = compressionResults.size,
                totalSavedSpace = totalSavedSpace
            )
            // 只要有压缩结果就显示绿色卡片，并且只显示一次
            if (compressionResults.isNotEmpty()) {
                CompressionResultsSection(
                    compressionResults = compressionResults,
                    totalSavedSpace = totalSavedSpace,
                    uiState = uiState,
                    onClearResults = { viewModel.undoCompression() }
                )
            }
            // 状态显示
            when (uiState) {
                is PhotoUiState.Idle -> {
                    IdleState()
                }
                is PhotoUiState.Scanning -> {
                    ScanningState()
                }
                is PhotoUiState.ScanProgress -> {
                    ScanProgressState(uiState as PhotoUiState.ScanProgress)
                }
                is PhotoUiState.Processing -> {
                    ProcessingState()
                }
                is PhotoUiState.ProcessProgress -> {
                    ProcessProgressState(
                        progress = uiState as PhotoUiState.ProcessProgress,
                        onCancel = { viewModel.cancelProcessing() }
                    )
                }
                is PhotoUiState.ScanComplete -> {
                    // 扫描完成，显示照片网格
                    PhotoGrid(
                        photos = scannedPhotos.filter { it.uri !in removedPhotos },
                        selectedPhotos = selectedPhotos,
                        removedPhotos = removedPhotos,
                        onPhotoClick = { photo ->
                            if (photo.uri in removedPhotos) {
                                viewModel.restorePhoto(photo.uri)
                            } else {
                                viewModel.togglePhotoSelection(photo.uri)
                            }
                        }
                    )
                }
                is PhotoUiState.Success -> {
                    // 处理完成，显示照片网格
                    PhotoGrid(
                        photos = scannedPhotos.filter { it.uri !in removedPhotos },
                        selectedPhotos = selectedPhotos,
                        removedPhotos = removedPhotos,
                        onPhotoClick = { photo ->
                            if (photo.uri in removedPhotos) {
                                viewModel.restorePhoto(photo.uri)
                            } else {
                                viewModel.togglePhotoSelection(photo.uri)
                            }
                        }
                    )
                }
                is PhotoUiState.Error -> {
                    ErrorState(uiState as PhotoUiState.Error)
                }
            }
        }
        
        // 删除确认对话框
        if (showDeleteDialog) {
            DeleteConfirmationDialog(
                selectedCount = selectedPhotos.size,
                onConfirm = {
                    showDeleteDialog = false
                    val intentSender = viewModel.deleteSelectedPhotos()
                    if (intentSender != null) {
                        onDeletePhotos(intentSender)
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun ControlButtons(
    uiState: PhotoUiState,
    onScanClick: () -> Unit,
    onProcessClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onInvertSelectionClick: () -> Unit,
    onDeleteClick: () -> Unit,
    selectedCount: Int,
    totalCount: Int,
    compressedCount: Int,
    totalSavedSpace: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onScanClick,
                    enabled = uiState !is PhotoUiState.Scanning && uiState !is PhotoUiState.Processing && uiState !is PhotoUiState.ProcessProgress,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp) // 添加统一的圆角
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("扫描照片")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = onProcessClick,
                    enabled = selectedCount > 0 && uiState !is PhotoUiState.Scanning && uiState !is PhotoUiState.Processing && uiState !is PhotoUiState.ProcessProgress,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp) // 添加统一的圆角
                ) {
                    Text("处理照片 ($selectedCount)")
                }
            }

            // 添加选择控制按钮
            if (totalCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onSelectAllClick,
                        enabled = uiState !is PhotoUiState.Scanning && uiState !is PhotoUiState.Processing && uiState !is PhotoUiState.ProcessProgress,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("全选")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedButton(
                        onClick = onInvertSelectionClick,
                        enabled = uiState !is PhotoUiState.Scanning && uiState !is PhotoUiState.Processing && uiState !is PhotoUiState.ProcessProgress,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("反选")
                    }
                }
                
                // 添加删除按钮
                if (selectedCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDeleteClick,
                        enabled = uiState !is PhotoUiState.Scanning && uiState !is PhotoUiState.Processing && uiState !is PhotoUiState.ProcessProgress,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除选中照片 ($selectedCount)")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "找到 $totalCount 张深度照片，选中 $selectedCount 张",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun IdleState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "点击扫描按钮开始查找深度照片",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ScanningState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("正在扫描照片...")
        }
    }
}

@Composable
fun ScanProgressState(progress: PhotoUiState.ScanProgress) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // 大号进度条
            CircularProgressIndicator(
                progress = {
                    progress.current.toFloat() / progress.total.toFloat()
                },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 百分比显示
            Text(
                text = "${((progress.current.toFloat() / progress.total.toFloat()) * 100).toInt()}%",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 进度文本
            Text(
                text = "扫描进度: ${progress.current}/${progress.total}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))

        }
    }
}

@Composable
fun ProcessingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("正在处理照片...")
        }
    }
}

@Composable
fun ProcessProgressState(
    progress: PhotoUiState.ProcessProgress,
    onCancel: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // 大号进度条
            CircularProgressIndicator(
                progress = {
                    progress.current.toFloat() / progress.total.toFloat()
                },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 百分比显示
            Text(
                text = "${((progress.current.toFloat() / progress.total.toFloat()) * 100).toInt()}%",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 进度文本
            Text(
                text = "处理进度: ${progress.current}/${progress.total}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            if (progress.currentFileName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = progress.currentFileName,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // 取消按钮
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("取消处理")
            }
        }
    }
}

@Composable
fun SuccessState(success: PhotoUiState.Success) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✓",
                fontSize = 48.sp,
                color = Color.Green
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = success.message,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ErrorState(error: PhotoUiState.Error) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✗",
                fontSize = 48.sp,
                color = Color.Red
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error.message,
                fontSize = 16.sp,
                color = Color.Red,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PhotoGrid(
    photos: List<PhotoItem>,
    selectedPhotos: Set<Uri>,
    removedPhotos: Set<Uri>,
    onPhotoClick: (PhotoItem) -> Unit
) {
    if (photos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "没有找到符合条件的照片",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
        return
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(photos) { photo ->
            PhotoItem(
                photo = photo,
                isSelected = photo.uri in selectedPhotos,
                isRemoved = photo.uri in removedPhotos,
                onClick = { onPhotoClick(photo) }
            )
        }
    }
}

@Composable
fun PhotoItem(
    photo: PhotoItem,
    isSelected: Boolean,
    isRemoved: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else if (isRemoved) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color.Red,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .size(200)
                .build(),
            contentDescription = photo.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        if (isRemoved) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "已移除",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // 选择状态指示器
        if (isSelected && !isRemoved) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = "已移除",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // 照片信息
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(4.dp)
        ) {
            Text(
                text = formatFileSize(photo.size),
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        else -> "${size / (1024 * 1024)}MB"
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun CompressionResultsSection(
    compressionResults: List<CompressionResult>,
    totalSavedSpace: Long,
    uiState: PhotoUiState,
    onClearResults: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            // 清理按钮
            Button(
                onClick = onClearResults,
                enabled = uiState !is PhotoUiState.Processing && uiState !is PhotoUiState.ProcessProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState is PhotoUiState.Processing || uiState is PhotoUiState.ProcessProgress) 
                        MaterialTheme.colorScheme.surfaceVariant 
                    else 
                        MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (uiState is PhotoUiState.Processing || uiState is PhotoUiState.ProcessProgress) 
                        MaterialTheme.colorScheme.onSurfaceVariant 
                    else 
                        MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .height(36.dp)
                    .width(100.dp)
            ) {
                Text(
                    if (uiState is PhotoUiState.Processing || uiState is PhotoUiState.ProcessProgress) "撤销中..." else "撤销",
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            Column(Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(
                    text = "已压缩 ${compressionResults.size} 张照片",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2ECC40)
                )
                val savedSpaceGB = String.format("%.2f", totalSavedSpace / (1024.0 * 1024.0 * 1024.0))
                Text(
                    text = "总共节省了 $savedSpaceGB GB 空间",
                    fontSize = 14.sp,
                    color = Color(0xFF2ECC40),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 100.dp)
                ) {
                    items(compressionResults.take(10)) { result ->
                        CompressionResultItem(result = result)
                    }
                }
                if (compressionResults.size > 10) {
                    Text(
                        text = "还有 ${compressionResults.size - 10} 张照片...",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun CompressionResultItem(result: CompressionResult) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = 1.dp,
                color = Color.Green,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(result.compressedPath)
                .size(100)
                .build(),
            contentDescription = "压缩后的照片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // 节省空间信息
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(2.dp)
        ) {
            Text(
                text = "-${formatFileSize(result.savedSpace)}",
                color = Color.Green,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "确认删除",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = "您即将删除 $selectedCount 张景深照片，此操作不可逆！\n\n删除后这些照片将无法恢复，请确认您真的要删除这些照片吗？",
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("确认删除")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.error,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

