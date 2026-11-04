// framework ExifInterface only reads the orientation tag here; avoids adding androidx.exifinterface
@file:SuppressLint("ExifInterface")

package com.yshalsager.shizukushortcuts

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.ThumbnailUtils
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.UUID

object ShortcutIcons {
    private val picker_intents = listOf(
        Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
        Intent("com.novalauncher.THEME").addCategory("com.novalauncher.category.CUSTOM_ICON_PICKER")
    )
    // Kvaesitso and most launchers only require one of these plus res/xml/drawable.xml, no picker activity
    private val pack_intents = listOf(
        Intent("org.adw.ActivityStarter.THEMES"),
        Intent("org.adw.launcher.THEMES"),
        Intent("com.novalauncher.THEME")
    ) + picker_intents

    // action ids can come from user backups, so never use them as raw file names
    private fun file(context: Context, action_id: String) =
        File(context.filesDir, "shortcut_icons/${UUID.nameUUIDFromBytes(action_id.toByteArray())}.png")

    fun has(context: Context, action_id: String) = file(context, action_id).exists()

    fun icon(context: Context, action_id: String, @DrawableRes fallback_res: Int): IconCompat =
        BitmapFactory.decodeFile(file(context, action_id).path)?.let(IconCompat::createWithBitmap)
            ?: IconCompat.createWithResource(context, fallback_res)

    // write then rename so the background shortcut sync never decodes a half-written file
    fun save(context: Context, action_id: String, bitmap: Bitmap): Boolean = runCatching {
        val target = file(context, action_id)
        target.parentFile?.mkdirs()
        val temp = File(target.path + ".tmp")
        try {
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            temp.renameTo(target)
        } finally {
            temp.delete()
        }
    }.getOrDefault(false)

    fun delete(context: Context, action_id: String) = file(context, action_id).delete()

    fun prune(context: Context, keep_ids: Collection<String>) {
        val keep_names = keep_ids.mapTo(mutableSetOf()) { file(context, it).name }
        File(context.filesDir, "shortcut_icons").listFiles()?.filterNot { it.name in keep_names }?.forEach(File::delete)
    }

    fun packs(context: Context): List<ResolveInfo> = pack_intents
        .flatMap { context.packageManager.queryIntentActivities(it, 0) }
        .distinctBy { it.activityInfo.packageName }

    fun picker_intent(context: Context, package_name: String) = picker_intents.firstNotNullOfOrNull { intent ->
        context.packageManager.queryIntentActivities(Intent(intent).setPackage(package_name), 0).firstOrNull()
            ?.let { Intent(intent).setClassName(package_name, it.activityInfo.name) }
    }

    fun icon_names(context: Context, package_name: String): List<String> = runCatching {
        val res = context.packageManager.getResourcesForApplication(package_name)
        val xml_id = res.getIdentifier("drawable", "xml", package_name)
        val parser = if (xml_id != 0) res.getXml(xml_id)
        else XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("drawable.xml"), null) }
        buildList {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") parser.getAttributeValue(null, "drawable")?.let(::add)
            }
        }.distinct()
    }.getOrDefault(emptyList())

    fun pack_bitmap(context: Context, package_name: String, name: String, size: Int = ShortcutManagerCompat.getIconMaxWidth(context)) =
        runCatching {
            val res = context.packageManager.getResourcesForApplication(package_name)
            ResourcesCompat.getDrawable(res, res.getIdentifier(name, "drawable", package_name), null)?.toBitmap(size, size)
        }.getOrNull()

    // downsampled decode so large photos cannot exhaust memory
    fun image_bitmap(context: Context, uri: Uri): Bitmap? = runCatching {
        val size = ShortcutManagerCompat.getIconMaxWidth(context)
        fun <T> read(block: (InputStream) -> T) = context.contentResolver.openInputStream(uri)!!.use(block)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { options -> read { BitmapFactory.decodeStream(it, null, options) } }
        val options = BitmapFactory.Options().apply { inSampleSize = (minOf(bounds.outWidth, bounds.outHeight) / size).coerceAtLeast(1) }
        val bitmap = read { BitmapFactory.decodeStream(it, null, options) }!!
        val orientation = read { ExifInterface(it) }.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> setRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> setRotate(270f)
            }
            if (orientation == ExifInterface.ORIENTATION_TRANSPOSE || orientation == ExifInterface.ORIENTATION_TRANSVERSE) postScale(-1f, 1f)
        }
        Bitmap.createBitmap(ThumbnailUtils.extractThumbnail(bitmap, size, size), 0, 0, size, size, transform, true)
    }.getOrNull()

    fun picked_bitmap(context: Context, data: Intent): Bitmap? {
        val resource = IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
        // resourceName is fully qualified (pkg:drawable/name), which getIdentifier accepts as-is
        return resource?.let { pack_bitmap(context, it.packageName, it.resourceName) }
            ?: IntentCompat.getParcelableExtra(data, "icon", Bitmap::class.java)
    }
}
