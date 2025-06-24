package eu.tutorials.drawingapp

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import eu.tutorials.drawingapp.ui.theme.DrawingAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrawingAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val displayMetrics = context.resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels
    val screenHeight = displayMetrics.heightPixels

    // --- STATE MANAGEMENT ---
    val imageUri = remember { mutableStateOf<Uri?>(null) }
    val backgroundImageBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val color = remember { mutableStateOf(Color.Black) }
    val paths = remember { mutableStateListOf<PathData>() }
    val showBrushDialog = remember { mutableStateOf(false) }
    val strokeWidth = remember { mutableFloatStateOf(5f) }

    // --- LOGIC LƯU ẢNH ---
    val saveDrawingAction = {
        scope.launch {
            Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show()
            val bitmapToSave = createBitmapFromDrawing(
                backgroundImage = backgroundImageBitmap.value,
                paths = paths,
                width = screenWidth,
                height = screenHeight
            )
            val uri = saveBitmapToGallery(context, bitmapToSave)
            withContext(Dispatchers.Main) {
                if (uri != null) {
                    Toast.makeText(context, "Image saved to Gallery", Toast.LENGTH_SHORT).show()
                    shareImage(context, uri) // 👈 Gọi share ngay sau khi lưu
                } else {
                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }

    // --- LAUNCHERS ---
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri.value = uri
    }

    val readPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Read permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                saveDrawingAction()
            } else {
                Toast.makeText(context, "Write permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // --- SIDE EFFECTS ---
    LaunchedEffect(imageUri.value) {
        imageUri.value?.let {
            try {
                val newBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                backgroundImageBitmap.value = newBitmap
            } catch (e: Exception) {
                Log.e("ImageDecoder", "Error decoding image", e)
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- UI ---
    Box(modifier = Modifier.fillMaxSize()) {
        DrawingScreen(
            color = color.value,
            strokeWidth = strokeWidth.floatValue,
            paths = paths,
            backgroundImage = backgroundImageBitmap.value?.asImageBitmap() ?: ImageBitmap(1, 1)
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp)
                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorButton(Color.Black) { color.value = Color.Black }
                ColorButton(Color.Red) { color.value = Color.Red }
                ColorButton(Color.Green) { color.value = Color.Green }
                ColorButton(Color.Yellow) { color.value = Color.Yellow }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp)
                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolButton(Icons.Default.Image) {
                    val permission =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
                    readPermissionLauncher.launch(permission)
                }
                ToolButton(Icons.Default.Undo) {
                    paths.removeLastOrNull()
                }
                ToolButton(Icons.Default.Brush) {
                    showBrushDialog.value = true
                }
                ToolButton(Icons.Default.Save) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveDrawingAction()
                    } else {
                        when (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )) {
                            PackageManager.PERMISSION_GRANTED -> saveDrawingAction()
                            else -> writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                }
            }
        }
    }

    if (showBrushDialog.value) {
        BrushSizeChooserDialog(
            onDismissRequest = { showBrushDialog.value = false },
            onSizeSelected = { newSize ->
                strokeWidth.floatValue = newSize
                showBrushDialog.value = false
            }
        )
    }
}

//--- COMPOSABLES ---

@Composable
fun BrushSizeChooserDialog(onDismissRequest: () -> Unit, onSizeSelected: (Float) -> Unit) {
    val brushSizes = listOf(5f, 10f, 20f, 30f)
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                brushSizes.forEach { size ->
                    IconButton(
                        modifier = Modifier.size(48.dp),
                        onClick = { onSizeSelected(size) }) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.Black,
                                radius = size / 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorButton(color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(color, shape = CircleShape)
            .border(width = 1.dp, color = Color.LightGray, shape = CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
fun ToolButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = null, tint = Color.Black) }
}

@Composable
fun DrawingScreen(
    color: Color,
    strokeWidth: Float,
    paths: MutableList<PathData>,
    backgroundImage: ImageBitmap
) {
    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(color, strokeWidth) {
            detectDragGestures(
                onDragStart = { offset ->
                    paths.add(
                        PathData(
                            path = Path().apply { moveTo(offset.x, offset.y) },
                            strokeWidth = strokeWidth,
                            color = color
                        )
                    )
                },
                onDrag = { change, _ ->
                    // Sửa lỗi không vẽ trực tiếp
                    // Bằng cách tạo bản sao và thay thế phần tử cuối, chúng ta kích hoạt recomposition
                    val currentPathData = paths.lastOrNull() ?: return@detectDragGestures
                    val updatedPath = Path().apply {
                        addPath(currentPathData.path)
                        lineTo(change.position.x, change.position.y)
                    }
                    paths[paths.size - 1] = currentPathData.copy(path = updatedPath)
                }
            )
        }) {

        if (backgroundImage.width > 1 && backgroundImage.height > 1) {
            val srcWidth = backgroundImage.width.toFloat()
            val srcHeight = backgroundImage.height.toFloat()
            val dstWidth = size.width
            val dstHeight = size.height
            val scaleX = dstWidth / srcWidth
            val scaleY = dstHeight / srcHeight
            val scale = min(scaleX, scaleY) // Tỷ lệ "Fit"
            val scaledWidth = srcWidth * scale
            val scaledHeight = srcHeight * scale
            val offsetX = (dstWidth - scaledWidth) / 2f
            val offsetY = (dstHeight - scaledHeight) / 2f

            drawImage(
                image = backgroundImage,
                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                dstSize = IntSize(scaledWidth.roundToInt(), scaledHeight.roundToInt())
            )
        }

        paths.forEach { pathData ->
            drawPath(
                path = pathData.path,
                color = pathData.color,
                style = Stroke(width = pathData.strokeWidth, cap = pathData.cap)
            )
        }
    }
}

//--- DATA CLASS ---

data class PathData(
    val path: Path,
    val color: Color = Color.Black,
    val strokeWidth: Float = 5f,
    val cap: StrokeCap = StrokeCap.Round
)

//--- HELPER FUNCTIONS FOR SAVING ---

fun createBitmapFromDrawing(
    backgroundImage: Bitmap?,
    paths: List<PathData>,
    width: Int,
    height: Int
): Bitmap {
    val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultBitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    backgroundImage?.let { bg ->
        val srcWidth = bg.width.toFloat()
        val srcHeight = bg.height.toFloat()
        val dstWidth = width.toFloat()
        val dstHeight = height.toFloat()
        val scaleX = dstWidth / srcWidth
        val scaleY = dstHeight / srcHeight
        val scale = min(scaleX, scaleY)
        val scaledWidth = srcWidth * scale
        val scaledHeight = srcHeight * scale
        val left = (dstWidth - scaledWidth) / 2
        val top = (dstHeight - scaledHeight) / 2
        val dstRect = android.graphics.RectF(left, top, left + scaledWidth, top + scaledHeight)
        canvas.drawBitmap(bg, null, dstRect, null)
    }

    val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    paths.forEach { pathData ->
        paint.color = pathData.color.toArgb()
        paint.strokeWidth = pathData.strokeWidth
        paint.strokeCap = when (pathData.cap) {
            StrokeCap.Butt -> Paint.Cap.BUTT
            StrokeCap.Round -> Paint.Cap.ROUND
            StrokeCap.Square -> Paint.Cap.SQUARE
            else -> Paint.Cap.ROUND
        }
        canvas.drawPath(pathData.path.asAndroidPath(), paint)
    }
    return resultBitmap
}

suspend fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    displayName: String = "DrawingApp_${System.currentTimeMillis()}.png"
): Uri? {
    return withContext(Dispatchers.IO) {
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(imageCollection, contentValues)
        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        throw IOException("Failed to save bitmap.")
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
                return@withContext it
            } catch (e: Exception) {
                resolver.delete(it, null, null)
                Log.e("SaveImage", "Failed", e)
            }
        }
        return@withContext null
    }
}

// hàm chia sẻ ảnh, (phải lưu vào cache mới chia sẻ được)
fun shareImage(context: Context, imageUri: Uri) {
    val shareIntent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
        type = "image/png"
        flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(
        android.content.Intent.createChooser(shareIntent, "Share Drawing")
    )
}
