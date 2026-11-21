package com.prism.gallery.di

import android.content.Context
import androidx.room.Room
import com.prism.gallery.data.db.GalleryDao
import com.prism.gallery.data.db.GalleryDb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext c: Context): GalleryDb = Room.databaseBuilder(c, GalleryDb::class.java, "gallery.db").build()

    @Provides fun dao(db: GalleryDb): GalleryDao = db.dao()
}
