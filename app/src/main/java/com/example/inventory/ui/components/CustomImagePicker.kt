package com.example.inventory.ui.components

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.ui.res.stringResource
import coil.compose.rememberAsyncImagePainter
import com.example.inventory.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaImage(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long
)

class MediaPagingSource(
    private val contentResolver: ContentResolver
) : PagingSource<Int, MediaImage>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaImage> {
        val offset = params.key ?: 0
        val pageSize = params.loadSize

        return try {
            val images = withContext(Dispatchers.IO) {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_ADDED
                )
                
                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bundle = Bundle().apply {
                        putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize)
                        putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
                        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    }
                    contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        bundle,
                        null
                    )
                } else {
                    // 对于 Android Q (29) 及以下，使用传统的 SQL 拼接方式
                    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $pageSize OFFSET $offset"
                    contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        sortOrder
                    )
                }

                val result = mutableListOf<MediaImage>()
                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    
                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        val dateAdded = it.getLong(dateColumn)
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        result.add(MediaImage(id, uri, dateAdded))
                    }
                }
                result
            }

            LoadResult.Page(
                data = images,
                prevKey = if (offset == 0) null else maxOf(0, offset - pageSize),
                nextKey = if (images.size < pageSize) null else offset + images.size
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaImage>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(state.config.pageSize)
        }
    }
}

@Composable
fun CustomImagePicker(
    onDismiss: () -> Unit,
    onImagesSelected: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val selectedUris = remember { mutableStateListOf<Uri>() }
    var previewImage by remember { mutableStateOf<MediaImage?>(null) }
    
    val pager = remember {
        Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false,
                initialLoadSize = 50
            ),
            pagingSourceFactory = { MediaPagingSource(context.contentResolver) }
        )
    }
    
    val lazyPagingItems = pager.flow.collectAsLazyPagingItems()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.select_images_count, selectedUris.size),
                    style = MaterialTheme.typography.titleLarge
                )
                Row {
                    if (selectedUris.isNotEmpty()) {
                        Button(onClick = { onImagesSelected(selectedUris) }) {
                            Text(stringResource(R.string.done))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    count = lazyPagingItems.itemCount,
                    key = { index -> 
                        lazyPagingItems.peek(index)?.id ?: index 
                    }
                ) { index ->
                    val image = lazyPagingItems[index]
                    if (image != null) {
                        ImageItem(
                            image = image,
                            isSelected = selectedUris.contains(image.uri),
                            onSelect = {
                                if (selectedUris.contains(image.uri)) {
                                    selectedUris.remove(image.uri)
                                } else {
                                    selectedUris.add(image.uri)
                                }
                            },
                            onLongPress = { previewImage = image }
                        )
                    } else {
                        // Placeholder
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(Color.LightGray)
                        )
                    }
                }
            }
        }
    }

    // Preview Dialog
    previewImage?.let { image ->
        Dialog(
            onDismissRequest = { previewImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(image.uri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    // Check (Select)
                    IconButton(
                        onClick = {
                            if (!selectedUris.contains(image.uri)) {
                                selectedUris.add(image.uri)
                            }
                            previewImage = null
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Green, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Select", tint = Color.White)
                    }

                    // X (Cancel/Close Preview)
                    IconButton(
                        onClick = { previewImage = null },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageItem(
    image: MediaImage,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSelect() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = image.uri,
                contentScale = ContentScale.Crop
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
