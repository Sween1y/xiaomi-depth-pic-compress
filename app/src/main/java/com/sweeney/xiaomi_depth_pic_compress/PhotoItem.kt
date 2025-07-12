package com.sweeney.xiaomi_depth_pic_compress

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long = 0L
)
