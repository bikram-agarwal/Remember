// Google Tasks import requires proprietary Google Play Services + Credentials libraries.
// These coordinates are deliberately NOT in gradle/libs.versions.toml: the F-Droid scanner
// resolves `libs.*` catalog accessors and flags com.google.android.gms / *play-services as
// non-free, which fails the fdroid build. Keeping them as hardcoded strings here lets the
// fdroid recipe `scandelete` this whole file (and src/nonfdroid) so the scanner never sees
// them, while the github/playstore flavors still get the real implementation.
//
// TRADEOFF: because they're outside the version catalog, `versionCatalogUpdate` will NOT bump
// them. `./gradlew checkDependencyUpdates` still REPORTS newer versions for them (they're live
// on github/playstoreImplementation on a normal checkout) — but you must apply any bump by
// editing the version literals below by hand.
val playServicesAuthVersion = "21.6.0"
val androidxCredentialsVersion = "1.6.0"

dependencies {
    // Google account picker + OAuth token mint for Google Tasks import.
    // play-services-auth provides Identity Services (modern picker + Authorization API).
    // androidx.credentials provides clearCredentialState() for explicit Disconnect cleanup.
    add("githubImplementation", "com.google.android.gms:play-services-auth:$playServicesAuthVersion")
    add("githubImplementation", "androidx.credentials:credentials:$androidxCredentialsVersion")
    add("githubImplementation", "androidx.credentials:credentials-play-services-auth:$androidxCredentialsVersion")
    add("playstoreImplementation", "com.google.android.gms:play-services-auth:$playServicesAuthVersion")
    add("playstoreImplementation", "androidx.credentials:credentials:$androidxCredentialsVersion")
    add("playstoreImplementation", "androidx.credentials:credentials-play-services-auth:$androidxCredentialsVersion")
}
