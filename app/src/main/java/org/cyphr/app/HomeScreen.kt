package org.cyphr.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.cyphr.app.R
import org.cyphr.app.ui.MaxWidthBox
import org.cyphr.app.ui.theme.CyphrTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    forceDarkTheme: Boolean? = null,
    onNavigateToTransformText: () -> Unit = {},
    onNavigateToInspectPayload: () -> Unit = {},
    onNavigateToMyIdentity: () -> Unit = {},
    onNavigateToContactList: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToMessageLog: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onToggleDarkMode: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) })
        }
    ) { innerPadding ->
        MaxWidthBox(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                HeroSection()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.home_section_identity),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                NavCard(
                    title = stringResource(R.string.home_profiles),
                    description = stringResource(R.string.home_profiles_desc),
                    onClick = onNavigateToProfiles
                )
                Spacer(modifier = Modifier.height(8.dp))
                NavCard(
                    title = stringResource(R.string.home_my_identity),
                    description = stringResource(R.string.home_my_identity_desc),
                    onClick = onNavigateToMyIdentity
                )
                Spacer(modifier = Modifier.height(8.dp))
                NavCard(
                    title = stringResource(R.string.home_contacts),
                    description = stringResource(R.string.home_contacts_desc),
                    onClick = onNavigateToContactList
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.home_section_tools),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                NavCard(
                    title = stringResource(R.string.home_transform),
                    description = stringResource(R.string.home_transform_desc),
                    onClick = onNavigateToTransformText
                )
                Spacer(modifier = Modifier.height(8.dp))
                NavCard(
                    title = stringResource(R.string.home_inspect),
                    description = stringResource(R.string.home_inspect_desc),
                    onClick = onNavigateToInspectPayload
                )
                Spacer(modifier = Modifier.height(8.dp))
                NavCard(
                    title = stringResource(R.string.home_message_log),
                    description = stringResource(R.string.home_message_log_desc),
                    onClick = onNavigateToMessageLog
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_local_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.home_no_accounts),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onToggleDarkMode) {
                        Text(
                            text = when (forceDarkTheme) {
                                null -> stringResource(R.string.home_theme_system)
                                true -> stringResource(R.string.home_theme_dark)
                                false -> stringResource(R.string.home_theme_light)
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    TextButton(onClick = onNavigateToSettings) {
                        Text(
                            text = stringResource(R.string.home_settings),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun NavCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroSection() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_hero_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    CyphrTheme { HomeScreen() }
}
