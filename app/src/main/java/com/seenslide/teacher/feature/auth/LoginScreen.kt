package com.seenslide.teacher.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seenslide.teacher.R

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState.step) {
                LoginStep.LOADING -> LoadingStep()
                LoginStep.PHONE_INPUT -> {
                    AuthStepCard(
                        icon = Icons.Default.School,
                        title = stringResource(R.string.phone_step_title),
                        subtitle = stringResource(R.string.phone_step_subtitle),
                    ) {
                        PhoneInputStep(
                            uiState = uiState,
                            onPhoneChanged = viewModel::onPhoneChanged,
                            onNameChanged = viewModel::onNameChanged,
                            onSubmit = viewModel::submitPhone,
                        )
                    }
                }

                LoginStep.PIN_SETUP -> {
                    AuthStepCard(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.pin_setup_step_title),
                        subtitle = stringResource(R.string.pin_setup_step_subtitle),
                        headerAction = {
                            IconButton(onClick = viewModel::goBackToPhone) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        },
                    ) {
                        PinSetupStep(
                            uiState = uiState,
                            onPinChanged = viewModel::onPinChanged,
                            onConfirmPinChanged = viewModel::onConfirmPinChanged,
                            onSubmit = { viewModel.submitPin(onLoginSuccess) },
                        )
                    }
                }

                LoginStep.PIN_LOCK -> {
                    AuthStepCard(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.pin_lock_step_title),
                        subtitle = stringResource(R.string.pin_lock_step_subtitle),
                    ) {
                        PinLockStep(
                            uiState = uiState,
                            onPinChanged = viewModel::onPinChanged,
                            onSubmit = { viewModel.verifyPin(onLoginSuccess) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.loading_account),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuthStepCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (headerAction != null) {
                    headerAction()
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(68.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun PhoneInputStep(
    uiState: LoginUiState,
    onPhoneChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = uiState.phone,
        onValueChange = onPhoneChanged,
        label = { Text(stringResource(R.string.phone_number)) },
        supportingText = { Text(stringResource(R.string.phone_number_hint)) },
        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
        isError = uiState.error != null,
    )

    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.your_name_optional)) },
        supportingText = { Text(stringResource(R.string.name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
    )

    if (uiState.error != null) {
        ErrorText(uiState.error)
    }

    Spacer(modifier = Modifier.height(4.dp))

    Button(
        onClick = onSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(
            text = stringResource(R.string.next),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun PinSetupStep(
    uiState: LoginUiState,
    onPinChanged: (String) -> Unit,
    onConfirmPinChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = uiState.pin,
        onValueChange = onPinChanged,
        label = { Text(stringResource(R.string.enter_pin)) },
        supportingText = { Text(stringResource(R.string.pin_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = uiState.confirmPin,
        onValueChange = onConfirmPinChanged,
        label = { Text(stringResource(R.string.confirm_pin)) },
        supportingText = { Text(stringResource(R.string.confirm_pin_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
        isError = uiState.error != null,
    )

    if (uiState.error != null) {
        ErrorText(uiState.error)
    }

    Spacer(modifier = Modifier.height(4.dp))

    Button(
        onClick = onSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = !uiState.isLoading,
    ) {
        if (uiState.isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.setting_up_account))
            }
        } else {
            Text(
                text = stringResource(R.string.start),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun PinLockStep(
    uiState: LoginUiState,
    onPinChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        text = stringResource(R.string.pin_lock_helper),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = uiState.pin,
        onValueChange = { pin ->
            onPinChanged(pin)
            if (pin.length == 4) onSubmit()
        },
        label = { Text(stringResource(R.string.enter_pin)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        isError = uiState.error != null,
    )

    if (uiState.error != null) {
        ErrorText(uiState.error)
    }

    Spacer(modifier = Modifier.height(4.dp))

    Button(
        onClick = onSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = !uiState.isLoading,
    ) {
        if (uiState.isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.verifying_pin))
            }
        } else {
            Text(
                text = stringResource(R.string.unlock),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
}
