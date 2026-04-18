package dev.bikram.remember.ui.edit

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

data class ContactPick(val displayName: String, val data: String)

@Composable
fun rememberPhonePickLauncher(onPicked: (ContactPick) -> Unit): ActivityResultLauncher<Intent> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri: Uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        resolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { cur ->
            if (cur.moveToFirst()) {
                val name = cur.getString(0) ?: ""
                val number = cur.getString(1) ?: ""
                onPicked(ContactPick(name, number))
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
        resolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
            ),
            null, null, null,
        )?.use { cur ->
            if (cur.moveToFirst()) {
                val name = cur.getString(0) ?: ""
                val email = cur.getString(1) ?: ""
                onPicked(ContactPick(name, email))
            }
        }
    }
}

fun phonePickIntent(): Intent =
    Intent(Intent.ACTION_PICK).apply {
        type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
    }

fun emailPickIntent(): Intent =
    Intent(Intent.ACTION_PICK).apply {
        type = ContactsContract.CommonDataKinds.Email.CONTENT_TYPE
    }
