package com.seenslide.teacher.navigation

import java.net.URLEncoder

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CREATE_SESSION = "create_session"
    const val SESSION_DETAIL = "session/{sessionId}"
    const val TALK_DETAIL = "talk/{sessionId}/{talkId}"
    const val CAMERA = "camera/{sessionId}/{talkId}"
    const val SLIDE_EDITOR = "slide_editor/{sessionId}/{talkId}/{mode}"
    const val PDF_IMPORT = "pdf/{sessionId}/{talkId}"
    const val LIVE_CLASS = "live/{sessionId}/{talkId}"

    fun sessionDetail(sessionId: String) = "session/$sessionId"
    fun talkDetail(sessionId: String, talkId: String) = "talk/$sessionId/$talkId"
    fun camera(sessionId: String, talkId: String) = "camera/$sessionId/$talkId"
    fun pdfImport(sessionId: String, talkId: String) = "pdf/$sessionId/$talkId"
    fun liveClass(sessionId: String, talkId: String) = "live/$sessionId/$talkId"
    fun slideEditor(sessionId: String, talkId: String, mode: String = "blank"): String {
        val encoded = URLEncoder.encode(mode, "UTF-8")
        return "slide_editor/$sessionId/$talkId/$encoded"
    }
}
