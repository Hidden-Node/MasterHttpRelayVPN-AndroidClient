package com.masterhttprelay.vpn.di

import android.content.Context
import com.masterhttprelay.vpn.data.ConfigStore
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
    fun provideConfigStore(@ApplicationContext context: Context): ConfigStore {
        return ConfigStore(context)
    }
}
