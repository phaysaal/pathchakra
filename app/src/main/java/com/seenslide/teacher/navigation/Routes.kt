package com.seenslide.teacher.navigation

import java.net.URLEncoder

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CREATE_SESSION = "create_session"
    const val SESSION_DETAIL = "session/{sessionId}"
    const val TALK_DETAIL = "talk/{sessionId}/{talkId}"
    const val CAMERA = "camera/{sessionId}/{talkId}?replaceSlide={replaceSlide}"
    const val SLIDE_EDITOR = "slide_editor/{sessionId}/{talkId}/{mode}?replaceSlide={replaceSlide}"
    const val SLIDE_MAKER = "slide_maker/{sessionId}/{talkId}?replaceSlide={replaceSlide}"
    const val PDF_IMPORT = "pdf/{sessionId}/{talkId}?replaceSlide={replaceSlide}"
    const val SETTINGS = "settings"
    const val STUDENT_ROSTER = "students/{sessionId}"
    const val ATTENDANCE = "attendance/{sessionId}"
    const val ATTENDANCE_HISTORY = "attendance_history/{sessionId}"
    const val STUDENT_DETAIL = "student_detail/{studentId}"
    const val NOTES = "notes/{sessionId}"
    const val LIVE_CLASS = "live/{sessionId}/{talkId}"
    const val NARRATION = "narration/{sessionId}/{talkId}"

    fun sessionDetail(sessionId: String) = "session/$sessionId"
    fun talkDetail(sessionId: String, talkId: String) = "talk/$sessionId/$talkId"
    fun camera(sessionId: String, talkId: String, replaceSlide: Int? = null) =
        "camera/$sessionId/$talkId?replaceSlide=${replaceSlide ?: -1}"
    fun pdfImport(sessionId: String, talkId: String, replaceSlide: Int? = null) =
        "pdf/$sessionId/$talkId?replaceSlide=${replaceSlide ?: -1}"
    fun notes(sessionId: String) = "notes/$sessionId"
    fun studentRoster(sessionId: String) = "students/$sessionId"
    fun attendance(sessionId: String) = "attendance/$sessionId"
    fun attendanceHistory(sessionId: String) = "attendance_history/$sessionId"
    fun studentDetail(studentId: String) = "student_detail/$studentId"
    fun liveClass(sessionId: String, talkId: String) = "live/$sessionId/$talkId"
    fun narration(sessionId: String, talkId: String) = "narration/$sessionId/$talkId"
    fun slideEditor(sessionId: String, talkId: String, mode: String = "blank", replaceSlide: Int? = null): String {
        val encoded = URLEncoder.encode(mode, "UTF-8")
        return "slide_editor/$sessionId/$talkId/$encoded?replaceSlide=${replaceSlide ?: -1}"
    }
    fun slideMaker(sessionId: String, talkId: String, replaceSlide: Int? = null) =
        "slide_maker/$sessionId/$talkId?replaceSlide=${replaceSlide ?: -1}"
}
