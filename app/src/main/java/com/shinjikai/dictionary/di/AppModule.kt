package com.shinjikai.dictionary.di

import android.content.Context
import com.shinjikai.dictionary.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideBookmarkDao(database: AppDatabase) = database.bookmarkDao()

    @Provides
    @Singleton
    fun provideYomitanDao(database: AppDatabase) = database.yomitanDao()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore {
        return SettingsStore(context)
    }

    @Provides
    @Singleton
    fun provideRecentSearchStore(@ApplicationContext context: Context): RecentSearchStore {
        return RecentSearchStore(context)
    }

    @Provides
    @Singleton
    fun provideBookmarkRepository(bookmarkDao: BookmarkDao, yomitanDao: YomitanDao): BookmarkRepository {
        return BookmarkRepository(bookmarkDao, yomitanDao)
    }
}
