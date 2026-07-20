package com.example.adobongkangkong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Health Connect permission rationale screen for AK's optional health-data
 * integrations.
 *
 * AK reads Health Connect data only when the user grants permission. Calories
 * are used as optional estimated-burn context, and weight can be imported into
 * the body-weight tracker after the user explicitly requests it.
 *
 * Keep temporary smoke-test/debug UI out of this activity. Debug widgets belong
 * behind developer/debug toggles so this rationale stays stable for Android's
 * Health Connect permission flow.
 */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Health Connect access",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = "AdobongKangkong can read selected Health Connect data only if you grant permission. Calories can be used as optional estimated-burn context, and scale weight can be imported into the weight tracker when you ask AK to import it."
                    )
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = "AK does not write health data, does not change your food logs from Health Connect data, and does not use Health Connect data unless you enable or request the feature."
                    )
                }
            }
        }
    }
}
