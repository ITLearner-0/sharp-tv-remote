package io.github.itlearner0.tvremote.brand

import io.github.itlearner0.tvremote.data.TvApp

/** Applications courantes des TV Samsung et LG, avec leurs identifiants. */
object BrandApps {
    fun list(brand: Brand): List<TvApp> = when (brand) {
        Brand.SAMSUNG -> listOf(
            TvApp("3201907018807", "Netflix"),
            TvApp("111299001912", "YouTube"),
            TvApp("3201910019365", "Prime Video"),
            TvApp("3201901017640", "Disney+"),
            TvApp("3201807016597", "Apple TV"),
            TvApp("3201601007250", "Google Play Films"),
            TvApp("3201606009684", "Spotify"),
            TvApp("org.tizen.browser", "Internet"),
        )
        Brand.LG -> listOf(
            TvApp("netflix", "Netflix"),
            TvApp("youtube.leanback.v4", "YouTube"),
            TvApp("amazon", "Prime Video"),
            TvApp("com.disney.disneyplus-prod", "Disney+"),
            TvApp("com.apple.appletv", "Apple TV"),
            TvApp("spotify-beehive", "Spotify"),
            TvApp("com.webos.app.livetv", "TV"),
            TvApp("com.webos.app.browser", "Internet"),
        )
    }
}
