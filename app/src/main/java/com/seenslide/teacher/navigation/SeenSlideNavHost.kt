package com.seenslide.teacher.navigation

import androidx.compose.runtime.Composable
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
            HomeScreen(
                viewModel = viewModel,
                onCreateSession = {
                    navController.navigate(Routes.CREATE_SESSION)
                },
                onSessionClick = { sessionId ->
                    navController.navigate(Routes.sessionDetail(sessionId))
                },
            )
        }

        composable(Routes.CREATE_SESSION) {
            val viewModel = hiltViewModel<CreateSessionViewModel>()
            CreateSessionScreen(
                viewModel = viewModel,
                onSessionCreated = { sessionId ->
                    navController.navigate(Routes.sessionDetail(sessionId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SESSION_DETAIL) {
            val viewModel = hiltViewModel<SessionDetailViewModel>()
            SessionDetailScreen(
                viewModel = viewModel,
                onTalkClick = { sessionId, talkId ->
                    navController.navigate(Routes.talkDetail(sessionId, talkId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TALK_DETAIL) {
            val viewModel = hiltViewModel<TalkDetailViewModel>()
            TalkDetailScreen(
                viewModel = viewModel,
                onTakePhoto = { sessionId, talkId ->
                    navController.navigate(Routes.camera(sessionId, talkId))
                },
                onOpenCanvas = { sessionId, talkId ->
                    navController.navigate(Routes.slideEditor(sessionId, talkId, "blank"))
                },
                onOpenPdf = { sessionId, talkId ->
                    navController.navigate(Routes.pdfImport(sessionId, talkId))
                },
                onGoLive = { sessionId, talkId ->
                    navController.navigate(Routes.liveClass(sessionId, talkId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CAMERA) { backStackEntry ->
            val viewModel = hiltViewModel<CameraViewModel>()
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val talkId = backStackEntry.arguments?.getString("talkId") ?: "none"
            CameraFlow(
                viewModel = viewModel,
                onSlideUploaded = { navController.popBackStack() },
                onClose = { navController.popBackStack() },
                onDrawOnPhoto = { photoPath ->
                    navController.navigate(Routes.slideEditor(sessionId, talkId, "photo:$photoPath"))
                },
            )
        }

        composable(Routes.PDF_IMPORT) { backStackEntry ->
            val viewModel = hiltViewModel<PdfViewModel>()
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val talkId = backStackEntry.arguments?.getString("talkId") ?: "none"
            PdfImportScreen(
                viewModel = viewModel,
                onDrawOnPage = { photoPath ->
                    navController.navigate(Routes.slideEditor(sessionId, talkId, "photo:$photoPath"))
                },
                onDone = { navController.popBackStack() },
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

        composable(Routes.SLIDE_EDITOR) {
            val viewModel = hiltViewModel<SlideEditorViewModel>()
            SlideEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
