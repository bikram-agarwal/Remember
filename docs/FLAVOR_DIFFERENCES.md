# Remember Flavor Differences

Remember is built using four product flavors under the `distribution` dimension: **`github`**, **`fdroid`**, **`playstore`**, and **`offline`**.

| Feature / Characteristic | GitHub Flavor (`github`) | F-Droid Flavor (`fdroid`) | Play Store Flavor (`playstore`) | Offline Flavor (`offline`) |
| :--- | :--- | :--- | :--- | :--- |
| **Application ID** | `dev.bikram.remember.gh` | `dev.bikram.remember.gh` | `dev.bikram.remember` | `dev.bikram.remember.offline` |
| **Side-by-Side Installation** | Replaces/blocks F-Droid (shares .gh ID). Can run alongside Play Store & Offline. | Replaces/blocks GitHub (shares .gh ID). Can run alongside Play Store & Offline. | Can run alongside all other flavors | Can run alongside all other flavors |
| **Internet permission** | Used for GitHub update checks, APK download, changelog fetch, and Google Tasks connect. | Used for F-Droid package API and changelog fetch | Used for Play in-app updates, changelog fetch, and Google Tasks connect. | No internet permission |
| **Update check source** | GitHub Release API for `bikram-agarwal/Remember` | F-Droid package API for the installed package ID | Google Play Core In-App Updates | None |
| **Update Action** | Downloads the release APK and launches the system package installer | Opens Remember in user's installed FOSS package client for the update. If no FOSS client handles that deep link, the app falls back to the app web page. | Starts the Play Core in-app update flow. | Track updates via ObtainX (a card on the Settings page; Settings → Updates on tablets), or install a newer offline APK from GitHub Releases manually. |
| **Tasks import** | Connect to Google account, plus Takeout / Tasks.org local file import. | Google Takeout / Tasks.org local file import only (no Google account connect). | Same as GitHub | Same as F-Droid |
| **Manifest Permissions** | Requests `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` | Same as GitHub | Requests `SCHEDULE_EXACT_ALARM` | Same as GitHub |
| **Save Update APK to Downloads** | Yes | No | No | No |
| **In-App Rating / Review** | Does not prompt for Play ratings | Does not prompt for Play ratings | Uses the Google Play In-App Review API with automated prompt | Does not prompt for Play ratings |
| **Cross-Promo Cards** | Shows FilePipe and ObtainX cards. Tapping opens each app's webpage. | Shows FilePipe and ObtainX cards. Tapping first tries `fdroid.app:<target_package_id>`, then falls back to the target app's webpage. | Shows FilePipe only. Tapping opens the FilePipe Play Store listing. | Same as GitHub |

---

## Backup Portability FAQ

### Are backups portable between the GitHub, F-Droid, Play Store, and Offline flavors?

**Yes, the backup files are fully portable between all four flavors.**

All flavors share the same data domain representation, Room database schema, and JSON serialization. Backup import (`org.json`) only reads the keys it knows, so flavor-specific preference fields are safely ignored by a flavor that does not use them.

> [!WARNING]
> **Package-scoped grants do not transfer across application IDs.**
>
> The GitHub and F-Droid flavors share `dev.bikram.remember.gh`. The Play Store flavor uses `dev.bikram.remember`. The Offline flavor uses `dev.bikram.remember.offline`. Switching to Offline (or from Offline to another package ID) means restoring a backup into a different app install; re-grant any permissions the new package needs after restore.
