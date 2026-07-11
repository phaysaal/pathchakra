package com.seenslide.teacher.core.slidedoc

import com.squareup.moshi.Moshi

/**
 * JSON codec for [SlideDoc]. Uses KSP-generated adapters from @JsonClass.
 */
object SlideDocJson {
    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val adapter by lazy { moshi.adapter(SlideDoc::class.java) }

    fun encode(doc: SlideDoc): String = adapter.toJson(doc)

    fun decode(json: String): SlideDoc? = try {
        adapter.fromJson(json)
    } catch (_: Exception) {
        null
    }
}
