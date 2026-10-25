package com.ayush.circleguard

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform