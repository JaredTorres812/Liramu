package com.applemusic.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class AppleMusicApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.28)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_art_cache"))
                    .maxSizeBytes(160L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(160)
            .build()
}
