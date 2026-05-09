package dev.bikram.remember.data

import java.io.File

internal fun canonicalFileInsideBaseDirectoryOrNull(
    candidate: File,
    baseDirectory: File,
): File? {
    val canonicalBaseDirectory = baseDirectory.canonicalFile
    val canonicalCandidate = candidate.canonicalFile
    val baseDirectoryPath = canonicalBaseDirectory.path
    val candidatePath = canonicalCandidate.path
    if (candidatePath == baseDirectoryPath) {
        return canonicalCandidate
    }

    val baseDirectoryPathWithSeparator =
        if (baseDirectoryPath.endsWith(File.separator)) {
            baseDirectoryPath
        } else {
            baseDirectoryPath + File.separator
        }
    return if (candidatePath.startsWith(baseDirectoryPathWithSeparator)) {
        canonicalCandidate
    } else {
        null
    }
}
