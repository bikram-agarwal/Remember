package dev.bikram.remember.ui.edit

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

data class ContactPick(
    val displayName: String,
    val data: String,
    val avatar: Drawable? = null,
)

@Composable
fun rememberPhonePickLauncher(onPicked: (ContactPick) -> Unit): ActivityResultLauncher<Intent> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri: Uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        resolver
            .query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                null,
                null,
                null,
            )?.use { cur ->
                if (cur.moveToFirst()) {
                    val name = cur.getString(0) ?: ""
                    val number = cur.getString(1) ?: ""
                    val avatar = cur.getString(2)?.let { resolver.drawableFromUri(it) }
                    onPicked(ContactPick(name, number, avatar))
                }
            }
    }
}

@Composable
fun rememberEmailPickLauncher(onPicked: (ContactPick) -> Unit): ActivityResultLauncher<Intent> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri: Uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        var name = ""
        var email = ""
        var photoUri = ""
        // Pull all columns the picker URI exposes. Different OEM Contacts apps return
        // slightly different schemas (some include the joined display_name on the
        // email row, some do not), so column-name fallbacks are more reliable than a
        // fixed projection. data1 is the email address.
        resolver.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                fun col(columnName: String): String {
                    val idx = cur.getColumnIndex(columnName)
                    return if (idx >= 0) cur.getString(idx) ?: "" else ""
                }
                name =
                    col(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
                        .ifBlank { col("display_name") }
                        .ifBlank { col("display_name_alt") }
                email =
                    col(ContactsContract.CommonDataKinds.Email.ADDRESS)
                        .ifBlank { col("data1") }
                photoUri =
                    col(ContactsContract.CommonDataKinds.Email.PHOTO_THUMBNAIL_URI)
                        .ifBlank { col("photo_thumb_uri") }
                if (name.isBlank()) name = email.substringBefore("@")
            }
        }
        if (email.isNotBlank()) onPicked(ContactPick(name, email, photoUri.takeIf { it.isNotBlank() }?.let { resolver.drawableFromUri(it) }))
    }
}

private fun android.content.ContentResolver.drawableFromUri(uri: String): Drawable? =
    runCatching {
        openInputStream(Uri.parse(uri))?.use { input ->
            Drawable.createFromStream(input, null)
        }
    }.getOrNull()

fun phonePickIntent(): Intent =
    // Phone CONTENT_TYPE (vnd.android.cursor.dir/phone_v2) is narrow enough that
    // only Contacts handles it on the user's device today, so we leave it as-is.
    Intent(Intent.ACTION_PICK).apply {
        type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

fun emailPickIntent(): Intent =
    // Use the Email content URI (content://com.android.contacts/data/emails) instead
    // of just setting type = vnd.android.cursor.dir/email_v2. Some installed apps
    // (file managers, app managers) declare intent filters for the broad
    // vnd.android.cursor.dir/* MIME prefix and were showing up in the chooser. The
    // explicit data URI restricts resolvers to those declaring filters for
    // com.android.contacts/data/emails, which is effectively just Contacts.
    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
