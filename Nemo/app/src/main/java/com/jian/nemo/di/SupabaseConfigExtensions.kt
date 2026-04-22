package com.jian.nemo.di

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.SupabaseClientBuilder

import io.ktor.client.plugins.websocket.WebSockets

@OptIn(SupabaseInternal::class)
fun SupabaseClientBuilder.applyHttpConfig() {
    httpConfig {
        install(WebSockets)
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 60000 // 60s
            connectTimeoutMillis = 60000
            socketTimeoutMillis = 60000
        }
    }
}
