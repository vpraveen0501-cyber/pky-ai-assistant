package com.pkyai.android.di

import android.content.Context
import com.pkyai.android.AuthManager
import com.pkyai.android.data.network.PkyAiApiService
import com.pkyai.android.data.repository.ConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authManager: AuthManager,
        configRepository: ConfigRepository
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (com.pkyai.android.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            
            authManager.getToken()?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }
            chain.proceed(requestBuilder.build())
        }

        val dynamicUrlInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newBaseUrlStr = configRepository.getBaseUrl()
            val newBaseUrl = newBaseUrlStr.toHttpUrlOrNull()
            
            val newRequest = if (newBaseUrl != null) {
                com.pkyai.android.util.LocalLogger.d("Network", "Redirecting to $newBaseUrl")
                val newUrl = originalRequest.url.newBuilder()
                    .scheme(newBaseUrl.scheme)
                    .host(newBaseUrl.host)
                    .port(newBaseUrl.port)
                    .build()
                originalRequest.newBuilder().url(newUrl).build()
            } else {
                originalRequest
            }
            chain.proceed(newRequest)
        }
        
        // 10 MB Cache
        val cacheSize = 10 * 1024 * 1024L
        val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize)

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(dynamicUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, configRepository: ConfigRepository): Retrofit {
        return Retrofit.Builder()
            .baseUrl(configRepository.getBaseUrl() + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePkyAiApiService(retrofit: Retrofit): PkyAiApiService {
        return retrofit.create(PkyAiApiService::class.java)
    }
}
