package dev.bikram.remember.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.bikram.remember.update.GithubPlayInAppUpdateProgressController
import dev.bikram.remember.update.GithubPlayInAppUpdateStarter
import dev.bikram.remember.update.GithubPlayStoreUpdateChecker
import dev.bikram.remember.update.GithubPlayUpdateSession
import dev.bikram.remember.update.PlayInAppUpdateProgressController
import dev.bikram.remember.update.PlayInAppUpdateStarter
import dev.bikram.remember.update.PlayStoreUpdateChecker
import dev.bikram.remember.update.PlayUpdateSessionHandle
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GithubUpdateFlavorModule {
    @Binds
    @Singleton
    abstract fun bindPlayUpdateSession(handle: GithubPlayUpdateSession): PlayUpdateSessionHandle

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateStarter(starter: GithubPlayInAppUpdateStarter): PlayInAppUpdateStarter

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateProgressController(
        controller: GithubPlayInAppUpdateProgressController,
    ): PlayInAppUpdateProgressController

    @Binds
    @Singleton
    abstract fun bindPlayStoreUpdateChecker(checker: GithubPlayStoreUpdateChecker): PlayStoreUpdateChecker
}
