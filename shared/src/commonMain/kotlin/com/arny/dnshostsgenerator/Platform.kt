package com.arny.dnshostsgenerator

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform