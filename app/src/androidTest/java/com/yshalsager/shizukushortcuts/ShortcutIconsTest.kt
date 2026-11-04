package com.yshalsager.shizukushortcuts

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShortcutIconsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun saved_icon_replaces_default_until_deleted() {
        val action_id = "../../escape"
        ShortcutIcons.save(context, action_id, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))

        assertTrue(ShortcutIcons.has(context, action_id))
        assertFalse(File(context.filesDir.parentFile, "escape.png").exists())
        assertEquals(IconCompat.TYPE_BITMAP, ShortcutIcons.icon(context, action_id, R.drawable.ic_shortcut_custom_action).type)

        ShortcutIcons.delete(context, action_id)
        assertFalse(ShortcutIcons.has(context, action_id))
        assertEquals(IconCompat.TYPE_RESOURCE, ShortcutIcons.icon(context, action_id, R.drawable.ic_shortcut_custom_action).type)
    }

    @Test
    fun save_returns_false_when_storage_path_is_not_a_directory() {
        val blocked = File.createTempFile("blocked-icons", null, context.cacheDir)
        val blocked_context = object : ContextWrapper(context) {
            override fun getFilesDir() = blocked
        }
        try {
            assertFalse(ShortcutIcons.save(blocked_context, "action", Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)))
        } finally {
            blocked.delete()
        }
    }

    @Test
    fun picked_bitmap_reads_resource_then_bitmap_extra() {
        val resource = Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_shortcut_custom_action)
        assertNotNull(ShortcutIcons.picked_bitmap(context, Intent().putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, resource)))
        assertNotNull(ShortcutIcons.picked_bitmap(context, Intent().putExtra("icon", Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))))
    }

    @Test
    fun drawable_xml_items_are_listed_once_and_categories_skipped() {
        val pack = InstrumentationRegistry.getInstrumentation().context
        assertEquals(listOf("settings", "clock"), ShortcutIcons.icon_names(context, pack.packageName))
    }

    @Test
    fun prune_keeps_only_listed_ids() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        ShortcutIcons.save(context, "keep", bitmap)
        ShortcutIcons.save(context, "drop", bitmap)
        ShortcutIcons.prune(context, listOf("keep"))
        assertTrue(ShortcutIcons.has(context, "keep"))
        assertFalse(ShortcutIcons.has(context, "drop"))
        ShortcutIcons.delete(context, "keep")
    }

    @Test
    fun gallery_image_is_downsampled_square_and_rotated() {
        val size = ShortcutManagerCompat.getIconMaxWidth(context)
        // landscape image: red top half, blue bottom half, tagged as rotated 90 degrees by the camera
        val image = Bitmap.createBitmap(size * 8, size * 4, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawRect(0f, 0f, image.width.toFloat(), image.height / 2f, Paint().apply { color = Color.RED })
            drawRect(0f, image.height / 2f, image.width.toFloat(), image.height.toFloat(), Paint().apply { color = Color.BLUE })
        }
        val file = File(context.cacheDir, "landscape.jpg")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }

        val icon = ShortcutIcons.image_bitmap(context, Uri.fromFile(file))!!
        assertEquals(size, icon.width)
        assertEquals(size, icon.height)
        // rotating 90 degrees clockwise moves the red top to the right and the blue bottom to the left
        assertTrue(Color.blue(icon.getPixel(size / 10, size / 2)) > 200)
        assertTrue(Color.red(icon.getPixel(size * 9 / 10, size / 2)) > 200)
        file.delete()
    }

    @Test
    fun gallery_image_handles_all_exif_orientations() {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        colors.forEachIndexed { index, color ->
            val x = (index % 2) * 32f
            val y = (index / 2) * 32f
            canvas.drawRect(x, y, x + 32, y + 32, Paint().apply { this.color = color })
        }
        val expected = listOf(
            listOf(0, 1, 2, 3), listOf(1, 0, 3, 2), listOf(3, 2, 1, 0), listOf(2, 3, 0, 1),
            listOf(0, 2, 1, 3), listOf(2, 0, 3, 1), listOf(3, 1, 2, 0), listOf(1, 3, 0, 2)
        )
        val file = File.createTempFile("orientations", ".jpg", context.cacheDir)
        try {
            file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            expected.forEachIndexed { index, quadrants ->
                ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, (index + 1).toString()); saveAttributes() }
                val icon = ShortcutIcons.image_bitmap(context, Uri.fromFile(file))!!
                quadrants.forEachIndexed { quadrant, color_index ->
                    val pixel = icon.getPixel(icon.width * (1 + 2 * (quadrant % 2)) / 4, icon.height * (1 + 2 * (quadrant / 2)) / 4)
                    val expected_color = colors[color_index]
                    for (shift in listOf(0, 8, 16)) {
                        assertEquals("orientation ${index + 1}, quadrant $quadrant", ((expected_color shr shift) and 255).toDouble(), ((pixel shr shift) and 255).toDouble(), 15.0)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun pack_without_drawable_xml_has_no_icons_but_resources_still_load() {
        assertTrue(ShortcutIcons.icon_names(context, context.packageName).isEmpty())
        assertNotNull(ShortcutIcons.pack_bitmap(context, context.packageName, "ic_shortcut_custom_action"))
    }
}
