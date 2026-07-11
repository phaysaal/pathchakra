package com.seenslide.teacher.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.seenslide.teacher.feature.auth.LoginScreen
import com.seenslide.teacher.feature.auth.LoginViewModel
import com.seenslide.teacher.feature.home.HomeScreen
import com.seenslide.teacher.feature.home.HomeViewModel
import com.seenslide.teacher.feature.session.CreateSessionScreen
import com.seenslide.teacher.feature.session.CreateSessionViewModel
import com.seenslide.teacher.feature.session.SessionDetailScreen
import com.seenslide.teacher.feature.session.SessionDetailViewModel
import com.seenslide.teacher.feature.session.TalkDetailScreen
import com.seenslide.teacher.feature.session.TalkDetailViewModel
import com.seenslide.teacher.feature.slide.camera.CameraFlow
import com.seenslide.teacher.feature.slide.camera.CameraViewModel
import com.seenslide.teacher.feature.live.LiveClassScreen
import com.seenslide.teacher.feature.live.LiveClassViewModel
import com.seenslide.teacher.feature.slide.pdf.PdfImportScreen
import com.seenslide.teacher.feature.slide.pdf.PdfViewModel
import com.seenslide.teacher.feature.slide.editor.SlideEditorScreen
import com.seenslide.teacher.feature.slide.editor.SlideEditorViewModel
import com.seenslide.teacher.feature.slide.maker.SlideMakerScreen
import com.seenslide.teacher.feature.slide.maker.SlideMakerViewModel
import com.seenslide.teacher.feature.settings.SettingsScreen
import com.seenslide.teacher.feature.settings.SettingsViewModel
import com.seenslide.teacher.feature.attendance.StudentRosterScreen
import com.seenslide.teacher.feature.attendance.StudentRosterViewModel
import com.seenslide.teacher.feature.attendance.AttendanceScreen
import com.seenslide.teacher.feature.attendance.AttendanceViewModel
import com.seenslide.teacher.feature.attendance.AttendanceHistoryScreen
import com.seenslide.teacher.feature.attendance.AttendanceHistoryViewModel
import com.seenslide.teacher.feature.attendance.StudentDetailScreen
import com.seenslide.teacher.feature.attendance.StudentDetailViewModel
import com.seenslide.teacher.feature.notes.NotesScreen
import com.seenslide.teacher.feature.notes.NotesViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument

@Composable
fun SeenSlideNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            val viewModel = hiltViewModel<LoginViewModel>()
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            val viewModel = hiltViewModel<HomeViewModel>()
            val currentBackStackEntry = navController.currentBackStackEntry
            val createdSessionId by currentBackStackEntry?.savedStateHandle
                ?.getStateFlow<String?>(NavResults.CREATED_SESSION_ID, null)
                ?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)
            val createdSessionName by currentBackStackEntry?.savedStateHandle
                ?.getStateFlow<String?>(NavResults.CREATED_SESSION_NAME, null)
                ?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)
            val updatedSessionId by currentBackStackEntry?.savedStateHandle
                ?.getStateFlow<String?>(NavResults.UPDATED_SESSION_ID, null)
                ?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)
            val updatedSessionName by currentBackStackEntry?.savedStateHandle
                ?.getStateFlow<String?>(NavResults.UPDATED_SESSION_NAME, null)
                ?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)
            val deletedSessionId by currentBackStackEntry?.savedStateHandle
                ?.getStateFlow<String?>(NavResults.DELETED_SESSION_ID, null)
                ?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)

            LaunchedEffect(createdSessionId, createdSessionName) {
                if (createdSessionId != null && createdSessionName != null) {
                    viewModel.upsertSession(createdSessionId!!, createdSessionName!!)
                    currentBackStackEntry?.savedStateHandle?.set(NavResults.CREATED_SESSION_ID, null)
                    currentBackStackEntry?.savedStateHandle?.set(NavResults.CREATED_SESSION_NAME, null)
                }
            }
            LaunchedEffect(updatedSessionId, updatedSessionName) {
                if (updatedSessionId != null && updatedSessionName != null) {
                    viewModel.upsertSession(updatedSessionId!!, updatedSessionName!!)
                    currentBackStackEntry?.savedStateHandle?.set(NavResults.UPDATED_SESSION_ID, null)
                    currentBackStackEntry?.savedStateHandle?.set(NavResults.UPDATED_SESSION_NAME, null)
                }
            }
            LaunchedEffect(deletedSessionId) {
                if (deletedSessionId != null) {
                    viewModel.removeSession(deletedSessionId!!)
                    currentBackStackEntry?.savedStateHandle?.set(NavResults.DELETED_SESSION_ID, null)
                }
            }
            HomeScreen(
                viewModel = viewModel,
                onCreateSession = {
                    navController.navigate(Routes.CREATE_SESSION)
                },
                onSessionClick = { sessionId ->
                    navController.navigate(Routes.sessionDetail(sessionId))
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(Routes.CREATE_SESSION) {
            val viewModel = hiltViewModel<CreateSessionViewModel>()
            CreateSessionScreen(
                viewModel = viewModel,
                onSessionCreated = { sessionId, sessionName ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.CREATED_SESSION_ID,
                        sessionId,
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.CREATED_SESSION_NAME,
                        sessionName,
                    )
                    navController.navigate(Routes.sessionDetail(sessionId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SESSION_DETAIL) { backStackEntry ->
            val viewModel = hiltViewModel<SessionDetailViewModel>()
            val updatedTalkId by backStackEntry.savedStateHandle
                .getStateFlow<String?>(NavResults.UPDATED_TALK_ID, null)
                .collectAsState()
            val updatedTalkTitle by backStackEntry.savedStateHandle
                .getStateFlow<String?>(NavResults.UPDATED_TALK_TITLE, null)
                .collectAsState()
            val updatedTalkSlideCount by backStackEntry.savedStateHandle
                .getStateFlow<Int?>(NavResults.UPDATED_TALK_SLIDE_COUNT, null)
                .collectAsState()
            val deletedTalkId by backStackEntry.savedStateHandle
                .getStateFlow<String?>(NavResults.DELETED_TALK_ID, null)
                .collectAsState()

            LaunchedEffect(updatedTalkId, updatedTalkTitle, updatedTalkSlideCount) {
                if (updatedTalkId != null && updatedTalkTitle != null && updatedTalkSlideCount != null) {
                    viewModel.upsertTalk(updatedTalkId!!, updatedTalkTitle!!, updatedTalkSlideCount!!)
                    backStackEntry.savedStateHandle[NavResults.UPDATED_TALK_ID] = null
                    backStackEntry.savedStateHandle[NavResults.UPDATED_TALK_TITLE] = null
                    backStackEntry.savedStateHandle[NavResults.UPDATED_TALK_SLIDE_COUNT] = null
                }
            }
            LaunchedEffect(deletedTalkId) {
                if (deletedTalkId != null) {
                    viewModel.removeTalkById(deletedTalkId!!)
                    backStackEntry.savedStateHandle[NavResults.DELETED_TALK_ID] = null
                }
            }
            SessionDetailScreen(
                viewModel = viewModel,
                onTalkClick = { sessionId, talkId ->
                    navController.navigate(Routes.talkDetail(sessionId, talkId))
                },
                onStudents = { sessionId ->
                    navController.navigate(Routes.studentRoster(sessionId))
                },
                onAttendance = { sessionId ->
                    navController.navigate(Routes.attendance(sessionId))
                },
                onAttendanceHistory = { sessionId ->
                    navController.navigate(Routes.attendanceHistory(sessionId))
                },
                onNotes = { sessionId ->
                    navController.navigate(Routes.notes(sessionId))
                },
                onBack = {
                    viewModel.uiState.value.session?.let { session ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            NavResults.UPDATED_SESSION_ID,
                            session.sessionId,
                        )
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            NavResults.UPDATED_SESSION_NAME,
                            session.presenterName ?: session.sessionId,
                        )
                    }
                    navController.popBackStack()
                },
                onDeleteSession = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.DELETED_SESSION_ID,
                        viewModel.sessionId,
                    )
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.TALK_DETAIL) { backStackEntry ->
            val viewModel = hiltViewModel<TalkDetailViewModel>()
            val addedSlideNumber by backStackEntry.savedStateHandle
                .getStateFlow<Int?>(NavResults.ADDED_SLIDE_NUMBER, null)
                .collectAsState()
            val addedSlideId by backStackEntry.savedStateHandle
                .getStateFlow<String?>(NavResults.ADDED_SLIDE_ID, null)
                .collectAsState()

            LaunchedEffect(addedSlideNumber, addedSlideId) {
                if (addedSlideNumber != null) {
                    viewModel.addUploadedSlide(addedSlideNumber!!, addedSlideId)
                    backStackEntry.savedStateHandle[NavResults.ADDED_SLIDE_NUMBER] = null
                    backStackEntry.savedStateHandle[NavResults.ADDED_SLIDE_ID] = null
                }
            }
            TalkDetailScreen(
                viewModel = viewModel,
                onTakePhoto = { sessionId, talkId ->
                    navController.navigate(Routes.camera(sessionId, talkId))
                },
                onOpenCanvas = { sessionId, talkId ->
                    navController.navigate(Routes.slideEditor(sessionId, talkId, "blank"))
                },
                onOpenSlideMaker = { sessionId, talkId ->
                    navController.navigate(Routes.slideMaker(sessionId, talkId))
                },
                onOpenPdf = { sessionId, talkId ->
                    navController.navigate(Routes.pdfImport(sessionId, talkId))
                },
                onGoLive = { sessionId, talkId ->
                    navController.navigate(Routes.liveClass(sessionId, talkId))
                },
                onRecordNarration = { sessionId, talkId ->
                    navController.navigate(Routes.narration(sessionId, talkId))
                },
                onReplaceWithPhoto = { sessionId, talkId, slideNumber ->
                    navController.navigate(Routes.camera(sessionId, talkId, slideNumber))
                },
                onReplaceWithCanvas = { sessionId, talkId, slideNumber ->
                    navController.navigate(Routes.slideEditor(sessionId, talkId, "blank", slideNumber))
                },
                onReplaceWithPdf = { sessionId, talkId, slideNumber ->
                    navController.navigate(Routes.pdfImport(sessionId, talkId, slideNumber))
                },
                onEditSlide = { sessionId, talkId, slideNumber ->
                    navController.navigate(Routes.slideEditor(sessionId, talkId, "edit:$talkId:$slideNumber", slideNumber))
                },
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.UPDATED_TALK_ID,
                        viewModel.talkId,
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.UPDATED_TALK_TITLE,
                        viewModel.uiState.value.talkTitle,
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.UPDATED_TALK_SLIDE_COUNT,
                        viewModel.uiState.value.slides.size,
                    )
                    navController.popBackStack()
                },
                onDeleteTalk = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.DELETED_TALK_ID,
                        viewModel.talkId,
                    )
                    navController.popBackStack()
                },
            )
        }

        composable(
            Routes.CAMERA,
            arguments = listOf(navArgument("replaceSlide") { type = NavType.IntType; defaultValue = -1 }),
        ) { backStackEntry ->
            val viewModel = hiltViewModel<CameraViewModel>()
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val talkId = backStackEntry.arguments?.getString("talkId") ?: "none"
            CameraFlow(
                viewModel = viewModel,
                onSlideUploaded = { slideNumber, slideId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.ADDED_SLIDE_NUMBER,
                        slideNumber,
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.ADDED_SLIDE_ID,
                        slideId,
                    )
                    navController.popBackStack()
                },
                onClose = { navController.popBackStack() },
                onDrawOnPhoto = { photoPath ->
                    navController.navigate(Routes.slideEditor(sessionId, talkId, "photo:$photoPath"))
                },
            )
        }

        composable(
            Routes.PDF_IMPORT,
            arguments = listOf(navArgument("replaceSlide") { type = NavType.IntType; defaultValue = -1 }),
        ) { backStackEntry ->
            val viewModel = hiltViewModel<PdfViewModel>()
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val talkId = backStackEntry.arguments?.getString("talkId") ?: "none"
            PdfImportScreen(
                viewModel = viewModel,
                onDrawOnPage = { photoPath ->
                    navController.navigate(Routes.slideEditor(sessionId, talkId, "photo:$photoPath"))
                },
                onDone = { slideNumber, slideId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.ADDED_SLIDE_NUMBER,
                        slideNumber,
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.ADDED_SLIDE_ID,
                        slideId,
                    )
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.LIVE_CLASS) {
            val viewModel = hiltViewModel<LiveClassViewModel>()
            LiveClassScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.NARRATION) {
            val viewModel = hiltViewModel<com.seenslide.teacher.feature.narration.NarrationViewModel>()
            com.seenslide.teacher.feature.narration.NarrationScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.SLIDE_EDITOR,
            arguments = listOf(navArgument("replaceSlide") { type = NavType.IntType; defaultValue = -1 }),
        ) {
            val viewModel = hiltViewModel<SlideEditorViewModel>()
            SlideEditorScreen(
                viewModel = viewModel,
                onSlideSaved = { slideNumber, slideId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.ADDED_SLIDE_NUMBER,
                        slideNumber,
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavResults.ADDED_SLIDE_ID,
                        slideId,
                    )
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.SLIDE_MAKER,
            arguments = listOf(navArgument("replaceSlide") { type = NavType.IntType; defaultValue = -1 }),
        ) {
            val viewModel = hiltViewModel<SlideMakerViewModel>()
            SlideMakerScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.NOTES) {
            val viewModel = hiltViewModel<NotesViewModel>()
            NotesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.STUDENT_ROSTER) {
            val viewModel = hiltViewModel<StudentRosterViewModel>()
            StudentRosterScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ATTENDANCE) {
            val viewModel = hiltViewModel<AttendanceViewModel>()
            AttendanceScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ATTENDANCE_HISTORY) {
            val viewModel = hiltViewModel<AttendanceHistoryViewModel>()
            AttendanceHistoryScreen(
                viewModel = viewModel,
                onStudentDetail = { studentId ->
                    navController.navigate(Routes.studentDetail(studentId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.STUDENT_DETAIL) {
            val viewModel = hiltViewModel<StudentDetailViewModel>()
            StudentDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel = hiltViewModel<SettingsViewModel>()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
