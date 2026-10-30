package co.gapfinder.mobile.foundation

import co.gapfinder.mobile.BuildConfig

/** Backend address. It comes from gapfinder.baseUrl in gradle.properties (the public server). */
object ServerEnv {
    const val BASE_URL = BuildConfig.BASE_URL
}
