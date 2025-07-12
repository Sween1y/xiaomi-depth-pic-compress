package com.sweeney.xiaomi_depth_pic_compress

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.*
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sweeney.xiaomi_depth_pic_compress.ui.theme.XiaomidepthpiccompressTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoViewModel by viewModels()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 权限结果会在UI中处理
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiaomidepthpiccompressTheme {
                PhotoApp(
                    viewModel = viewModel,
                    onRequestPermission = { permission ->
                        requestPermissionLauncher.launch(permission)
                    }
                )
            }
        }
    }
}

@Composable
fun PhotoApp(
    viewModel: PhotoViewModel,
    onRequestPermission: (String) -> Unit
) {
    val context = LocalContext.current
    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // 监听权限变化
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            onRequestPermission(readPermission)
        }
    }
    
    // 定期检查权限状态
    LaunchedEffect(Unit) {
        while (true) {
            val currentPermission = ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED
            if (currentPermission != hasPermission) {
                hasPermission = currentPermission
            }
            kotlinx.coroutines.delay(500) // 每500ms检查一次
        }
    }
    
    if (hasPermission) {
        PhotoScreen(viewModel = viewModel)
    } else {
        PermissionScreen(
            onRequestPermission = { onRequestPermission(readPermission) }
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
                    text = "此应用需要访问您的相册来扫描包含深度信息的照片",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScreen(viewModel: PhotoViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scannedPhotos by viewModel.scannedPhotos.collectAsStateWithLifecycle()
    val selectedPhotos by viewModel.selectedPhotos.collectAsStateWithLifecycle()
    val removedPhotos by viewModel.removedPhotos.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小米深度照片压缩") },
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
                selectedCount = selectedPhotos.size,
                totalCount = scannedPhotos.size
            )
            
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
                is PhotoUiState.ScanComplete -> {
                    PhotoGrid(
                        photos = scannedPhotos.filter { it.uri !in removedPhotos },
                        selectedPhotos = selectedPhotos,
                        removedPhotos = removedPhotos,
                        onPhotoClick = { photo ->
                            if (photo.uri in removedPhotos) {
                                viewModel.restorePhoto(photo.uri)
                            } else {
                                viewModel.removePhoto(photo.uri)
                            }
                        }
                    )
                }
                is PhotoUiState.Processing -> {
                    ProcessingState()
                }
                is PhotoUiState.ProcessProgress -> {
                    ProcessProgressState(uiState as PhotoUiState.ProcessProgress)
                }
                is PhotoUiState.Success -> {
                    SuccessState(uiState as PhotoUiState.Success)
                }
                is PhotoUiState.Error -> {
                    ErrorState(uiState as PhotoUiState.Error)
                }
            }
        }
    }
}

@Composable
fun ControlButtons(
    uiState: PhotoUiState,
    onScanClick: () -> Unit,
    onProcessClick: () -> Unit,
    selectedCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                    enabled = uiState !is PhotoUiState.Scanning && uiState !is PhotoUiState.Processing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("扫描照片")
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Button(
                    onClick = onProcessClick,
                    enabled = selectedCount > 0 && uiState !is PhotoUiState.Scanning && uiState !is PhotoUiState.Processing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("处理照片 ($selectedCount)")
                }
            }
            
            if (totalCount > 0) {
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                progress = progress.current.toFloat() / progress.total.toFloat()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("扫描进度: ${progress.current}/${progress.total}")
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
fun ProcessProgressState(progress: PhotoUiState.ProcessProgress) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                progress = progress.current.toFloat() / progress.total.toFloat()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("处理进度: ${progress.current}/${progress.total}")
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
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isRemoved) Color.Red else if (isSelected) Color.Blue else Color.Gray,
                shape = RoundedCornerShape(8.dp)
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

