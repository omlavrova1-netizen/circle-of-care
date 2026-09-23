package ru.krugzaboty.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.krugzaboty.app.R

/** TODO(E3-01/02, E8-02, E12-03, E14-01): подопечный, экстренная карточка, отчёты, «Моя подписка», приватность, дисклеймер. */
@Composable
fun ProfileScreen() {
    Column(Modifier.padding(24.dp)) {
        Text("Профиль", style = MaterialTheme.typography.headlineSmall)
        Text("E3/E8/E12-03/E14: подопечный, экстренная карточка, отчёты, подписка, приватность", style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.disclaimer_not_medical), style = MaterialTheme.typography.bodySmall)
    }
}
