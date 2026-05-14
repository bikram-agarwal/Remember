package dev.bikram.remember.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.bikram.remember.update.AppReviewLauncher
import dev.bikram.remember.update.PlayInAppUpdateProgressController
import dev.bikram.remember.update.PlayInAppUpdateSession
import dev.bikram.remember.update.PlayInAppUpdateStarter
import dev.bikram.remember.update.PlayStoreAppReviewLauncher
import dev.bikram.remember.update.PlayStorePlayInAppUpdateCoordinator
import dev.bikram.remember.update.PlayStoreUpdateChecker
import dev.bikram.remember.update.PlayStoreUpdateCheckerImpl
import dev.bikram.remember.update.PlayUpdateSessionHandle
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayStoreUpdateFlavorModule {
    @Binds
    @Singleton
    abstract fun bindPlayUpdateSession(handle: PlayInAppUpdateSession): PlayUpdateSessionHandle

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateStarter(starter: PlayStorePlayInAppUpdateCoordinator): PlayInAppUpdateStarter

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateProgressController(
        controller: PlayStorePlayInAppUpdateCoordinator,
    ): PlayInAppUpdateProgressController

    @Binds
    @Singleton
    abstract fun bindPlayStoreUpdateChecker(checker: PlayStoreUpdateCheckerImpl): PlayStoreUpdateChecker

    @Binds
    @Singleton
    abstract fun bindAppReviewLauncher(launcher: PlayStoreAppReviewLauncher): AppReviewLauncher
}
