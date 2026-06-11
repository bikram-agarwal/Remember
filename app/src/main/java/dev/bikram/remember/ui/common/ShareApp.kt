package dev.bikram.remember.ui.common

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.R

/**
 * Launches the system share chooser with the app's download link: the Play listing for
 * the playstore flavor, otherwise the latest GitHub release.
 */
@Composable
fun rememberShareAppAction(): () -> Unit {
    val context = LocalContext.current
    val githubRepoForSourceLink = BuildConfig.GITHUB_REPO.trim()
    val playStoreListingUrl = BuildConfig.PLAY_STORE_LISTING_URL
    val shareUrl =
        when {
            BuildConfig.FLAVOR == "playstore" -> playStoreListingUrl
            githubRepoForSourceLink.isNotEmpty() -> "https://github.com/$githubRepoForSourceLink/releases/latest"
            else -> playStoreListingUrl
        }
    val shareText = stringResource(R.string.about_share_text, shareUrl)
    val shareChooserTitle = stringResource(R.string.main_share_chooser_title)
    return remember(context, shareText, shareChooserTitle) {
        {
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
            context.startActivity(Intent.createChooser(send, shareChooserTitle))
        }
    }
}
